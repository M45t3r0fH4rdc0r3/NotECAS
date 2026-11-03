package de.mohcomputer.notecas.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

object ReadingInterpolation {
    fun valueAt(meter: Meter, readings: List<Reading>, timestamp: LocalDateTime): Double? {
        val ordered = readings.sortedBy { it.timestamp }
        ordered.firstOrNull { it.timestamp == timestamp }?.let {
            return MeterCalculations.scaledValue(meter, it)
        }
        val newerIndex = ordered.indexOfFirst { it.timestamp > timestamp }
        if (newerIndex <= 0) return null
        val older = ordered[newerIndex - 1]
        val newer = ordered[newerIndex]
        require(older.meterId == meter.id && newer.meterId == meter.id) {
            "Readings must belong to meter ${meter.id}"
        }
        val complete = Duration.between(older.timestamp, newer.timestamp).toNanos().toDouble()
        val elapsed = Duration.between(older.timestamp, timestamp).toNanos().toDouble()
        val fraction = elapsed / complete
        val oldValue = MeterCalculations.scaledValue(meter, older)
        val newValue = MeterCalculations.scaledValue(meter, newer)
        return oldValue + (newValue - oldValue) * fraction
    }
}

enum class CalendarPeriod {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    fun startContaining(timestamp: LocalDateTime): LocalDateTime {
        val date = timestamp.toLocalDate()
        val startDate = when (this) {
            DAY -> date
            WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MONTH -> date.withDayOfMonth(1)
            QUARTER -> date.withMonth(((date.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1)
            YEAR -> date.withDayOfYear(1)
        }
        return startDate.atStartOfDay()
    }

    fun next(start: LocalDateTime): LocalDateTime = when (this) {
        DAY -> start.plusDays(1)
        WEEK -> start.plusWeeks(1)
        MONTH -> start.plusMonths(1)
        QUARTER -> start.plusMonths(3)
        YEAR -> start.plusYears(1)
    }
}

data class CalendarBucket(
    val start: LocalDateTime,
    val endExclusive: LocalDateTime,
    val delta: Double,
    val variableCost: Double,
    val fixedCost: Double,
) {
    val totalCost: Double get() = variableCost + fixedCost
}

object CalendarAggregator {
    fun aggregate(
        meter: Meter,
        readings: List<Reading>,
        from: LocalDateTime,
        until: LocalDateTime,
        period: CalendarPeriod,
    ): List<CalendarBucket> {
        require(from < until) { "Aggregation range must be positive" }
        require(meter.type != MeterType.SENSOR) { "Use sensor statistics for sensor meters" }
        val ordered = readings.sortedBy { it.timestamp }
        if (ordered.size < 2) return emptyList()
        require(ordered.all { it.meterId == meter.id }) { "All readings must belong to meter ${meter.id}" }
        require(ordered.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp }) {
            "Reading timestamps must be unique"
        }

        val result = mutableListOf<CalendarBucket>()
        var bucketStart = period.startContaining(from)
        while (bucketStart < until) {
            val bucketEnd = period.next(bucketStart)
            if (bucketStart >= from && bucketEnd <= until &&
                bucketStart >= ordered.first().timestamp && bucketEnd <= ordered.last().timestamp
            ) {
                var delta = 0.0
                for ((older, newer) in ordered.zipWithNext()) {
                    val overlapStart = maxOf(bucketStart, older.timestamp)
                    val overlapEnd = minOf(bucketEnd, newer.timestamp)
                    if (overlapStart < overlapEnd) {
                        val intervalNanos = Duration.between(older.timestamp, newer.timestamp).toNanos().toDouble()
                        val overlapNanos = Duration.between(overlapStart, overlapEnd).toNanos().toDouble()
                        delta += MeterCalculations.delta(meter, older, newer) * overlapNanos / intervalNanos
                    }
                }
                val elapsedMillis = Duration.between(bucketStart, bucketEnd).toMillis().toDouble()
                val fixedCost = meter.annualFixedCost * elapsedMillis /
                    Duration.ofDays(365).toMillis().toDouble()
                result += CalendarBucket(
                    start = bucketStart,
                    endExclusive = bucketEnd,
                    delta = delta,
                    variableCost = delta * meter.costPerUnit,
                    fixedCost = fixedCost,
                )
            }
            bucketStart = bucketEnd
        }
        return result
    }
}
