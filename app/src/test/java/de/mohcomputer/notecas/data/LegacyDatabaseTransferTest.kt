package de.mohcomputer.notecas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyDatabaseTransferTest {
    private lateinit var context: Context
    private lateinit var helper: EcasDatabaseHelper
    private lateinit var repository: EcasRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("target.db")
        helper = EcasDatabaseHelper(context, "target.db")
        repository = EcasRepository(helper)
        repository.upsertMeter(meter(1, "Current"))
    }

    @After fun tearDown() {
        helper.close()
        context.deleteDatabase("target.db")
    }

    @Test
    fun `imports v17 database through temporary validated copy preserving ids`() {
        val source = createV17Source()

        val report = source.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertTrue(report.success)
        assertEquals(1, report.metersImported)
        assertEquals(1, report.readingsImported)
        assertEquals(8L, repository.meters().single().id)
        assertEquals(11L, repository.readings(8).single().id)
    }

    @Test
    fun `import report counts records replacing existing ids and meter dates`() {
        repository.upsertReading(LegacyReadingRecord(3, 1, "2026-01-01T00:00:00", 1.0))
        val file = File(context.cacheDir, "replacement-v17.db").also { it.delete() }
        EcasDatabaseHelper(context, file.path).use { sourceHelper ->
            val sourceRepository = EcasRepository(sourceHelper)
            sourceRepository.upsertMeter(meter(1, "Replacement"))
            sourceRepository.upsertReading(LegacyReadingRecord(9, 1, "2026-01-01T00:00:00", 2.0))
        }

        val report = file.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertTrue(report.success)
        assertEquals(2, report.replaced)
    }

    @Test
    fun `bad magic is rejected without replacing current database`() {
        val report = LegacyDatabaseImporter(context).import("not sqlite".byteInputStream(), repository)

        assertFalse(report.success)
        assertTrue(report.issues.any { it.code == ImportIssueCode.INVALID_SQLITE_FILE })
        assertEquals("Current", repository.meters().single().name)
    }

    @Test
    fun `legacy version missing optional columns receives defaults and warning`() {
        val file = File(context.cacheDir, "legacy-v1.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE meter(_id INTEGER PRIMARY KEY, name, units)")
            db.execSQL("CREATE TABLE reading(_id INTEGER PRIMARY KEY, meterid INTEGER, date, value)")
            db.execSQL("INSERT INTO meter VALUES(2,'Old','m3')")
            db.version = 1
        }

        val report = file.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertTrue(report.success)
        assertEquals(1.0, repository.meters().single().scaling, 0.0)
        assertTrue(report.issues.any { it.code == ImportIssueCode.LEGACY_VERSION_DEFAULTS })
    }

    @Test
    fun `missing required schema and newer version are reported safely`() {
        val file = File(context.cacheDir, "bad-schema.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE meter(_id INTEGER PRIMARY KEY, name)")
            db.version = 18
        }

        val report = file.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertFalse(report.success)
        assertTrue(report.issues.any { it.code == ImportIssueCode.UNSUPPORTED_VERSION })
        assertEquals("Current", repository.meters().single().name)
    }

    @Test
    fun `invalid legacy numeric text is reported instead of coerced by SQLite`() {
        val file = createV17Source()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE reading SET value='NaN'")
        }

        val report = file.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertFalse(report.success)
        assertTrue(report.issues.any { it.code == ImportIssueCode.INVALID_NUMBER })
        assertEquals("Current", repository.meters().single().name)
    }

    @Test
    fun `invalid legacy numeric flags are reported before typed conversion`() {
        val file = createV17Source()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("UPDATE meter SET invert=2")
        }

        val report = file.inputStream().use { LegacyDatabaseImporter(context).import(it, repository) }

        assertFalse(report.success)
        assertTrue(report.issues.any { it.code == ImportIssueCode.INVALID_FLAG })
        assertEquals("Current", repository.meters().single().name)
    }

    @Test
    fun `exports checkpointed v17 database to output stream`() {
        val output = ByteArrayOutputStream()

        LegacyDatabaseExporter(context, "target.db", repository).export(output)

        val exported = File(context.cacheDir, "export-check.db").also { it.writeBytes(output.toByteArray()) }
        SQLiteDatabase.openDatabase(exported.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(17, db.version)
            assertEquals("ok", db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst(); it.getString(0) })
            assertEquals(1, db.rawQuery("SELECT count(*) FROM meter", null).use { it.moveToFirst(); it.getInt(0) })
        }
    }

    private fun createV17Source(): File {
        val file = File(context.cacheDir, "source-v17.db").also { it.delete() }
        EcasDatabaseHelper(context, file.path).use { sourceHelper ->
            val repo = EcasRepository(sourceHelper)
            repo.upsertMeter(meter(8, "Imported"))
            repo.upsertReading(LegacyReadingRecord(11, 8, "2026-01-01T00:00:00", 4.5))
        }
        return file
    }

    private fun meter(id: Long, name: String) = LegacyMeterRecord(
        id, name, "kWh", null, 50, 50, 0.0, 0.0, false, null, 0, "EUR", 1.0, "kWh", false, false,
    )
}
