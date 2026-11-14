package app.sugarlicious.tools.maintenance.migration

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*
import kotlin.test.Test

class MigrationTest {
    private fun error(code: String, operation: () -> Unit) {
        try {
            operation()
            fail("Expected $code")
        } catch (e: MigrationError) {
            assertEquals(code, e.code)
        }
    }

    @Test fun metadataAndPaths() {
        val root = Files.createTempDirectory("migration")

        fun metadata(name: String, id: String = "app.sugarwear") =
            buildJsonObject {
                put("applicationId", id)
                putJsonArray("elements") { add(buildJsonObject { put("outputFile", name) }) }
            }
        assertEquals(root.resolve("app.apk"), validateMetadata(metadata("app.apk"), "app.sugarwear", root))
        error("apk_application_id_mismatch") { validateMetadata(metadata("app.apk"), "other", root) }
        listOf("../other.apk", "config.xml").forEach { error("apk_path_invalid") { validateMetadata(metadata(it), "app.sugarwear", root) } }
        error("sugarwear_repository_required") { artifactDirectory("sugarwear", root) }
        assertEquals(root.resolve("app-sugarwear/build/outputs/apk/debug"), artifactDirectory("sugarwear", Path.of("ignored"), root))
    }

    @Test fun plansAndAllowlist() {
        assertEquals(listOf("sugarwear" to "watch"), migrationPlan("sugarwear", null, "watch"))
        assertEquals(3, migrationPlan("both", "phone", "watch").size)
        error("unknown_product") { migrationPlan("unknown", "p", "w") }
        error("explicit_serial_required") { migrationPlan("both", null, "w") }
        error("phone_watch_serial_collision") { migrationPlan("both", "same", "same") }
        assertEquals(setOf("android.permission.BLUETOOTH_SCAN", "com.dexcom.cgm.EXTERNAL_PERMISSION"), extractGrants("android.permission.BLUETOOTH_SCAN: granted=true\nandroid.permission.CAMERA: granted=true\nandroid.permission.BLUETOOTH_CONNECT: granted=false\ncom.dexcom.cgm.EXTERNAL_PERMISSION: granted=true"))
        assertTrue(packageAllowed("other:app.aapswear", "app.aapswear"))
        assertFalse(packageAllowed("app.aapswear.extra", "app.aapswear"))
    }

    @Test fun componentValidation() {
        assertEquals("default-state", componentCommands("{\"androidx.profileinstaller.Receiver\":0}", "old").single()[2])
        listOf("{\"arbitrary; command\":1}", "{\"androidx..Receiver\":1}", "{\"androidx.Receiver\":true}", "{\"androidx.Receiver\":7}", "[]", "invalid").forEach { text -> error("rollback_component_state_invalid") { componentCommands(text, "old") } }
    }

    @Test fun adbQuotesAndFailures() {
        val commands = mutableListOf<List<String>>()
        val adb =
            Adb(
                "fake",
                "watch:5555",
                CommandRunner { command, timeout ->
                    commands.add(command)
                    assertEquals(if ("install" in command) 180L else 30L, timeout)
                    ProcessResult(0, " ok \n")
                },
            )
        assertEquals("ok", adb.run("shell", "echo", "hello; rm x", "a'b"))
        assertEquals(listOf("fake", "-s", "watch:5555", "shell", "echo", "'hello; rm x'", "'a'\"'\"'b'"), commands.single())
        error("invalid_serial") { Adb("fake", "x;inject") }
        val failed = Adb("fake", "w", CommandRunner { _, _ -> ProcessResult(1, "private output") })
        assertEquals("", failed.run("x", optional = true))
        error("adb_command_failed") { failed.run("x") }
        error("apk_install_failed") { adb.install(Path.of("x")) }
    }

    @Test fun rollbackPreservesHistoryAndInvalidatesAcknowledgmentFirst() {
        val commands = mutableListOf<List<String>>()
        val adb =
            Adb(
                "fake",
                "w",
                CommandRunner { command, _ ->
                    commands.add(command.drop(3))
                    ProcessResult(0, "")
                },
            )
        assertTrue(rollback(adb, "old", "new", "Activity"))
        assertTrue(commands[1].contains("no_backup/product-migration-complete"))
        assertEquals(listOf("shell", "am", "start", "-W", "-n", "old/Activity"), commands.last())
        assertFalse(commands.flatten().any { "shared_prefs" in it || "database" in it })
        val unreachable = Adb("fake", "w", CommandRunner { _, _ -> throw MigrationError("adb_timeout_or_unavailable") })
        assertFalse(rollback(unreachable, "old", "new", "Activity"))
    }

    @Test fun preflightsAllSurfacesBeforeMutation() {
        val root = Files.createTempDirectory("preflight")
        val legacy = root.resolve("legacy").createDirectories()
        listOf("mobile", "wear").forEach { key ->
            val p = products.getValue(key)
            val dir = artifactDirectory(key, root).createDirectories()
            dir.resolve("app.apk").writeText("")
            dir.resolve("output-metadata.json").writeText("{\"applicationId\":\"${p.target}\",\"elements\":[{\"outputFile\":\"app.apk\"}]}")
            legacy.resolve(p.bridge).writeText("")
        }
        val commands = mutableListOf<List<String>>()
        val runner =
            CommandRunner { command, _ ->
                commands.add(command)
                ProcessResult(
                    0,
                    when {
                        command.last() == "get-state" -> "device"
                        command.last() == "ro.build.characteristics" -> "phone"
                        else -> "Model"
                    },
                )
            }
        var report = ""
        assertEquals(1, runMigration(listOf("--product", "sugarlicious", "--phone", "p", "--watch", "w", "--legacy-dir", legacy.toString()), root, runner) { report = it })
        assertTrue(report.contains("device_role_mismatch"))
        assertFalse(commands.any { "install" in it || "pm" in it })
    }

    @Test fun existingTargetMirrorsBroadcastPermission() {
        val commands = mutableListOf<List<String>>()
        val adb =
            Adb(
                "fake",
                "w",
                CommandRunner { command, _ ->
                    commands.add(command)
                    ProcessResult(
                        0,
                        when {
                            "path" in command -> "package:apk"
                            "package" in command && "dumpsys" in command -> "com.dexcom.cgm.EXTERNAL_PERMISSION: granted=true"
                            command.last().contains("product-migration-complete") -> "acknowledged"
                            "install" in command -> "Success"
                            else -> ""
                        },
                    )
                },
            )
        val result = migrate(adb, "mobile", Path.of("new.apk"), Path.of("old.apk"))
        assertEquals("updated_existing_target", result["status"]?.jsonPrimitive?.content)
        assertTrue(commands.any { "grant" in it && "com.dexcom.cgm.EXTERNAL_PERMISSION" in it })
    }

    @Test fun legacyRequiresAcknowledgmentAndRollsBack() {
        val commands = mutableListOf<List<String>>()
        val adb =
            Adb(
                "fake",
                "w",
                CommandRunner { command, _ ->
                    commands.add(command)
                    ProcessResult(
                        0,
                        when {
                            "path" in command && command.last() == "app.aapswear" -> "package:old"
                            "install" in command -> "Success"
                            command.last().contains("product-migration-complete") -> "fresh"
                            else -> ""
                        },
                    )
                },
            )
        val result = migrate(adb, "mobile", Path.of("new.apk"), Path.of("bridge.apk"))
        assertEquals("legacy_import_not_acknowledged", result["error_code"]?.jsonPrimitive?.content)
        assertEquals(true, result["legacy_restart_attempt_succeeded"]?.jsonPrimitive?.boolean)
        assertFalse(commands.any { "disable-user" in it })
    }

    @Test fun freshInstallAndTimeoutAreReportedWithoutLegacyRetirement() {
        fun adb(terminal: String) =
            Adb(
                "fake",
                "w",
                CommandRunner { command, _ ->
                    ProcessResult(
                        0,
                        when {
                            "install" in command -> "Success"
                            command.last().contains("product-migration-complete") -> terminal
                            else -> ""
                        },
                    )
                },
            )
        val fresh = migrate(adb("fresh"), "mobile", Path.of("new.apk"), Path.of("bridge.apk"))
        assertEquals("fresh_install", fresh["status"]?.jsonPrimitive?.content)
        var nanos = 0L
        val timeout = migrate(adb(""), "mobile", Path.of("new.apk"), Path.of("bridge.apk"), nanoTime = { nanos.also { nanos += 100_000_000_000L } }, pause = {})
        assertEquals("migration_acknowledgment_timeout", timeout["error_code"]?.jsonPrimitive?.content)
    }

    @Test fun deviceRoleAndLaunchErrors() {
        val wrong = Adb("fake", "w", CommandRunner { command, _ -> ProcessResult(0, if (command.last() == "get-state") "offline" else "") })
        error("device_not_ready") { deviceInfo(wrong, true) }
        val launch = Adb("fake", "w", CommandRunner { _, _ -> ProcessResult(0, "Error: unable to resolve") })
        error("activity_launch_failed") { launch.launch("pkg", "Activity") }
        val timeout = Adb("fake", "w", CommandRunner { _, _ -> throw MigrationError("adb_timeout_or_unavailable") })
        error("adb_timeout_or_unavailable") { timeout.run("get-state", optional = true) }
    }

    @Test fun codefreeVerifier() {
        val apk = Files.createTempFile("vigil", ".apk")

        fun zip(name: String) {
            ZipOutputStream(Files.newOutputStream(apk)).use {
                it.putNextEntry(ZipEntry(name))
                it.write(byteArrayOf(1))
                it.closeEntry()
            }
        }
        zip("res/raw/watchface.xml")
        assertEquals(64, verifyCodefreeVigil(apk).length)
        zip("nested/classes2.dex")
        try {
            verifyCodefreeVigil(apk)
            fail("DEX accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("contains executable DEX code"))
        }
    }
}
