package app.sugarlicious.tools.cwf

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TherapyBridgeCwfParserTest {
    @Test
    fun `parses and normalizes a valid custom watchface`() =
        withZip(
            mapOf("CustomWatchface.json" to VALID_JSON.encodeToByteArray(), "CustomWatchface.png" to byteArrayOf(1, 2, 3)),
        ) { zip ->
            val result = WatchfacePackageParser().parse(zip)
            assertEquals("Test face", result.metadata.name)
            assertEquals(400, result.canvasWidth)
            assertEquals(2, result.elements.size)
            assertTrue("chart" in result.featureFlags)
            assertTrue("dynamic_preferences" in result.featureFlags)
        }

    @Test
    fun `rejects zip traversal before reading entries`() =
        withZip(
            mapOf("../CustomWatchface.json" to VALID_JSON.encodeToByteArray()),
        ) { zip ->
            assertFailsWith<CwfValidationException> { WatchfacePackageParser().parse(zip) }
        }

    @Test
    fun `rejects wrong numeric field type`() =
        withZip(
            mapOf(
                "CustomWatchface.json" to VALID_JSON.replace("\"width\":400", "\"width\":\"wide\"").encodeToByteArray(),
                "CustomWatchface.png" to byteArrayOf(1),
            ),
        ) { zip ->
            assertFailsWith<CwfValidationException> { WatchfacePackageParser().parse(zip) }
        }

    @Test
    fun `rejects entries above configured size limit`() =
        withZip(
            mapOf("CustomWatchface.json" to VALID_JSON.encodeToByteArray(), "CustomWatchface.png" to ByteArray(128)),
        ) { zip ->
            assertFailsWith<CwfValidationException> { WatchfacePackageParser(maxEntryBytes = 64).parse(zip) }
        }

    private fun withZip(
        entries: Map<String, ByteArray>,
        block: (Path) -> Unit,
    ) {
        val path = Files.createTempFile("cwf-test-", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(path)).use { output ->
                entries.forEach { (name, bytes) ->
                    output.putNextEntry(ZipEntry(name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
            block(path)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    companion object {
        private val VALID_JSON =
            """
            {
              "metadata":{"name":"Test face","author":"Tester","cwf_version":"1.0"},
              "background":{"width":400,"height":400,"topmargin":0,"leftmargin":0,"visibility":"visible"},
              "chart":{"width":400,"height":160,"topmargin":240,"leftmargin":0,"visibility":"visible"},
              "dynPref":{"dark":{"prefKey":"dark"}}
            }
            """.trimIndent()
    }
}
