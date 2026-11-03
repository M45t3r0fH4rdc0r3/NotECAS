package de.mohcomputer.notecas.domain

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

enum class MeterType {
    CUMULATIVE,
    SENSOR,
    VIRTUAL,
}

data class MeterFlags(
    val inverted: Boolean = false,
    val bidirectional: Boolean = false,
    val production: Boolean = false,
)

data class Meter(
    val id: Long,
    val name: String,
    val unit: String,
    val type: MeterType = MeterType.CUMULATIVE,
    val scaling: Double = 1.0,
    val costPerUnit: Double = 0.0,
    val annualFixedCost: Double = 0.0,
    val flags: MeterFlags = MeterFlags(),
) {
    init {
        require(id >= 0) { "Meter id must not be negative" }
        require(scaling.isFinite()) { "Scaling must be finite" }
        require(costPerUnit.isFinite()) { "Variable cost must be finite" }
        require(annualFixedCost.isFinite()) { "Fixed cost must be finite" }
    }
}

data class Reading(
    val id: Long,
    val meterId: Long,
    val timestamp: LocalDateTime,
    val rawValue: Double,
    val isFirst: Boolean = false,
) {
    init {
        require(rawValue.isFinite()) { "Reading value must be finite" }
    }
}

data class IntervalResult(
    val delta: Double,
    val elapsed: Duration,
    val variableCost: Double,
    val fixedCost: Double,
) {
    val totalCost: Double get() = variableCost + fixedCost
}

object MeterCalculations {
    private const val ROLLOVER_THRESHOLD = 90_000.0
    private const val DAYS_PER_YEAR = 365L

    fun scaledValue(meter: Meter, reading: Reading): Double = reading.rawValue * meter.scaling

    fun delta(meter: Meter, older: Reading, newer: Reading): Double {
        require(older.meterId == meter.id && newer.meterId == meter.id) {
            "Readings must belong to meter ${meter.id}"
        }
        if (newer.isFirst) return 0.0

        val oldScaled = scaledValue(meter, older)
        val newScaled = scaledValue(meter, newer)
        var delta = if (meter.flags.inverted) oldScaled - newScaled else newScaled - oldScaled

        if (meter.flags.bidirectional && abs(delta) > ROLLOVER_THRESHOLD) {
            val largest = max(oldScaled, newScaled).let { if (it > 0.0) it else max(abs(oldScaled), abs(newScaled)) }
            if (largest > 0.0) {
                val modulus = 10.0.pow(ceil(log10(largest)))
                delta -= sign(delta) * modulus
            }
        }

        val retainsNegative = meter.type == MeterType.SENSOR ||
            meter.type == MeterType.VIRTUAL || meter.flags.bidirectional
        return if (!retainsNegative && delta < 0.0) 0.0 else delta
    }

    fun interval(meter: Meter, older: Reading, newer: Reading): IntervalResult {
        val elapsed = Duration.between(older.timestamp, newer.timestamp)
        require(!elapsed.isNegative) { "Newer reading must not precede older reading" }
        val delta = delta(meter, older, newer)
        val fixedCost = meter.annualFixedCost * elapsed.toMillis().toDouble() /
            Duration.ofDays(DAYS_PER_YEAR).toMillis().toDouble()
        return IntervalResult(
            delta = delta,
            elapsed = elapsed,
            variableCost = delta * meter.costPerUnit,
            fixedCost = fixedCost,
        )
    }
}
