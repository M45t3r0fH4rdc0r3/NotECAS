package de.mohcomputer.notecas.domain

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MeterCalculationTest {
    private val start = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `scales readings before calculating cumulative consumption`() {
        val meter = Meter(id = 1, name = "Gas", unit = "kWh", scaling = 10.0)

        val result = MeterCalculations.interval(
            meter,
            Reading(1, meter.id, start, 12.0),
            Reading(2, meter.id, start.plusHours(2), 14.5),
        )

        assertEquals(25.0, result.delta, 0.0)
    }

    @Test
    fun `inverted meter consumes when its reading decreases`() {
        val meter = Meter(1, "Prepaid", "kWh", flags = MeterFlags(inverted = true))

        val result = MeterCalculations.interval(
            meter,
            Reading(1, 1, start, 100.0),
            Reading(2, 1, start.plusDays(1), 92.0),
        )

        assertEquals(8.0, result.delta, 0.0)
    }

    @Test
    fun `new start marker makes transition consumption zero`() {
        val meter = Meter(1, "Water", "m³")

        val result = MeterCalculations.interval(
            meter,
            Reading(1, 1, start, 100.0),
            Reading(2, 1, start.plusDays(1), 5.0, isFirst = true),
        )

        assertEquals(0.0, result.delta, 0.0)
    }

    @Test
    fun `ordinary meter clamps a negative delta while sensor and virtual retain it`() {
        val old = Reading(1, 1, start, 10.0)
        val new = Reading(2, 1, start.plusHours(1), 4.0)

        assertEquals(0.0, MeterCalculations.delta(Meter(1, "Power", "kWh"), old, new), 0.0)
        assertEquals(-6.0, MeterCalculations.delta(Meter(1, "Temp", "°C", type = MeterType.SENSOR), old, new), 0.0)
        assertEquals(-6.0, MeterCalculations.delta(Meter(1, "Combined", "kWh", type = MeterType.VIRTUAL), old, new), 0.0)
    }

    @Test
    fun `bidirectional rollover is corrected in both directions`() {
        val meter = Meter(1, "Grid", "kWh", flags = MeterFlags(bidirectional = true))

        val forward = MeterCalculations.delta(
            meter,
            Reading(1, 1, start, 99_990.0),
            Reading(2, 1, start.plusHours(1), 10.0),
        )
        val backward = MeterCalculations.delta(
            meter,
            Reading(3, 1, start, 10.0),
            Reading(4, 1, start.plusHours(1), 99_990.0),
        )

        assertEquals(20.0, forward, 0.0)
        assertEquals(-20.0, backward, 0.0)
    }

    @Test
    fun `interval cost combines variable and prorated annual fixed cost`() {
        val meter = Meter(1, "Power", "kWh", costPerUnit = 0.4, annualFixedCost = 365.0)

        val result = MeterCalculations.interval(
            meter,
            Reading(1, 1, start, 10.0),
            Reading(2, 1, start.plusDays(2), 15.0),
        )

        assertEquals(2.0, result.variableCost, 1e-12)
        assertEquals(2.0, result.fixedCost, 1e-12)
        assertEquals(4.0, result.totalCost, 1e-12)
        assertEquals(Duration.ofDays(2), result.elapsed)
    }
}
