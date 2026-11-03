package de.mohcomputer.notecas.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualMeterTest {
    private val physical1 = Meter(1, "Import", "kWh", costPerUnit = 0.30)
    private val physical2 = Meter(2, "Export", "kWh", costPerUnit = 0.10, flags = MeterFlags(bidirectional = true))
    private val virtual10 = Meter(10, "Balance", "kWh", type = MeterType.VIRTUAL)

    @Test
    fun `parses alternating source ids and finite nonzero factors`() {
        val result = VirtualMeterDefinitions.parse(10, "1|2.5|2|-0.5", mapOf(1L to physical1, 2L to physical2))

        assertEquals(listOf(VirtualComponent(1, 2.5), VirtualComponent(2, -0.5)), result.components)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `ignores missing ids and zero factors while reporting validation issues`() {
        val result = VirtualMeterDefinitions.parse(10, "1|0|99|2|2|1", mapOf(1L to physical1, 2L to physical2))

        assertEquals(listOf(VirtualComponent(2, 1.0)), result.components)
        assertEquals(listOf(VirtualIssueCode.ZERO_FACTOR, VirtualIssueCode.MISSING_SOURCE), result.issues.map { it.code })
    }

    @Test
    fun `reports malformed token pairs without inventing components`() {
        val result = VirtualMeterDefinitions.parse(10, "one|2|1|NaN|2", mapOf(1L to physical1, 2L to physical2))

        assertTrue(result.components.isEmpty())
        assertEquals(3, result.issues.size)
        assertTrue(result.issues.all { it.code == VirtualIssueCode.MALFORMED_DEFINITION })
    }

    @Test
    fun `detects direct and indirect virtual meter cycles`() {
        val meters = mapOf(10L to virtual10, 11L to virtual10.copy(id = 11), 12L to virtual10.copy(id = 12))
        val definitions = mapOf(
            10L to listOf(VirtualComponent(11, 1.0)),
            11L to listOf(VirtualComponent(12, 1.0)),
            12L to listOf(VirtualComponent(10, 1.0)),
        )

        val cyclic = VirtualMeterDefinitions.cyclicMeterIds(definitions, meters)

        assertEquals(setOf(10L, 11L, 12L), cyclic)
    }

    @Test
    fun `calculates weighted value with factor magnitude but cost with factor sign`() {
        val result = VirtualMeterCalculations.combine(
            components = listOf(VirtualComponent(1, 2.0), VirtualComponent(2, -0.5)),
            sources = mapOf(
                1L to SourceInterval(delta = 10.0, costPerUnit = 0.30),
                2L to SourceInterval(delta = 4.0, costPerUnit = 0.10),
            ),
        )

        assertEquals(18.0, result.delta, 1e-12)
        assertEquals(2.6, result.variableCost, 1e-12)
    }

    @Test
    fun `interpolates asynchronous source intervals before combining them`() {
        val base = LocalDateTime.of(2026, 1, 1, 0, 0)
        val readings = mapOf(
            1L to listOf(Reading(1, 1, base, 0.0), Reading(2, 1, base.plusHours(2), 20.0)),
            2L to listOf(Reading(3, 2, base.plusMinutes(15), 10.0), Reading(4, 2, base.plusHours(1).plusMinutes(45), 16.0)),
        )

        val result = VirtualMeterCalculations.between(
            components = listOf(VirtualComponent(1, 2.0), VirtualComponent(2, -0.5)),
            meters = mapOf(1L to physical1, 2L to physical2),
            readingsByMeter = readings,
            from = base.plusMinutes(30),
            until = base.plusMinutes(90),
        )

        assertEquals(18.0, result.delta, 1e-12)
        assertEquals(2.6, result.variableCost, 1e-12)
    }
}
