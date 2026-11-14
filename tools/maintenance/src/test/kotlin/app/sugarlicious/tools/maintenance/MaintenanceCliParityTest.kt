package app.sugarlicious.tools.maintenance

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.*

class MaintenanceCliParityTest {
    private val required =
        mapOf(
            "configure-apex-progress" to listOf("--resources", "res"),
            "configure-indicator-gradient" to listOf("--resources", "res", "--face", "apex"),
            "configure-trend-icon-geometry" to emptyList(),
            "curated-palettes" to listOf("--resources", "res"),
            "generate-orbit" to listOf("--output", "out"),
            "prepare-slot-capture" to listOf("--source", "src", "--destination", "dst", "--name", "orbit"),
            "restore-native-hands" to listOf("--resources", "res", "--face", "orbit"),
            "segmented-progress" to listOf("--resources", "res", "--face", "apex"),
            "unify-apex-progress-settings" to listOf("--resources", "res"),
            "user-flavors" to listOf("--resources", "res", "--face", "apex"),
            "import-app-brand-icons" to listOf("--source", "master.svg"),
            "import-adaptive-brand-icons" to emptyList(),
            "refresh-brand-presentations" to emptyList(),
            "import-orbit-handset" to emptyList(),
            "configure-orbit-seconds-motion" to emptyList(),
            "import-orbit-weather" to listOf("--samsung-apk", "s.apk", "--google-reference-apk", "g.apk", "--samsung-resource-table", "table.txt"),
            "render-orbit-hands" to emptyList(),
            "configure-vigil-decoration" to emptyList(),
            "import-vigil-markers" to emptyList(),
            "migrate-products" to listOf("--watch", "serial", "--product", "sugarwear", "--legacy-dir", "legacy"),
            "verify-codefree-vigil" to emptyList(),
        )

    @Test fun helpIsAvailableForEveryCommandWithoutTouchingFilesOrDevices() {
        assertEquals(commands.toSet(), required.keys)
        synchronized(System::class.java) {
            val original = System.out
            val output = ByteArrayOutputStream()
            try {
                System.setOut(PrintStream(output))
                commands.forEach { command ->
                    listOf("--help", "-h").forEach { flag ->
                        output.reset()
                        assertEquals(0, runMaintenance(listOf(command, flag)), command)
                        assertTrue(output.toString().contains("Usage: $command"), command)
                    }
                }
            } finally {
                System.setOut(original)
            }
        }
    }

    @Test fun allOriginalRequiredArgumentsAndDefaultsRemainAvailable() {
        required.forEach { (command, arguments) ->
            assertEquals(arguments, normalizeCommandArguments(command, arguments), command)
            if (arguments.isNotEmpty()) {
                arguments.chunked(2).indices.forEach { omitted ->
                    assertFailsWith<IllegalArgumentException>(command) {
                        normalizeCommandArguments(command, arguments.chunked(2).filterIndexed { index, _ -> index != omitted }.flatten())
                    }
                }
            }
            assertFailsWith<IllegalArgumentException>(command) { normalizeCommandArguments(command, arguments + listOf("--unknown", "value")) }
        }
    }

    @Test fun argparseEqualsSpellingPrefixesAndRepeatedOptionsUseLastValue() {
        required.filterValues { it.isNotEmpty() }.forEach { (command, arguments) ->
            val pairs = arguments.chunked(2)
            assertEquals(arguments, normalizeCommandArguments(command, pairs.map { (key, value) -> "$key=$value" }), command)
            val key = pairs.first()[0]
            val normalized = normalizeCommandArguments(command, listOf(key, "discarded") + arguments)
            assertEquals(arguments, normalized, command)
            assertFailsWith<IllegalArgumentException>(command) { normalizeCommandArguments(command, arguments + key) }
        }
        assertEquals(listOf("--resources", "res"), normalizeCommandArguments("configure-apex-progress", listOf("--res=res")))
        assertEquals(listOf("--source", "--a-file-starting-with-dashes.svg"), normalizeCommandArguments("import-app-brand-icons", listOf("--source=--a-file-starting-with-dashes.svg")))
        assertFailsWith<IllegalArgumentException> { normalizeCommandArguments("import-orbit-weather", listOf("--s", "ambiguous")) }
        assertFailsWith<IllegalArgumentException> { normalizeCommandArguments("configure-apex-progress", listOf("--resources", "--unknown")) }
        assertFailsWith<IllegalArgumentException> { normalizeCommandArguments("user-flavors", listOf("--resources", "res", "--face", "invalid", "--face", "apex")) }
    }

    @Test fun invalidChoicesFailBeforeResourceReadsOrDeviceActions() {
        listOf("user-flavors", "configure-indicator-gradient", "restore-native-hands", "segmented-progress").forEach { command ->
            assertFailsWith<IllegalArgumentException> { runMaintenance(listOf(command, "--resources", "missing", "--face", "invalid")) }
        }
        assertFailsWith<IllegalArgumentException> { runMaintenance(listOf("prepare-slot-capture", "--source", "missing", "--destination", "missing", "--name", "invalid")) }
        listOf("import-app-brand-icons", "import-adaptive-brand-icons").forEach { command ->
            assertFailsWith<IllegalArgumentException> { runMaintenance(listOf(command) + (if (command == "import-app-brand-icons") listOf("--source", "missing") else emptyList()) + listOf("--product", "invalid")) }
        }
        assertFailsWith<IllegalArgumentException> { runMaintenance(listOf("migrate-products", "--watch", "serial", "--product", "invalid", "--legacy-dir", "missing")) }
    }

    @Test fun argumentsFileUsesTheSameHelpAndValidationPath() {
        val path = Files.createTempFile("maintenance-arguments", ".json")
        try {
            Files.writeString(path, "[\"generate-orbit\",\"--help\"]")
            assertEquals(0, runMaintenance(listOf("--arguments-file", path.toString())))
            Files.writeString(path, "[\"generate-orbit\",\"--output\"]")
            assertFailsWith<IllegalArgumentException> { runMaintenance(listOf("--arguments-file", path.toString())) }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
