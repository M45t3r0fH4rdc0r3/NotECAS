package de.mohcomputer.notecas.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
private val REQUIRED_METER_COLUMNS = setOf("_id", "name", "units")
private val REQUIRED_READING_COLUMNS = setOf("_id", "meterid", "date", "value")
private val V17_METER_COLUMNS = setOf(
    "_id", "name", "units", "comment", "vsf", "tsf", "cost", "fcost", "invert", "vmsetup",
    "type", "currency", "scaling", "phyunits", "bidir", "prod",
)
private val V17_READING_COLUMNS = setOf(
    "_id", "meterid", "date", "value", "comment", "color", "cost", "vcor", "ccor", "first",
)

data class DatabaseImportReport(
    val success: Boolean,
    val sourceVersion: Int?,
    val metersRead: Int,
    val metersImported: Int,
    val readingsRead: Int,
    val readingsImported: Int,
    val replaced: Int,
    val discarded: Int,
    val issues: List<ImportIssue>,
)

class LegacyDatabaseImporter(private val context: Context) {
    fun import(uri: Uri, repository: EcasRepository): DatabaseImportReport {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return failure(ImportIssueCode.INVALID_SQLITE_FILE, "ContentResolver returned no stream for $uri")
        return stream.use { import(it, repository) }
    }

    fun import(input: InputStream, repository: EcasRepository): DatabaseImportReport {
        val temporary = File.createTempFile("notecas-import-", ".db", context.cacheDir)
        return try {
            input.use { source -> temporary.outputStream().use(source::copyTo) }
            if (!hasSqliteMagic(temporary)) return failure(ImportIssueCode.INVALID_SQLITE_FILE, "Selected file is not a SQLite 3 database")
            val extraction = extract(temporary)
            if (extraction.dataset == null) {
                DatabaseImportReport(false, extraction.version, 0, 0, 0, 0, 0, 0, extraction.issues)
            } else {
                val validation = LegacyImportValidator.validate(extraction.dataset)
                val issues = extraction.issues + validation.issues
                if (!validation.canImport || issues.any { it.fatal }) {
                    DatabaseImportReport(
                        false, extraction.dataset.version, extraction.dataset.meters.size, 0,
                        extraction.dataset.readings.size, 0, 0, extraction.dataset.meters.size + extraction.dataset.readings.size, issues,
                    )
                } else {
                    val currentMeters = repository.meters()
                    val currentMeterIds = currentMeters.map { it.id }.toSet()
                    val currentReadingKeys = currentMeters.flatMap { meter ->
                        repository.readings(meter.id).map { it.meterId to it.date }
                    }.toSet()
                    val replaced = extraction.dataset.meters.count { it.id in currentMeterIds } +
                        extraction.dataset.readings.count { (it.meterId to it.date) in currentReadingKeys }
                    repository.replaceAll(extraction.dataset)
                    DatabaseImportReport(
                        true, extraction.dataset.version, extraction.dataset.meters.size, extraction.dataset.meters.size,
                        extraction.dataset.readings.size, extraction.dataset.readings.size, replaced, 0, issues,
                    )
                }
            }
        } catch (e: Exception) {
            failure(ImportIssueCode.INTEGRITY_ERROR, e.message ?: e.javaClass.simpleName)
        } finally {
            temporary.delete()
        }
    }

    private fun extract(file: File): Extraction {
        var version: Int? = null
        val issues = mutableListOf<ImportIssue>()
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            val quickCheck = db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "no result"
            }
            if (quickCheck != "ok") {
                return Extraction(null, version, listOf(ImportIssue(ImportIssueCode.INTEGRITY_ERROR, "PRAGMA quick_check: $quickCheck", true)))
            }
            version = db.version
            if (version !in 1..17) {
                return Extraction(null, version, listOf(ImportIssue(ImportIssueCode.UNSUPPORTED_VERSION, "Unsupported user_version $version; expected 1..17", true)))
            }
            val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            if (!tables.containsAll(setOf("meter", "reading"))) {
                return Extraction(null, version, listOf(ImportIssue(ImportIssueCode.SCHEMA_ERROR, "Required meter/reading tables are missing", true)))
            }
            val meterColumns = columns(db, "meter")
            val readingColumns = columns(db, "reading")
            val expectedMeter = if (version == 17) V17_METER_COLUMNS else REQUIRED_METER_COLUMNS
            val expectedReading = if (version == 17) V17_READING_COLUMNS else REQUIRED_READING_COLUMNS
            val missing = (expectedMeter - meterColumns).map { "meter.$it" } + (expectedReading - readingColumns).map { "reading.$it" }
            if (missing.isNotEmpty()) {
                return Extraction(null, version, listOf(ImportIssue(ImportIssueCode.SCHEMA_ERROR, "Missing columns: ${missing.joinToString()}", true)))
            }
            if (version < 17) {
                issues += ImportIssue(
                    ImportIssueCode.LEGACY_VERSION_DEFAULTS,
                    "Legacy schema v$version converted to v17; absent optional columns use v17 defaults",
                    false,
                )
            }
            val meters = db.query("meter", null, null, null, null, null, "_id").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.requiredLong("_id")
                        listOf("invert", "bidir", "prod").filter { it in meterColumns }.forEach { flag ->
                            val value = cursor.intOrDefault(flag, 0)
                            if (value !in 0..1) issues += ImportIssue(
                                ImportIssueCode.INVALID_FLAG,
                                "Meter $id has invalid $flag flag $value",
                                true,
                            )
                        }
                        add(sourceMeter(cursor))
                    }
                }
            }
            val readings = db.query("reading", null, null, null, null, null, "_id").use { cursor ->
                buildList { while (cursor.moveToNext()) add(sourceReading(cursor)) }
            }
            return Extraction(LegacyDataset(version, meters, readings), version, issues)
        }
    }

    private fun failure(code: ImportIssueCode, detail: String) = DatabaseImportReport(
        false, null, 0, 0, 0, 0, 0, 0, listOf(ImportIssue(code, detail, true)),
    )

    private fun hasSqliteMagic(file: File): Boolean = file.inputStream().use { input ->
        val actual = ByteArray(SQLITE_MAGIC.size)
        input.read(actual) == actual.size && actual.contentEquals(SQLITE_MAGIC)
    }

    private data class Extraction(val dataset: LegacyDataset?, val version: Int?, val issues: List<ImportIssue>)
}

class LegacyDatabaseExporter(
    private val context: Context,
    private val databaseName: String = EcasDatabaseHelper.DATABASE_NAME,
    private val repository: EcasRepository,
) {
    fun export(uri: Uri) {
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            "ContentResolver returned no output stream for $uri"
        }
        output.use(::export)
    }

    fun export(output: OutputStream) {
        repository.checkpointAndClose()
        val database = context.getDatabasePath(databaseName)
        require(database.isFile) { "Database does not exist: $databaseName" }
        database.inputStream().use { it.copyTo(output) }
        output.flush()
    }
}

private fun columns(db: SQLiteDatabase, table: String): Set<String> = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val index = cursor.getColumnIndexOrThrow("name")
    buildSet { while (cursor.moveToNext()) add(cursor.getString(index)) }
}

private fun sourceMeter(cursor: Cursor) = LegacyMeterRecord(
    id = cursor.requiredLong("_id"),
    name = cursor.stringOr("name", ""),
    units = cursor.stringOr("units", ""),
    comment = cursor.nullableString("comment"),
    vsf = cursor.intOrDefault("vsf", 50),
    tsf = cursor.intOrDefault("tsf", 50),
    cost = cursor.doubleOrDefault("cost", 0.0),
    fixedCost = cursor.doubleOrDefault("fcost", 0.0),
    invert = cursor.intOrDefault("invert", 0) != 0,
    vmsetup = cursor.nullableString("vmsetup"),
    type = cursor.intOrDefault("type", 0),
    currency = cursor.nullableString("currency"),
    scaling = cursor.doubleOrDefault("scaling", 1.0),
    physicalUnits = cursor.nullableString("phyunits"),
    bidirectional = cursor.intOrDefault("bidir", 0) != 0,
    production = cursor.intOrDefault("prod", 0) != 0,
)

private fun sourceReading(cursor: Cursor) = LegacyReadingRecord(
    id = cursor.requiredLong("_id"),
    meterId = cursor.requiredLong("meterid"),
    date = cursor.stringOr("date", ""),
    value = cursor.doubleOrDefault("value", Double.NaN),
    comment = cursor.nullableString("comment"),
    color = cursor.intOrDefault("color", 0),
    cost = cursor.nullableDoubleValue("cost"),
    valueCorrection = cursor.doubleOrDefault("vcor", 0.0),
    costCorrection = cursor.doubleOrDefault("ccor", 0.0),
    first = cursor.intOrDefault("first", 0),
)

private fun Cursor.column(name: String) = getColumnIndex(name)
private fun Cursor.requiredLong(name: String) = getLong(getColumnIndexOrThrow(name))
private fun Cursor.stringOr(name: String, default: String): String = column(name).let { if (it < 0 || isNull(it)) default else getString(it) }
private fun Cursor.nullableString(name: String): String? = column(name).let { if (it < 0 || isNull(it)) null else getString(it) }
private fun Cursor.intOrDefault(name: String, default: Int): Int = column(name).let { if (it < 0 || isNull(it)) default else getInt(it) }
private fun Cursor.doubleOrDefault(name: String, default: Double): Double = column(name).let { if (it < 0 || isNull(it)) default else getDouble(it) }
private fun Cursor.nullableDoubleValue(name: String): Double? = column(name).let { if (it < 0 || isNull(it)) null else getDouble(it) }
