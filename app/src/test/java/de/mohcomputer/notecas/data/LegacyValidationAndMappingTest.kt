package de.mohcomputer.notecas.data

import de.mohcomputer.notecas.domain.MeterFlags
import de.mohcomputer.notecas.domain.MeterType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyValidationAndMappingTest {
    private val meter = LegacyMeterRecord(
        id = 7, name = "Solar", units = "kWh", comment = "roof", vsf = 60, tsf = 40,
        cost = 0.3, fixedCost = 12.0, invert = false, vmsetup = null, type = 0,
        currency = "EUR", scaling = 2.0, physicalUnits = "turns", bidirectional = true, production = true,
    )

    @Test
    fun `legacy records map to and from existing domain models`() {
        val domain = meter.toDomain()

        assertEquals(MeterType.CUMULATIVE, domain.type)
        assertEquals(MeterFlags(bidirectional = true, production = true), domain.flags)
        assertEquals(meter.id, LegacyMapper.fromDomain(domain).id)
        assertEquals(meter.scaling, LegacyMapper.fromDomain(domain).scaling, 0.0)
    }

    @Test
    fun `validator reports orphans invalid numbers timestamps flags and virtual references`() {
        val dataset = LegacyDataset(
            version = 17,
            meters = listOf(meter.copy(vmsetup = "99|1", scaling = Double.NaN, invert = true)),
            readings = listOf(
                LegacyReadingRecord(1, 99, "2026-01-01T00:00:00", 1.0),
                LegacyReadingRecord(2, 7, "bad", Double.POSITIVE_INFINITY, color = 9, first = 2),
            ),
        )

        val validation = LegacyImportValidator.validate(dataset)

        assertFalse(validation.canImport)
        assertTrue(validation.issues.any { it.code == ImportIssueCode.ORPHAN_READING })
        assertTrue(validation.issues.any { it.code == ImportIssueCode.INVALID_NUMBER })
        assertTrue(validation.issues.any { it.code == ImportIssueCode.INVALID_TIMESTAMP })
        assertTrue(validation.issues.any { it.code == ImportIssueCode.INVALID_FLAG })
        assertTrue(validation.issues.any { it.code == ImportIssueCode.INVALID_VM_REFERENCE })
    }

    @Test
    fun `valid v17 dataset passes and preserves reading metadata mapping`() {
        val reading = LegacyReadingRecord(
            4, 7, "2026-01-02T03:04:05", 42.25, "note", color = 2,
            cost = 5.5, valueCorrection = 0.2, costCorrection = 0.1, first = 1,
        )
        val validation = LegacyImportValidator.validate(LegacyDataset(17, listOf(meter), listOf(reading)))

        assertTrue(validation.canImport)
        val domain = reading.toDomain()
        assertEquals(LocalDateTime.of(2026, 1, 2, 3, 4, 5), domain.timestamp)
        assertTrue(domain.isFirst)
        assertEquals(reading.value, LegacyMapper.fromDomain(domain).value, 0.0)
    }

    @Test
    fun `versions one through sixteen are accepted with explicit compatibility warning`() {
        for (version in 1..16) {
            val validation = LegacyImportValidator.validate(LegacyDataset(version, listOf(meter), emptyList()))
            assertTrue("version $version", validation.canImport)
            assertTrue(validation.issues.any { it.code == ImportIssueCode.LEGACY_VERSION_DEFAULTS })
        }
    }

    @Test
    fun `version zero and newer versions are rejected`() {
        assertFalse(LegacyImportValidator.validate(LegacyDataset(0, emptyList(), emptyList())).canImport)
        assertFalse(LegacyImportValidator.validate(LegacyDataset(18, emptyList(), emptyList())).canImport)
    }
}
