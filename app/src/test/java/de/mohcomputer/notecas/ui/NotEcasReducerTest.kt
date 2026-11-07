package de.mohcomputer.notecas.ui

import de.mohcomputer.notecas.data.LegacyMeterRecord
import de.mohcomputer.notecas.data.LegacyReadingRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotEcasReducerTest {
    private val meter = LegacyMeterRecord(
        id = 1, name = "Strom", units = "kWh", comment = "Keller", vsf = 50, tsf = 50,
        cost = 0.31, fixedCost = 120.0, invert = false, vmsetup = null, type = 0,
        currency = "€", scaling = 1.0, physicalUnits = "kWh", bidirectional = false,
        production = false,
    )

    @Test fun `navigation selects requested root destination and closes overlays`() {
        val result = NotEcasReducer.reduce(
            NotEcasUiState(destination = RootDestination.OVERVIEW, overlay = Overlay.ABOUT),
            UiAction.Navigate(RootDestination.ANALYSIS),
        )
        assertEquals(RootDestination.ANALYSIS, result.destination)
        assertEquals(null, result.overlay)
    }

    @Test fun `meter filters distinguish sensors production and virtual meters`() {
        val meters = listOf(
            meter,
            meter.copy(id = 2, type = 1, name = "Temperatur"),
            meter.copy(id = 3, production = true, name = "PV"),
            meter.copy(id = 4, vmsetup = "1|1", name = "Gesamt"),
        )
        assertEquals(listOf("Temperatur"), filterMeters(meters, MeterFilter.SENSORS).map { it.name })
        assertEquals(listOf("PV"), filterMeters(meters, MeterFilter.PRODUCTION).map { it.name })
        assertEquals(listOf("Gesamt"), filterMeters(meters, MeterFilter.VIRTUAL).map { it.name })
    }

    @Test fun `reading editor previews domain delta from last reading`() {
        val state = NotEcasUiState(
            meters = listOf(meter),
            readings = mapOf(1L to listOf(LegacyReadingRecord(1, 1, "2026-10-01T10:00:00", 100.0))),
            selectedMeterId = 1,
        )
        val edited = NotEcasReducer.reduce(state, UiAction.OpenNewReading(1))
        val withValue = NotEcasReducer.reduce(edited, UiAction.ChangeReadingValue("112.5"))
        assertEquals(12.5, withValue.readingEditor?.previewDelta ?: Double.NaN, 0.0001)
        assertEquals(100.0, withValue.readingEditor?.lastValue ?: Double.NaN, 0.0001)
    }

    @Test fun `saving and deleting meter updates visible state`() {
        val opened = NotEcasReducer.reduce(NotEcasUiState(), UiAction.OpenNewMeter)
        val filled = opened.copy(meterEditor = opened.meterEditor?.copy(name = "Gas", unit = "m³"))
        val saved = NotEcasReducer.reduce(filled, UiAction.SaveMeter)
        assertEquals("Gas", saved.meters.single().name)
        assertNotNull(saved.snackbar)
        val deleted = NotEcasReducer.reduce(saved, UiAction.DeleteMeter(saved.meters.single().id))
        assertTrue(deleted.meters.isEmpty())
    }

    @Test fun `invalid reading is rejected with German error`() {
        val state = NotEcasUiState(meters = listOf(meter), selectedMeterId = 1)
        val opened = NotEcasReducer.reduce(state, UiAction.OpenNewReading(1))
        val result = NotEcasReducer.reduce(opened, UiAction.SaveReading)
        assertFalse(result.readingEditor == null)
        assertTrue(result.snackbar.orEmpty().contains("Messwert"))
    }

    @Test fun `new reading editor always supplies seconds accepted by legacy timestamp format`() {
        val state = NotEcasUiState(meters = listOf(meter), selectedMeterId = 1)
        val opened = NotEcasReducer.reduce(state, UiAction.OpenNewReading(1))
        val editor = requireNotNull(opened.readingEditor)

        assertTrue(editor.time.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
        val saved = NotEcasReducer.reduce(opened, UiAction.ChangeReadingValue("42"))
            .let { NotEcasReducer.reduce(it, UiAction.SaveReading) }
        assertTrue(saved.readingEditor == null)
        assertEquals(42.0, saved.readings.getValue(1L).single().value, 0.0)
    }

    @Test fun `analysis year navigation selects an earlier year`() {
        val state = NotEcasUiState(analysisYear = 2026)
        val result = NotEcasReducer.reduce(state, UiAction.ChangeAnalysisYear(-1))
        assertEquals(2025, result.analysisYear)
    }

    @Test fun `historical analysis calculates the selected full year and its previous year`() {
        val readings = listOf(
            LegacyReadingRecord(1, 1, "2024-01-01T00:00:00", 0.0),
            LegacyReadingRecord(2, 1, "2025-01-01T00:00:00", 100.0),
            LegacyReadingRecord(3, 1, "2026-01-01T00:00:00", 250.0),
        )

        val model = buildAnalysis(
            meter = meter,
            rows = readings,
            period = AnalysisPeriod.YEAR,
            now = java.time.LocalDateTime.of(2026, 7, 1, 0, 0),
            year = 2025,
        )

        assertEquals(150.0, model.consumption, 0.0001)
        assertEquals(46.5, model.cost, 0.0001)
        assertEquals(100.0, model.previousYearConsumption ?: Double.NaN, 0.0001)
        assertTrue(model.points.any { it.label.startsWith("2025-") })
        assertEquals(12, model.previousPoints.size)
        assertEquals(100.0, model.previousPoints.sumOf { it.value }, 0.0001)
    }

    @Test fun `annual comparison table contains prior year delta and percentage`() {
        val rows = buildAnalysisTableRows(
            current = listOf(AnalysisPoint("2025-01-01", 10.0), AnalysisPoint("2025-02-01", 20.0)),
            previous = listOf(AnalysisPoint("2024-01-01", 8.0), AnalysisPoint("2024-02-01", 25.0)),
            period = AnalysisPeriod.YEAR,
            sensor = false,
        )

        assertEquals("Jan", rows.first().label)
        assertEquals(10.0, rows.first().current, 0.0)
        assertEquals(8.0, rows.first().previous ?: Double.NaN, 0.0)
        assertEquals(2.0, rows.first().change ?: Double.NaN, 0.0)
        assertEquals(25.0, rows.first().changePercent ?: Double.NaN, 0.0)
    }

    @Test fun `sensor comparison table averages repeated readings per month`() {
        val rows = buildAnalysisTableRows(
            current = listOf(AnalysisPoint("2025-01-02", 10.0), AnalysisPoint("2025-01-20", 14.0)),
            previous = listOf(AnalysisPoint("2024-01-05", 8.0), AnalysisPoint("2024-01-25", 12.0)),
            period = AnalysisPeriod.YEAR,
            sensor = true,
        )

        assertEquals(1, rows.size)
        assertEquals(12.0, rows.single().current, 0.0)
        assertEquals(10.0, rows.single().previous ?: Double.NaN, 0.0)
    }

    @Test fun `analysis leaves a cumulative period without interval coverage empty`() {
        val model = buildAnalysis(
            meter = meter,
            rows = listOf(
                LegacyReadingRecord(1, meter.id, "2024-01-01T00:00:00", 0.0),
                LegacyReadingRecord(2, meter.id, "2024-12-31T00:00:00", 100.0),
            ),
            period = AnalysisPeriod.YEAR,
            now = java.time.LocalDateTime.of(2026, 7, 1, 0, 0),
            year = 2025,
        )

        assertTrue(model.points.isEmpty())
    }

    @Test fun `analysis omits cumulative buckets without interval coverage`() {
        val model = buildAnalysis(
            meter = meter,
            rows = listOf(
                LegacyReadingRecord(1, meter.id, "2025-01-15T00:00:00", 0.0),
                LegacyReadingRecord(2, meter.id, "2025-02-15T00:00:00", 31.0),
            ),
            period = AnalysisPeriod.YEAR,
            now = java.time.LocalDateTime.of(2026, 7, 1, 0, 0),
            year = 2025,
        )

        assertEquals(listOf("2025-01-01", "2025-02-01"), model.points.map { it.label })
        assertEquals(31.0, model.points.sumOf { it.value }, 0.0001)
    }

    @Test fun `sensor analysis keeps previous year readings without boundary coverage`() {
        val sensor = meter.copy(type = 1)
        val model = buildAnalysis(
            meter = sensor,
            rows = listOf(
                LegacyReadingRecord(1, sensor.id, "2024-06-15T12:00:00", 10.0),
                LegacyReadingRecord(2, sensor.id, "2025-06-15T12:00:00", 12.0),
            ),
            period = AnalysisPeriod.YEAR,
            now = java.time.LocalDateTime.of(2026, 7, 1, 0, 0),
            year = 2025,
        )

        assertEquals(listOf("2024-06-15"), model.previousPoints.map { it.label })
    }

    @Test fun `sensor table total averages all individual readings`() {
        val points = listOf(
            AnalysisPoint("2025-01-02", 10.0),
            AnalysisPoint("2025-01-20", 14.0),
            AnalysisPoint("2025-02-10", 100.0),
        )

        assertEquals(124.0 / 3.0, analysisTableTotal(points, sensor = true) ?: Double.NaN, 0.0001)
    }

    @Test fun `sensor analysis excludes readings at the end of each period`() {
        val sensor = meter.copy(type = 1)
        val now = java.time.LocalDateTime.of(2026, 5, 15, 12, 0)
        val cases = listOf(
            Triple(AnalysisPeriod.MONTH, "2025-05-01T00:00:00", "2025-06-01T00:00:00"),
            Triple(AnalysisPeriod.QUARTER, "2025-04-01T00:00:00", "2025-07-01T00:00:00"),
            Triple(AnalysisPeriod.YEAR, "2025-01-01T00:00:00", "2026-01-01T00:00:00"),
        )

        cases.forEach { (period, start, exclusiveEnd) ->
            val model = buildAnalysis(
                meter = sensor,
                rows = listOf(
                    LegacyReadingRecord(1, sensor.id, start, 10.0),
                    LegacyReadingRecord(2, sensor.id, exclusiveEnd, 20.0),
                ),
                period = period,
                now = now,
                year = 2025,
            )

            assertEquals("$period must exclude its end boundary", listOf(start.substringBefore('T')), model.points.map { it.label })
        }
    }

    @Test fun `ECAS smoothing changes only displayed trend points`() {
        val raw = listOf(
            AnalysisPoint("1", 0.0),
            AnalysisPoint("2", 0.0),
            AnalysisPoint("3", 100.0),
            AnalysisPoint("4", 0.0),
        )

        val smoothed = smoothAnalysisPoints(raw, valueSmoothing = 50, trendSmoothing = 50)

        assertEquals(listOf("1", "2", "3", "4"), smoothed.map { it.label })
        assertEquals(50.1, smoothed[2].value, 0.0001)
        assertEquals(27.50238999, smoothed[3].value, 0.0001)
        assertEquals(listOf(0.0, 0.0, 100.0, 0.0), raw.map { it.value })
    }

    @Test fun `analysis builds real calendar buckets and previous year comparison`() {
        val readings = listOf(
            LegacyReadingRecord(1, 1, "2025-01-01T00:00:00", 0.0),
            LegacyReadingRecord(2, 1, "2025-02-01T00:00:00", 10.0),
            LegacyReadingRecord(3, 1, "2026-01-01T00:00:00", 100.0),
            LegacyReadingRecord(4, 1, "2026-02-01T00:00:00", 130.0),
        )
        val model = buildAnalysis(meter, readings, AnalysisPeriod.YEAR, java.time.LocalDateTime.of(2026, 2, 1, 0, 0))
        assertTrue(model.points.isNotEmpty())
        assertEquals(30.0, model.consumption, 0.0001)
        assertEquals(10.0, model.previousYearConsumption ?: Double.NaN, 0.0001)
    }
}
