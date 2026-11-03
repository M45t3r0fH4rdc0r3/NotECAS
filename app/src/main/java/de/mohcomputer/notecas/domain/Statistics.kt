package de.mohcomputer.notecas.domain

import java.time.Duration

enum class FixedPeriod(val duration: Duration) {
    HOUR(Duration.ofHours(1)),
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH_30_DAYS(Duration.ofDays(30)),
    YEAR_365_DAYS(Duration.ofDays(365)),
}

object TimeNormalization {
    fun rate(delta: Double, elapsed: Duration, period: FixedPeriod): Double {
        require(!elapsed.isZero && !elapsed.isNegative) { "Elapsed duration must be positive" }
        return delta * period.duration.toNanos().toDouble() / elapsed.toNanos().toDouble()
    }
}

data class SensorPeriodStatistics(
    val minimum: Double,
    val maximum: Double,
) {
    val legacyMidpoint: Double get() = (minimum + maximum) / 2.0
    val range: Double get() = maximum - minimum
}

object SensorStatistics {
    fun calculate(meter: Meter, readings: List<Reading>): SensorPeriodStatistics? {
        require(meter.type == MeterType.SENSOR) { "Statistics require a sensor meter" }
        if (readings.isEmpty()) return null
        val values = readings.map {
            require(it.meterId == meter.id) { "Reading ${it.id} belongs to another meter" }
            MeterCalculations.scaledValue(meter, it)
        }
        return SensorPeriodStatistics(values.min(), values.max())
    }
}
