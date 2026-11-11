package de.mohcomputer.notecas

import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupConfigurationTest {
    @Test
    @Config(sdk = [31, 35])
    fun `backup configuration permits database transfer but not cloud backup`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        val rules = parseRules(context.resources.getXml(R.xml.data_extraction_rules))

        val allDomains = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
        assertEquals(
            allDomains,
            rules.filter { it.section == "cloud-backup" && it.operation == "exclude" }
                .mapTo(mutableSetOf()) { it.domain },
        )
        assertFalse(rules.any { it.section == "cloud-backup" && it.operation == "include" })
        assertEquals(
            listOf(Rule("device-transfer", "include", "database", ".")),
            rules.filter { it.section == "device-transfer" },
        )
    }

    @Test
    @Config(sdk = [26, 27])
    fun `Android 8 fallback excludes every backup domain`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = parseRules(context.resources.getXml(R.xml.backup_rules))

        assertFalse(rules.any { it.operation == "include" })
        assertEquals(
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref",
            ),
            rules.mapTo(mutableSetOf()) { it.domain },
        )
    }

    @Test
    @Config(sdk = [28, 29, 30])
    fun `Android 9 to 11 permits only database device transfer`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rules = parseRules(context.resources.getXml(R.xml.backup_rules))

        assertEquals(
            listOf(Rule("full-backup-content", "include", "database", ".", "deviceToDeviceTransfer")),
            rules,
        )
    }

    private fun parseRules(parser: XmlPullParser): List<Rule> {
        val rules = mutableListOf<Rule>()
        var section: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "cloud-backup", "device-transfer", "full-backup-content" -> section = parser.name
                    "include", "exclude" -> section?.let {
                        rules += Rule(
                            section = it,
                            operation = parser.name,
                            domain = parser.getAttributeValue(null, "domain"),
                            path = parser.getAttributeValue(null, "path"),
                            requireFlags = parser.getAttributeValue(null, "requireFlags"),
                        )
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == section) {
                section = null
            }
            event = parser.next()
        }
        return rules
    }

    private data class Rule(
        val section: String,
        val operation: String,
        val domain: String,
        val path: String,
        val requireFlags: String? = null,
    )
}
