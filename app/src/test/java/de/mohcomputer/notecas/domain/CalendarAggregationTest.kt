package de.mohcomputer.notecas.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalendarAggregationTest {
    private val meter = Meter(1, "Water", "m³", costPerUnit = 0.5, annualFixedCost = 365.0)

    @Test
    fun `linearly interpolates scaled cumulative value at a period boundary`() {
        val readings = listOf(
            Reading(1, 1, LocalDateTime.of(2026, 1, 1, 12, 0), 0.0),
            Reading(2, 1, LocalDateTime.of(2026, 1, 3, 12, 0), 48.0),
        )

        val interpolated = ReadingInterpolation.valueAt(meter, readings, LocalDateTime.of(2026, 1, 2, 0, 0))

        assertNotNull(interpolated)
        assertEquals(12.0, interpolated!!, 1e-12)
    }

    @Test
    fun `interpolation rejects timestamps outside reading coverage`() {
        val readings = listOf(
            Reading(1, 1, LocalDateTime.of(2026, 1, 2, 0, 0), 1.0),
            Reading(2, 1, LocalDateTime.of(2026, 1, 3, 0, 0), 2.0),
        )

        assertEquals(null, ReadingInterpolation.valueAt(meter, readings, LocalDateTime.of(2026, 1, 1, 0, 0)))
    }

    @Test
    fun `calendar periods use local calendar boundaries`() {
        val instant = LocalDateTime.of(2026, 5, 17, 13, 45)

        assertEquals(LocalDateTime.of(2026, 5, 17, 0, 0), CalendarPeriod.DAY.startContaining(instant))
        assertEquals(LocalDateTime.of(2026, 5, 11, 0, 0), CalendarPeriod.WEEK.startContaining(instant))
        assertEquals(LocalDateTime.of(2026, 5, 1, 0, 0), CalendarPeriod.MONTH.startContaining(instant))
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), CalendarPeriod.QUARTER.startContaining(instant))
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), CalendarPeriod.YEAR.startContaining(instant))
    }

    @Test
    fun `aggregates interpolated consumption and costs into daily buckets`() {
        val readings = listOf(
            Reading(1, 1, LocalDateTime.of(2026, 1, 1, 12, 0), 0.0),
            Reading(2, 1, LocalDateTime.of(2026, 1, 3, 12, 0), 48.0),
        )

        val buckets = CalendarAggregator.aggregate(
            meter = meter,
            readings = readings,
            from = LocalDate.of(2026, 1, 2).atStartOfDay(),
            until = LocalDate.of(2026, 1, 4).atStartOfDay(),
            period = CalendarPeriod.DAY,
        )

        assertEquals(1, buckets.size)
        assertEquals(LocalDate.of(2026, 1, 2).atStartOfDay(), buckets.single().start)
        assertEquals(24.0, buckets.single().delta, 1e-12)
        assertEquals(12.0, buckets.single().variableCost, 1e-12)
        assertEquals(1.0, buckets.single().fixedCost, 1e-12)
    }

    @Test
    fun `aggregation honors a reset reading inside a calendar bucket`() {
        val readings = listOf(
            Reading(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0), 100.0),
            Reading(2, 1, LocalDateTime.of(2026, 1, 1, 12, 0), 5.0, isFirst = true),
            Reading(3, 1, LocalDateTime.of(2026, 1, 2, 0, 0), 9.0),
        )

        val bucket = CalendarAggregator.aggregate(
            meter,
            readings,
            LocalDate.of(2026, 1, 1).atStartOfDay(),
            LocalDate.of(2026, 1, 2).atStartOfDay(),
            CalendarPeriod.DAY,
        ).single()

        assertEquals(4.0, bucket.delta, 0.0)
    }
}
