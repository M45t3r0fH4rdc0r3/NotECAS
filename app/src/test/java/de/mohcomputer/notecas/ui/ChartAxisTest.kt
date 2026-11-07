package de.mohcomputer.notecas.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {
    @Test fun `year view keeps month labels when sensor data covers only one month`() {
        val points = listOf(
            AnalysisPoint("2025-01-03", 10.0),
            AnalysisPoint("2025-01-17", 12.0),
            AnalysisPoint("2025-01-29", 11.0),
        )

        assertEquals(listOf(ChartAxisTick(1, "Jan")), chartAxisTicks(points, AnalysisPeriod.YEAR))
    }

    @Test fun `irregular sensor month labels retain their data point positions`() {
        val points = listOf(
            AnalysisPoint("2025-01-02", 10.0),
            AnalysisPoint("2025-01-20", 12.0),
            AnalysisPoint("2025-03-08", 14.0),
            AnalysisPoint("2025-09-01", 13.0),
            AnalysisPoint("2025-09-22", 15.0),
        )

        assertEquals(
            listOf(ChartAxisTick(1, "Jan"), ChartAxisTick(2, "Mär"), ChartAxisTick(4, "Sep")),
            chartAxisTicks(points, AnalysisPeriod.YEAR),
        )
    }

    @Test fun `month view limits daily labels while preserving first and last position`() {
        val points = (1..31).map { day -> AnalysisPoint("2025-01-${day.toString().padStart(2, '0')}", day.toDouble()) }

        val ticks = chartAxisTicks(points, AnalysisPeriod.MONTH)

        assertEquals(6, ticks.size)
        assertEquals(ChartAxisTick(0, "1"), ticks.first())
        assertEquals(ChartAxisTick(30, "31"), ticks.last())
        assertTrue(ticks.zipWithNext().all { (left, right) -> left.pointIndex < right.pointIndex })
    }
}
