package de.mohcomputer.notecas.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction

class EcasDatabaseHelper(context: Context, name: String = DATABASE_NAME) :
    SQLiteOpenHelper(context, name, null, VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(METER_SCHEMA)
        db.execSQL(READING_SCHEMA)
        db.execSQL("CREATE INDEX IF NOT EXISTS meteridx ON reading(meterid)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException("Internal database migration $oldVersion->$newVersion is not available; use the validated legacy importer")
    }

    companion object {
        const val DATABASE_NAME = "ecas"
        const val VERSION = 17
        const val METER_SCHEMA = """CREATE TABLE IF NOT EXISTS meter(
            _id INTEGER PRIMARY KEY,
            name, units, comment,
            vsf DEFAULT '50' NOT NULL,
            tsf DEFAULT '50' NOT NULL,
            cost, fcost, invert, vmsetup,
            type DEFAULT '0' NOT NULL,
            currency,
            scaling DEFAULT '1' NOT NULL,
            phyunits,
            bidir DEFAULT '0' NOT NULL,
            prod DEFAULT '0' NOT NULL
        )"""
        const val READING_SCHEMA = """CREATE TABLE IF NOT EXISTS reading(
            _id INTEGER PRIMARY KEY,
            meterid INTEGER,
            date, value, comment,
            color DEFAULT '0' NOT NULL,
            cost,
            vcor DEFAULT '0' NOT NULL,
            ccor DEFAULT '0' NOT NULL,
            first DEFAULT '0' NOT NULL,
            UNIQUE(meterid, date),
            FOREIGN KEY(meterid) REFERENCES meter(_id) ON DELETE CASCADE
        )"""
    }
}

class EcasRepository(private val helper: EcasDatabaseHelper) {
    fun upsertMeter(meter: LegacyMeterRecord) {
        val db = helper.writableDatabase
        val inserted = db.insertWithOnConflict("meter", null, meter.values(), SQLiteDatabase.CONFLICT_IGNORE)
        if (inserted == -1L) {
            check(db.update("meter", meter.values(), "_id=?", arrayOf(meter.id.toString())) == 1) {
                "Could not update meter ${meter.id}"
            }
        }
    }

    fun upsertReading(reading: LegacyReadingRecord) {
        helper.writableDatabase.insertWithOnConflict("reading", null, reading.values(), SQLiteDatabase.CONFLICT_REPLACE)
            .also { check(it != -1L) { "Could not store reading ${reading.id}" } }
    }

    fun meter(id: Long): LegacyMeterRecord? = helper.readableDatabase.query(
        "meter", null, "_id=?", arrayOf(id.toString()), null, null, null,
    ).use { if (it.moveToFirst()) it.meter() else null }

    fun meters(): List<LegacyMeterRecord> = helper.readableDatabase.query(
        "meter", null, null, null, null, null, "name COLLATE NOCASE, _id",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.meter()) } }

    fun readings(meterId: Long): List<LegacyReadingRecord> = helper.readableDatabase.query(
        "reading", null, "meterid=?", arrayOf(meterId.toString()), null, null, "date, _id",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.reading()) } }

    fun deleteMeter(id: Long): Boolean = helper.writableDatabase.delete("meter", "_id=?", arrayOf(id.toString())) > 0
    fun deleteReading(id: Long): Boolean = helper.writableDatabase.delete("reading", "_id=?", arrayOf(id.toString())) > 0

    fun replaceAll(dataset: LegacyDataset) {
        require(LegacyImportValidator.validate(dataset).canImport) { "Dataset validation failed" }
        helper.writableDatabase.transaction {
            delete("reading", null, null)
            delete("meter", null, null)
            dataset.meters.forEach { check(insertOrThrow("meter", null, it.values()) != -1L) }
            dataset.readings.forEach { check(insertOrThrow("reading", null, it.values()) != -1L) }
        }
    }

    fun checkpointAndClose() {
        helper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        helper.close()
    }
}

private fun LegacyMeterRecord.values() = ContentValues().apply {
    put("_id", id); put("name", name); put("units", units); put("comment", comment)
    put("vsf", vsf); put("tsf", tsf); put("cost", cost); put("fcost", fixedCost)
    put("invert", if (invert) 1 else 0); put("vmsetup", vmsetup); put("type", type)
    put("currency", currency); put("scaling", scaling); put("phyunits", physicalUnits)
    put("bidir", if (bidirectional) 1 else 0); put("prod", if (production) 1 else 0)
}

private fun LegacyReadingRecord.values() = ContentValues().apply {
    put("_id", id); put("meterid", meterId); put("date", date); put("value", value); put("comment", comment)
    put("color", color); if (cost == null) putNull("cost") else put("cost", cost)
    put("vcor", valueCorrection); put("ccor", costCorrection); put("first", first)
}

internal fun Cursor.meter() = LegacyMeterRecord(
    id = long("_id"), name = text("name"), units = text("units"), comment = nullableText("comment"),
    vsf = int("vsf"), tsf = int("tsf"), cost = doubleOr("cost", 0.0), fixedCost = doubleOr("fcost", 0.0),
    invert = intOr("invert", 0) != 0, vmsetup = nullableText("vmsetup"), type = intOr("type", 0),
    currency = nullableText("currency"), scaling = doubleOr("scaling", 1.0), physicalUnits = nullableText("phyunits"),
    bidirectional = intOr("bidir", 0) != 0, production = intOr("prod", 0) != 0,
)

internal fun Cursor.reading() = LegacyReadingRecord(
    id = long("_id"), meterId = long("meterid"), date = text("date"), value = getDouble(getColumnIndexOrThrow("value")),
    comment = nullableText("comment"), color = intOr("color", 0), cost = nullableDouble("cost"),
    valueCorrection = doubleOr("vcor", 0.0), costCorrection = doubleOr("ccor", 0.0), first = intOr("first", 0),
)

private fun Cursor.index(name: String) = getColumnIndex(name)
private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
private fun Cursor.nullableText(name: String): String? = index(name).let { if (it < 0 || isNull(it)) null else getString(it) }
private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
private fun Cursor.intOr(name: String, default: Int) = index(name).let { if (it < 0 || isNull(it)) default else getInt(it) }
private fun Cursor.doubleOr(name: String, default: Double) = index(name).let { if (it < 0 || isNull(it)) default else getDouble(it) }
private fun Cursor.nullableDouble(name: String): Double? = index(name).let { if (it < 0 || isNull(it)) null else getDouble(it) }
