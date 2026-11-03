package de.mohcomputer.notecas.data

import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CSV_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val CSV_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

data class CsvReading(val timestamp: LocalDateTime, val value: Double, val comment: String = "")
enum class LegacyCsvPrecision { DOUBLE, ECAS_FLOAT32 }
data class CsvImportReport(val read: Int, val imported: Int, val replaced: Int, val discarded: Int, val issues: List<String>)
data class CsvDecodeResult(val rows: List<CsvReading>, val report: CsvImportReport)

object LegacyCsvCodec {
    fun encode(rows: List<CsvReading>, precision: LegacyCsvPrecision = LegacyCsvPrecision.DOUBLE): ByteArray {
        val text = buildString {
            rows.forEach { row ->
                val value = when (precision) {
                    LegacyCsvPrecision.DOUBLE -> row.value.toString()
                    LegacyCsvPrecision.ECAS_FLOAT32 -> row.value.toFloat().toString()
                }
                append(csv(row.timestamp.toLocalDate().format(CSV_DATE)))
                append(',').append(csv(row.timestamp.toLocalTime().format(CSV_TIME)))
                append(',').append(csv(value))
                append(',').append(csv(row.comment)).append("\r\n")
            }
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    fun decode(input: InputStream): CsvDecodeResult {
        val records = parseRecords(input.readBytes().toString(Charsets.UTF_8))
        val rows = linkedMapOf<LocalDateTime, CsvReading>()
        val issues = mutableListOf<String>()
        var replaced = 0
        var discarded = 0
        records.forEachIndexed { index, fields ->
            val line = index + 1
            if (fields.size < 3) {
                discarded++
                issues += "Record $line: expected date,time,value"
                return@forEachIndexed
            }
            val timestamp = try {
                LocalDateTime.of(LocalDate.parse(fields[0], CSV_DATE), LocalTime.parse(fields[1], CSV_TIME))
            } catch (_: Exception) {
                discarded++
                issues += "Record $line: invalid timestamp"
                return@forEachIndexed
            }
            val value = fields[2].toDoubleOrNull()
            if (value == null || !value.isFinite()) {
                discarded++
                issues += "Record $line: value must be finite"
                return@forEachIndexed
            }
            if (timestamp in rows) replaced++
            rows[timestamp] = CsvReading(timestamp, value, fields.getOrElse(3) { "" })
        }
        return CsvDecodeResult(
            rows.values.toList(),
            CsvImportReport(records.size, rows.size, replaced, discarded, issues),
        )
    }

    private fun csv(value: String): String = if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value

    private fun parseRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun finishRecord() {
            fields += field.toString(); field.setLength(0)
            if (fields.any { it.isNotEmpty() }) records += fields
            fields = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { fields += field.toString(); field.setLength(0) }
                !quoted && (ch == '\r' || ch == '\n') -> {
                    finishRecord()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) finishRecord()
        return records
    }
}
