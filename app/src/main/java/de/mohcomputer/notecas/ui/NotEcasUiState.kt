package de.mohcomputer.notecas.ui

import de.mohcomputer.notecas.data.LEGACY_TIMESTAMP_FORMAT
import de.mohcomputer.notecas.data.LegacyMeterRecord
import de.mohcomputer.notecas.data.LegacyReadingRecord
import de.mohcomputer.notecas.domain.MeterCalculations
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val EDITOR_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

enum class RootDestination(val label: String) { OVERVIEW("Übersicht"), METERS("Zähler"), READINGS("Messwerte"), ANALYSIS("Analyse") }
enum class Overlay { TRANSFER, ABOUT, DONATION }
enum class MeterFilter(val label: String) { ALL("Alle"), CUMULATIVE("Kumulativ"), SENSORS("Sensoren"), PRODUCTION("Produktion"), VIRTUAL("Virtuell") }
enum class AnalysisMode(val label: String) { TREND("Trend"), BARS("Balken"), TABLE("Tabelle") }
enum class AnalysisPeriod(val label: String) { MONTH("Monat"), QUARTER("Quartal"), YEAR("Jahr") }

data class MeterEditor(
    val id: Long? = null, val name: String = "", val unit: String = "", val comment: String = "",
    val sensor: Boolean = false, val production: Boolean = false, val inverted: Boolean = false,
    val bidirectional: Boolean = false, val scaling: String = "1", val physicalUnit: String = "",
    val virtual: Boolean = false, val virtualSetup: String = "", val currency: String = "€",
    val price: String = "0", val fixedCost: String = "0", val valueSmoothing: Float = 50f,
    val trendSmoothing: Float = 50f,
)

data class ReadingEditor(
    val id: Long? = null, val meterId: Long, val value: String = "", val date: String,
    val time: String, val comment: String = "", val color: Int = 0, val first: Boolean = false,
    val lastValue: Double? = null, val previewDelta: Double? = null,
)

data class AnalysisPoint(val label: String, val value: Double)
data class AnalysisModel(
    val points: List<AnalysisPoint> = emptyList(), val consumption: Double = 0.0,
    val cost: Double = 0.0, val previousYearConsumption: Double? = null,
)

fun smoothAnalysisPoints(
    points: List<AnalysisPoint>,
    valueSmoothing: Int,
    trendSmoothing: Int,
): List<AnalysisPoint> {
    if (points.size < 2) return points
    val valueFactor = 0.001 + (0.01 * (100 - valueSmoothing.coerceIn(0, 100)))
    val trendFactor = 0.0001 + (0.002 * (100 - trendSmoothing.coerceIn(0, 100)))
    var level = points.first().value
    var trend = points[1].value - points.first().value
    return points.mapIndexed { index, point ->
        if (index == 0) point
        else {
            val previousLevel = level
            level = valueFactor * point.value + (1.0 - valueFactor) * (level + trend)
            trend = trendFactor * (level - previousLevel) + (1.0 - trendFactor) * trend
            point.copy(value = level)
        }
    }
}

data class NotEcasUiState(
    val destination: RootDestination = RootDestination.OVERVIEW,
    val overlay: Overlay? = null,
    val meters: List<LegacyMeterRecord> = emptyList(),
    val readings: Map<Long, List<LegacyReadingRecord>> = emptyMap(),
    val selectedMeterId: Long? = null,
    val meterFilter: MeterFilter = MeterFilter.ALL,
    val readingQuery: String = "",
    val meterEditor: MeterEditor? = null,
    val readingEditor: ReadingEditor? = null,
    val analysisMode: AnalysisMode = AnalysisMode.TREND,
    val analysisPeriod: AnalysisPeriod = AnalysisPeriod.YEAR,
    val analysisYear: Int = LocalDate.now().year,
    val snackbar: String? = null,
    val transferReport: String? = null,
)

sealed interface UiAction {
    data class Navigate(val destination: RootDestination) : UiAction
    data class ShowOverlay(val overlay: Overlay) : UiAction
    data object CloseOverlay : UiAction
    data class SelectMeter(val id: Long) : UiAction
    data class SetMeterFilter(val filter: MeterFilter) : UiAction
    data class SetReadingQuery(val query: String) : UiAction
    data object OpenNewMeter : UiAction
    data class EditMeter(val id: Long) : UiAction
    data class SetMeterEditor(val editor: MeterEditor) : UiAction
    data object SaveMeter : UiAction
    data class DeleteMeter(val id: Long) : UiAction
    data class OpenNewReading(val meterId: Long) : UiAction
    data class EditReading(val id: Long) : UiAction
    data class SetReadingEditor(val editor: ReadingEditor) : UiAction
    data class ChangeReadingValue(val value: String) : UiAction
    data object SaveReading : UiAction
    data class DeleteReading(val id: Long) : UiAction
    data object CloseEditor : UiAction
    data class SetAnalysisMode(val mode: AnalysisMode) : UiAction
    data class SetAnalysisPeriod(val period: AnalysisPeriod) : UiAction
    data class ChangeAnalysisYear(val delta: Int) : UiAction
    data class SetAnalysisYear(val year: Int) : UiAction
    data class SetTransferReport(val report: String) : UiAction
    data object ClearSnackbar : UiAction
}

fun filterMeters(meters: List<LegacyMeterRecord>, filter: MeterFilter): List<LegacyMeterRecord> = meters.filter { meter ->
    when (filter) {
        MeterFilter.ALL -> true
        MeterFilter.CUMULATIVE -> meter.type == 0 && meter.vmsetup.isNullOrBlank() && !meter.production
        MeterFilter.SENSORS -> meter.type == 1
        MeterFilter.PRODUCTION -> meter.production
        MeterFilter.VIRTUAL -> !meter.vmsetup.isNullOrBlank()
    }
}

object NotEcasReducer {
    fun reduce(state: NotEcasUiState, action: UiAction): NotEcasUiState = when (action) {
        is UiAction.Navigate -> state.copy(destination = action.destination, overlay = null, meterEditor = null, readingEditor = null)
        is UiAction.ShowOverlay -> state.copy(overlay = action.overlay)
        UiAction.CloseOverlay -> state.copy(overlay = null)
        is UiAction.SelectMeter -> state.copy(selectedMeterId = action.id)
        is UiAction.SetMeterFilter -> state.copy(meterFilter = action.filter)
        is UiAction.SetReadingQuery -> state.copy(readingQuery = action.query)
        UiAction.OpenNewMeter -> state.copy(meterEditor = MeterEditor())
        is UiAction.EditMeter -> state.copy(meterEditor = state.meters.firstOrNull { it.id == action.id }?.toEditor())
        is UiAction.SetMeterEditor -> state.copy(meterEditor = action.editor)
        UiAction.SaveMeter -> saveMeter(state)
        is UiAction.DeleteMeter -> state.copy(
            meters = state.meters.filterNot { it.id == action.id }, readings = state.readings - action.id,
            selectedMeterId = state.selectedMeterId.takeUnless { it == action.id }, snackbar = "Zähler gelöscht",
        )
        is UiAction.OpenNewReading -> state.copy(readingEditor = newReadingEditor(state, action.meterId), selectedMeterId = action.meterId)
        is UiAction.EditReading -> state.copy(readingEditor = state.readings.values.flatten().firstOrNull { it.id == action.id }?.toEditor(state))
        is UiAction.SetReadingEditor -> state.copy(readingEditor = preview(state, action.editor))
        is UiAction.ChangeReadingValue -> state.copy(readingEditor = state.readingEditor?.let { preview(state, it.copy(value = action.value)) })
        UiAction.SaveReading -> saveReading(state)
        is UiAction.DeleteReading -> state.copy(
            readings = state.readings.mapValues { (_, rows) -> rows.filterNot { it.id == action.id } }, snackbar = "Messwert gelöscht",
        )
        UiAction.CloseEditor -> state.copy(meterEditor = null, readingEditor = null)
        is UiAction.SetAnalysisMode -> state.copy(analysisMode = action.mode)
        is UiAction.SetAnalysisPeriod -> state.copy(analysisPeriod = action.period)
        is UiAction.ChangeAnalysisYear -> state.copy(analysisYear = (state.analysisYear + action.delta).coerceIn(1, LocalDate.now().year))
        is UiAction.SetAnalysisYear -> state.copy(analysisYear = action.year.coerceIn(1, LocalDate.now().year))
        is UiAction.SetTransferReport -> state.copy(transferReport = action.report, snackbar = "Transfer abgeschlossen")
        UiAction.ClearSnackbar -> state.copy(snackbar = null)
    }

    private fun saveMeter(state: NotEcasUiState): NotEcasUiState {
        val editor = state.meterEditor ?: return state
        val scaling = editor.scaling.replace(',', '.').toDoubleOrNull()
        val price = editor.price.replace(',', '.').toDoubleOrNull()
        val fixed = editor.fixedCost.replace(',', '.').toDoubleOrNull()
        if (editor.name.isBlank() || editor.unit.isBlank() || scaling == null || price == null || fixed == null) {
            return state.copy(snackbar = "Bitte Zählerdaten vollständig und gültig eingeben")
        }
        val id = editor.id ?: ((state.meters.maxOfOrNull { it.id } ?: 0L) + 1)
        val record = LegacyMeterRecord(
            id, editor.name.trim(), editor.unit.trim(), editor.comment.trim().ifBlank { null },
            editor.valueSmoothing.toInt(), editor.trendSmoothing.toInt(), price, fixed, editor.inverted,
            editor.virtualSetup.trim().takeIf { editor.virtual && it.isNotBlank() }, if (editor.sensor) 1 else 0,
            editor.currency.trim().ifBlank { null }, scaling, editor.physicalUnit.trim().ifBlank { null },
            editor.bidirectional, editor.production,
        )
        return state.copy(
            meters = (state.meters.filterNot { it.id == id } + record).sortedBy { it.name.lowercase() },
            selectedMeterId = id, meterEditor = null, snackbar = "Zähler gespeichert",
        )
    }

    private fun saveReading(state: NotEcasUiState): NotEcasUiState {
        val editor = state.readingEditor ?: return state
        val value = editor.value.replace(',', '.').toDoubleOrNull()
        val timestamp = runCatching { LocalDateTime.parse("${editor.date}T${editor.time}", LEGACY_TIMESTAMP_FORMAT) }.getOrNull()
        if (value == null || !value.isFinite() || timestamp == null || editor.color !in 0..4) {
            return state.copy(snackbar = "Messwert, Datum und Uhrzeit bitte gültig eingeben")
        }
        val all = state.readings.values.flatten()
        val id = editor.id ?: ((all.maxOfOrNull { it.id } ?: 0L) + 1)
        val row = LegacyReadingRecord(id, editor.meterId, timestamp.format(LEGACY_TIMESTAMP_FORMAT), value,
            editor.comment.trim().ifBlank { null }, editor.color, first = if (editor.first) 1 else 0)
        val rows = (state.readings[editor.meterId].orEmpty().filterNot { it.id == id || it.date == row.date } + row).sortedBy { it.date }
        return state.copy(readings = state.readings + (editor.meterId to rows), readingEditor = null, snackbar = "Messwert gespeichert")
    }

    private fun newReadingEditor(state: NotEcasUiState, meterId: Long): ReadingEditor {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val last = state.readings[meterId].orEmpty().maxByOrNull { it.date }?.value
        return ReadingEditor(
            null,
            meterId,
            date = now.toLocalDate().toString(),
            time = now.toLocalTime().format(EDITOR_TIME_FORMAT),
            lastValue = last,
        )
    }

    private fun preview(state: NotEcasUiState, editor: ReadingEditor): ReadingEditor {
        val meter = state.meters.firstOrNull { it.id == editor.meterId } ?: return editor
        val last = state.readings[editor.meterId].orEmpty().filterNot { it.id == editor.id }.maxByOrNull { it.date }
        val value = editor.value.replace(',', '.').toDoubleOrNull()
        val delta = if (last != null && value != null) runCatching {
            MeterCalculations.delta(meter.toDomain(), last.toDomain(), LegacyReadingRecord(0, meter.id, last.date, value, first = if (editor.first) 1 else 0).toDomain())
        }.getOrNull() else null
        return editor.copy(lastValue = last?.value, previewDelta = delta)
    }
}

private fun LegacyMeterRecord.toEditor() = MeterEditor(
    id, name, units, comment.orEmpty(), type == 1, production, invert, bidirectional, scaling.toString(),
    physicalUnits.orEmpty(), !vmsetup.isNullOrBlank(), vmsetup.orEmpty(), currency.orEmpty(), cost.toString(),
    fixedCost.toString(), vsf.toFloat(), tsf.toFloat(),
)

private fun LegacyReadingRecord.toEditor(state: NotEcasUiState): ReadingEditor {
    val timestamp = LocalDateTime.parse(date, LEGACY_TIMESTAMP_FORMAT)
    return ReadingEditor(id, meterId, value.toString(), timestamp.toLocalDate().toString(), timestamp.toLocalTime().format(EDITOR_TIME_FORMAT),
        comment.orEmpty(), color, first == 1).let { editor ->
        val last = state.readings[meterId].orEmpty().filter { it.id != id && it.date < date }.maxByOrNull { it.date }
        editor.copy(lastValue = last?.value)
    }
}

fun buildAnalysis(
    meter: LegacyMeterRecord,
    rows: List<LegacyReadingRecord>,
    period: AnalysisPeriod,
    now: LocalDateTime = LocalDateTime.now(),
    year: Int = now.year,
): AnalysisModel {
    val ordered = rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() }.sortedBy { it.timestamp }
    if (ordered.isEmpty()) return AnalysisModel()
    val start = when (period) {
        AnalysisPeriod.MONTH -> LocalDateTime.of(year, now.monthValue, 1, 0, 0)
        AnalysisPeriod.QUARTER -> LocalDateTime.of(year, ((now.monthValue - 1) / 3) * 3 + 1, 1, 0, 0)
        AnalysisPeriod.YEAR -> LocalDateTime.of(year, 1, 1, 0, 0)
    }
    val naturalEnd = when (period) {
        AnalysisPeriod.MONTH -> start.plusMonths(1)
        AnalysisPeriod.QUARTER -> start.plusMonths(3)
        AnalysisPeriod.YEAR -> start.plusYears(1)
    }
    val end = if (year == now.year && now in start..<naturalEnd) now else naturalEnd
    val domainMeter = meter.toDomain()
    fun consumption(from: LocalDateTime, until: LocalDateTime): Double = ordered.zipWithNext().sumOf { (old, new) ->
        val overlapStart = maxOf(from, old.timestamp)
        val overlapEnd = minOf(until, new.timestamp)
        if (overlapStart >= overlapEnd) 0.0 else {
            val total = Duration.between(old.timestamp, new.timestamp).toNanos().toDouble()
            val overlap = Duration.between(overlapStart, overlapEnd).toNanos().toDouble()
            MeterCalculations.delta(domainMeter, old, new) * overlap / total
        }
    }
    val current = consumption(start, end)
    val previousStart = start.minusYears(1)
    val previousEnd = end.minusYears(1)
    val previous = consumption(previousStart, previousEnd).takeIf {
        ordered.first().timestamp <= previousStart && ordered.last().timestamp >= previousEnd
    }
    val points = if (meter.type == 1) {
        ordered.filter { it.timestamp in start..<end }
            .map { AnalysisPoint(it.timestamp.toLocalDate().toString(), MeterCalculations.scaledValue(domainMeter, it)) }
    } else {
        val bucketStarts = when (period) {
            AnalysisPeriod.MONTH -> generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
            AnalysisPeriod.QUARTER, AnalysisPeriod.YEAR -> generateSequence(start) { it.plusMonths(1) }.takeWhile { it < end }.toList()
        }
        bucketStarts.map { bucketStart ->
            val bucketEnd = when (period) {
                AnalysisPeriod.MONTH -> minOf(bucketStart.plusDays(1), end)
                AnalysisPeriod.QUARTER, AnalysisPeriod.YEAR -> minOf(bucketStart.plusMonths(1), end)
            }
            AnalysisPoint(bucketStart.toLocalDate().toString(), consumption(bucketStart, bucketEnd))
        }
    }
    return AnalysisModel(points, current, current * meter.cost, previous)
}
