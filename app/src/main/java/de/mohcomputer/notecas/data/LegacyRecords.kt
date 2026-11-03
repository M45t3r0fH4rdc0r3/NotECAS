package de.mohcomputer.notecas.data

import de.mohcomputer.notecas.domain.Meter
import de.mohcomputer.notecas.domain.MeterFlags
import de.mohcomputer.notecas.domain.MeterType
import de.mohcomputer.notecas.domain.Reading
import de.mohcomputer.notecas.domain.VirtualMeterDefinitions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

val LEGACY_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

data class LegacyMeterRecord(
    val id: Long,
    val name: String,
    val units: String,
    val comment: String?,
    val vsf: Int,
    val tsf: Int,
    val cost: Double,
    val fixedCost: Double,
    val invert: Boolean,
    val vmsetup: String?,
    val type: Int,
    val currency: String?,
    val scaling: Double,
    val physicalUnits: String?,
    val bidirectional: Boolean,
    val production: Boolean,
) {
    fun toDomain() = Meter(
        id = id,
        name = name,
        unit = units,
        type = when {
            !vmsetup.isNullOrBlank() -> MeterType.VIRTUAL
            type == 1 -> MeterType.SENSOR
            else -> MeterType.CUMULATIVE
        },
        scaling = scaling,
        costPerUnit = cost,
        annualFixedCost = fixedCost,
        flags = MeterFlags(invert, bidirectional, production),
    )
}

data class LegacyReadingRecord(
    val id: Long,
    val meterId: Long,
    val date: String,
    val value: Double,
    val comment: String? = null,
    val color: Int = 0,
    val cost: Double? = null,
    val valueCorrection: Double = 0.0,
    val costCorrection: Double = 0.0,
    val first: Int = 0,
) {
    fun toDomain() = Reading(id, meterId, LocalDateTime.parse(date, LEGACY_TIMESTAMP_FORMAT), value, first == 1)
}

data class LegacyDataset(
    val version: Int,
    val meters: List<LegacyMeterRecord>,
    val readings: List<LegacyReadingRecord>,
)

object LegacyMapper {
    fun fromDomain(meter: Meter) = LegacyMeterRecord(
        id = meter.id,
        name = meter.name,
        units = meter.unit,
        comment = null,
        vsf = 50,
        tsf = 50,
        cost = meter.costPerUnit,
        fixedCost = meter.annualFixedCost,
        invert = meter.flags.inverted,
        vmsetup = null,
        type = if (meter.type == MeterType.SENSOR) 1 else 0,
        currency = null,
        scaling = meter.scaling,
        physicalUnits = meter.unit,
        bidirectional = meter.flags.bidirectional,
        production = meter.flags.production,
    )

    fun fromDomain(reading: Reading) = LegacyReadingRecord(
        id = reading.id,
        meterId = reading.meterId,
        date = reading.timestamp.format(LEGACY_TIMESTAMP_FORMAT),
        value = reading.rawValue,
        first = if (reading.isFirst) 1 else 0,
    )
}

enum class ImportIssueCode {
    UNSUPPORTED_VERSION,
    LEGACY_VERSION_DEFAULTS,
    ORPHAN_READING,
    INVALID_NUMBER,
    INVALID_TIMESTAMP,
    INVALID_FLAG,
    INVALID_RANGE,
    INVALID_VM_REFERENCE,
    VIRTUAL_CYCLE,
    SCHEMA_ERROR,
    INTEGRITY_ERROR,
    INVALID_SQLITE_FILE,
}

data class ImportIssue(val code: ImportIssueCode, val detail: String, val fatal: Boolean)
data class LegacyValidation(val canImport: Boolean, val issues: List<ImportIssue>)

object LegacyImportValidator {
    fun validate(dataset: LegacyDataset): LegacyValidation {
        val issues = mutableListOf<ImportIssue>()
        if (dataset.version !in 1..17) {
            issues += ImportIssue(ImportIssueCode.UNSUPPORTED_VERSION, "Unsupported user_version ${dataset.version}; expected 1..17", true)
        } else if (dataset.version < 17) {
            issues += ImportIssue(
                ImportIssueCode.LEGACY_VERSION_DEFAULTS,
                "Legacy schema v${dataset.version}: missing post-version columns are assigned documented v17 defaults",
                false,
            )
        }
        val meterIds = dataset.meters.map { it.id }.toSet()
        val meterMap = mutableMapOf<Long, Meter>()
        dataset.meters.forEach { meter ->
            if (meter.id < 0 || !meter.scaling.isFinite() || !meter.cost.isFinite() || !meter.fixedCost.isFinite()) {
                issues += ImportIssue(ImportIssueCode.INVALID_NUMBER, "Meter ${meter.id} contains a non-finite number or invalid ID", true)
            }
            if (meter.vsf !in 0..100 || meter.tsf !in 0..100) {
                issues += ImportIssue(ImportIssueCode.INVALID_RANGE, "Meter ${meter.id} smoothing is outside 0..100", true)
            }
            if (meter.type !in 0..1) issues += ImportIssue(ImportIssueCode.INVALID_FLAG, "Meter ${meter.id} has invalid type ${meter.type}", true)
            try { meterMap[meter.id] = meter.toDomain() } catch (e: IllegalArgumentException) {
                issues += ImportIssue(ImportIssueCode.INVALID_NUMBER, "Meter ${meter.id}: ${e.message}", true)
            }
        }
        dataset.readings.forEach { reading ->
            if (reading.meterId !in meterIds) issues += ImportIssue(ImportIssueCode.ORPHAN_READING, "Reading ${reading.id} references missing meter ${reading.meterId}", true)
            if (!reading.value.isFinite() || reading.cost?.isFinite() == false || !reading.valueCorrection.isFinite() || !reading.costCorrection.isFinite()) {
                issues += ImportIssue(ImportIssueCode.INVALID_NUMBER, "Reading ${reading.id} contains a non-finite number", true)
            }
            try { LocalDateTime.parse(reading.date, LEGACY_TIMESTAMP_FORMAT) } catch (_: DateTimeParseException) {
                issues += ImportIssue(ImportIssueCode.INVALID_TIMESTAMP, "Reading ${reading.id} has invalid timestamp '${reading.date}'", true)
            }
            if (reading.color !in 0..4 || reading.first !in 0..1) {
                issues += ImportIssue(ImportIssueCode.INVALID_FLAG, "Reading ${reading.id} has invalid color/reset flag", true)
            }
        }
        val definitions = mutableMapOf<Long, List<de.mohcomputer.notecas.domain.VirtualComponent>>()
        dataset.meters.filter { !it.vmsetup.isNullOrBlank() }.forEach { meter ->
            val parsed = VirtualMeterDefinitions.parse(meter.id, meter.vmsetup.orEmpty(), meterMap)
            parsed.issues.forEach { issue ->
                issues += ImportIssue(ImportIssueCode.INVALID_VM_REFERENCE, "Meter ${meter.id}: ${issue.detail}", false)
            }
            definitions[meter.id] = parsed.components
        }
        VirtualMeterDefinitions.cyclicMeterIds(definitions, meterMap).forEach {
            issues += ImportIssue(ImportIssueCode.VIRTUAL_CYCLE, "Virtual meter $it participates in a cycle", true)
        }
        return LegacyValidation(issues.none { it.fatal }, issues)
    }
}
