package de.mohcomputer.notecas.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EcasRepositoryTest {
    private lateinit var context: Context
    private lateinit var helper: EcasDatabaseHelper
    private lateinit var repository: EcasRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("repo-test.db")
        helper = EcasDatabaseHelper(context, "repo-test.db")
        repository = EcasRepository(helper)
    }

    @After fun tearDown() {
        helper.close()
        context.deleteDatabase("repo-test.db")
    }

    @Test
    fun `database is schema version 17 with legacy columns`() {
        val db = helper.writableDatabase
        assertEquals(17, db.version)
        val meterColumns = db.rawQuery("PRAGMA table_info(meter)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        assertTrue(meterColumns.containsAll(listOf("_id", "name", "units", "vmsetup", "prod")))
    }

    @Test
    fun `CRUD sorts meters and readings and duplicate date replaces`() {
        repository.upsertMeter(sampleMeter(2, "Zulu"))
        repository.upsertMeter(sampleMeter(1, "Alpha"))
        repository.upsertReading(sampleReading(5, 1, "2026-01-02T00:00:00", 2.0))
        repository.upsertReading(sampleReading(6, 1, "2026-01-01T00:00:00", 1.0))
        repository.upsertReading(sampleReading(7, 1, "2026-01-01T00:00:00", 9.0))

        assertEquals(listOf("Alpha", "Zulu"), repository.meters().map { it.name })
        val readings = repository.readings(1)
        assertEquals(listOf("2026-01-01T00:00:00", "2026-01-02T00:00:00"), readings.map { it.date })
        assertEquals(7L, readings.first().id)
        assertEquals(9.0, readings.first().value, 0.0)
    }

    @Test
    fun `updating meter does not delete its readings`() {
        repository.upsertMeter(sampleMeter(1, "Before"))
        repository.upsertReading(sampleReading(1, 1, "2026-01-01T00:00:00", 1.0))

        repository.upsertMeter(sampleMeter(1, "After"))

        assertEquals("After", repository.meter(1)?.name)
        assertEquals(1, repository.readings(1).size)
    }

    @Test
    fun `deleting meter cascades to readings`() {
        repository.upsertMeter(sampleMeter(1, "A"))
        repository.upsertReading(sampleReading(1, 1, "2026-01-01T00:00:00", 1.0))

        repository.deleteMeter(1)

        assertNull(repository.meter(1))
        assertTrue(repository.readings(1).isEmpty())
    }

    @Test
    fun `replaceAll preserves ids and rolls back invalid dataset atomically`() {
        repository.upsertMeter(sampleMeter(1, "Existing"))
        val valid = LegacyDataset(17, listOf(sampleMeter(8, "Imported")), listOf(sampleReading(11, 8, "2026-01-01T00:00:00", 3.0)))
        repository.replaceAll(valid)
        assertEquals(8L, repository.meters().single().id)
        assertEquals(11L, repository.readings(8).single().id)

        try {
            repository.replaceAll(LegacyDataset(17, emptyList(), listOf(sampleReading(12, 99, "2026-01-01T00:00:00", 1.0))))
        } catch (_: RuntimeException) { }
        assertEquals(8L, repository.meters().single().id)
    }

    private fun sampleMeter(id: Long, name: String) = LegacyMeterRecord(
        id, name, "kWh", null, 50, 50, 0.0, 0.0, false, null, 0, "EUR", 1.0, "kWh", false, false,
    )

    private fun sampleReading(id: Long, meterId: Long, date: String, value: Double) =
        LegacyReadingRecord(id, meterId, date, value)
}
