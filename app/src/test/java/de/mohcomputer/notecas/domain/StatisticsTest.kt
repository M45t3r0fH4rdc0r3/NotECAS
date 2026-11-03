package de.mohcomputer.notecas.domain

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsTest {
    private val start = LocalDateTime.of(2026, 2, 1, 0, 0)

    @Test
    fun `normalizes interval delta to ECAS fixed time periods`() {
        val elapsed = Duration.ofHours(12)

        assertEquals(2.0, TimeNormalization.rate(1.0, elapsed, FixedPeriod.DAY), 0.0)
        assertEquals(14.0, TimeNormalization.rate(1.0, elapsed, FixedPeriod.WEEK), 0.0)
        assertEquals(60.0, TimeNormalization.rate(1.0, elapsed, FixedPeriod.MONTH_30_DAYS), 0.0)
        assertEquals(730.0, TimeNormalization.rate(1.0, elapsed, FixedPeriod.YEAR_365_DAYS), 0.0)
        assertEquals(1.0 / 12.0, TimeNormalization.rate(1.0, elapsed, FixedPeriod.HOUR), 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalization rejects a zero elapsed interval`() {
        TimeNormalization.rate(10.0, Duration.ZERO, FixedPeriod.DAY)
    }

    @Test
    fun `legacy sensor statistic is midpoint of extrema rather than arithmetic mean`() {
        val meter = Meter(1, "Temperature", "°C", type = MeterType.SENSOR, scaling = 2.0)
        val readings = listOf(
            Reading(1, 1, start, 1.0),
            Reading(2, 1, start.plusHours(1), 2.0),
            Reading(3, 1, start.plusHours(2), 9.0),
        )

        val statistics = SensorStatistics.calculate(meter, readings)!!

        assertEquals(2.0, statistics.minimum, 0.0)
        assertEquals(18.0, statistics.maximum, 0.0)
        assertEquals(10.0, statistics.legacyMidpoint, 0.0)
        assertEquals(16.0, statistics.range, 0.0)
    }

    @Test
    fun `sensor statistic is absent without readings`() {
        assertNull(SensorStatistics.calculate(Meter(1, "Temperature", "°C", type = MeterType.SENSOR), emptyList()))
    }
}
