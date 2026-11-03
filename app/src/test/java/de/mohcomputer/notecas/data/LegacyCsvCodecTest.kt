package de.mohcomputer.notecas.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCsvCodecTest {
    @Test
    fun `encodes UTF-8 CSV with CRLF quotes no header and double precision`() {
        val rows = listOf(CsvReading(LocalDateTime.of(2026, 3, 4, 5, 6, 7), 1.23456789012345, "Größe, \"Nord\""))

        val bytes = LegacyCsvCodec.encode(rows)

        assertEquals("2026-03-04,05:06:07,1.23456789012345,\"Größe, \"\"Nord\"\"\"\r\n", bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun `legacy float mode rounds through Float32`() {
        val encoded = LegacyCsvCodec.encode(
            listOf(CsvReading(LocalDateTime.of(2026, 1, 1, 0, 0), 1.23456789012345, "")),
            LegacyCsvPrecision.ECAS_FLOAT32,
        ).toString(Charsets.UTF_8)

        assertEquals(1.2345679f.toString(), encoded.split(',')[2])
    }

    @Test
    fun `decodes quoted multiline comments and ignores extra columns`() {
        val input = "2026-01-02,03:04:05,12.5,\"line one\r\nline \"\"two\"\"\",ignored\r\n"

        val result = LegacyCsvCodec.decode(input.byteInputStream())

        assertEquals(1, result.report.read)
        assertEquals(1, result.report.imported)
        assertEquals("line one\r\nline \"two\"", result.rows.single().comment)
    }

    @Test
    fun `duplicate timestamp replaces earlier row and is reported`() {
        val input = "2026-01-02,03:04:05,1,old\r\n2026-01-02,03:04:05,2,new\r\n"

        val result = LegacyCsvCodec.decode(input.byteInputStream())

        assertEquals(2, result.report.read)
        assertEquals(1, result.report.imported)
        assertEquals(1, result.report.replaced)
        assertEquals(0, result.report.discarded)
        assertEquals(2.0, result.rows.single().value, 0.0)
    }

    @Test
    fun `malformed timestamp and nonfinite value are discarded with reasons`() {
        val input = "wrong,03:04:05,1,x\r\n2026-01-02,03:04:05,NaN,x\r\n"

        val result = LegacyCsvCodec.decode(input.byteInputStream())

        assertEquals(2, result.report.read)
        assertEquals(2, result.report.discarded)
        assertTrue(result.report.issues.any { it.contains("timestamp") })
        assertTrue(result.report.issues.any { it.contains("finite") })
    }
}
