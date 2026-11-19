package app.sugarlicious.tools.maintenance.migration

import kotlinx.serialization.json.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.*

class MigrationError(
    val code: String,
    cause: Throwable? = null,
) : RuntimeException(code, cause)

data class ProcessResult(
    val exitCode: Int,
    val output: String,
)

fun interface CommandRunner {
    fun execute(command: List<String>, timeoutSeconds: Long): ProcessResult
}

object SystemCommandRunner : CommandRunner {
    override fun execute(command: List<String>, timeoutSeconds: Long): ProcessResult {
        try {
            val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val output = java.io.ByteArrayOutputStream()
            val readFailure =
                java.util.concurrent.atomic
                    .AtomicReference<IOException?>()
            val reader =
                Thread {
                    try {
                        process.inputStream.use { it.copyTo(output) }
                    } catch (e: IOException) {
                        readFailure.set(e)
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor()
                reader.join()
                throw MigrationError("adb_timeout_or_unavailable")
            }
            reader.join()
            readFailure.get()?.let { throw MigrationError("adb_timeout_or_unavailable", it) }
            return ProcessResult(process.exitValue(), output.toString(Charsets.UTF_8))
        } catch (e: IOException) {
            throw MigrationError("adb_timeout_or_unavailable", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MigrationError("adb_timeout_or_unavailable", e)
        }
    }
}

val permissions = setOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION").map { "android.permission.$it" }.toSet() + setOf("com.dexcom.cgm.EXTERNAL_PERMISSION", "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE")

data class Product(
    val old: String,
    val target: String,
    val module: String,
    val activity: String,
    val bridge: String,
)

val products =
    mapOf(
        "mobile" to Product("app.aapswear", "app.sugarlicious", "app-mobile", "app.sugarlicious.mobile.MainActivity", "legacy-mobile.apk"),
        "wear" to Product("app.aapswear", "app.sugarlicious", "app-wear", "app.sugarlicious.wear.WearActivity", "legacy-wear.apk"),
        "sugarwear" to Product("app.aapswear.g7watch", "app.sugarwear", "app-sugarwear", "app.sugarwear.CgmWatchActivity", "legacy-sugarwear.apk"),
    )

fun validateMetadata(metadata: JsonObject, expected: String, directory: Path): Path {
    if (metadata["applicationId"]?.jsonPrimitive?.content != expected) throw MigrationError("apk_application_id_mismatch")
    val entries = metadata["elements"] as? JsonArray
    val output = entries?.singleOrNull()?.let { it as? JsonObject }?.get("outputFile") as? JsonPrimitive
    if (output == null || !output.isString) throw MigrationError("apk_requires_single_output")
    val base = directory.toAbsolutePath().normalize().let { if (it.exists()) it.toRealPath() else it }
    val apk = base.resolve(output.content).normalize().let { if (it.exists()) it.toRealPath() else it }
    if (apk.parent != base || apk.extension != "apk") throw MigrationError("apk_path_invalid")
    return apk
}

fun extractGrants(dump: String): Set<String> =
    Regex("^\\s*([A-Za-z0-9_.]+):\\s+granted=true\\b", RegexOption.MULTILINE)
        .findAll(dump)
        .map { it.groupValues[1] }
        .toSet()
        .intersect(permissions)

fun migrationPlan(product: String, phone: String?, watch: String?): List<Pair<String, String>> {
    if (product !in setOf("sugarlicious", "sugarwear", "both")) throw MigrationError("unknown_product")
    if (watch.isNullOrEmpty() || (product != "sugarwear" && phone.isNullOrEmpty())) throw MigrationError("explicit_serial_required")
    if (!phone.isNullOrEmpty() && phone == watch) throw MigrationError("phone_watch_serial_collision")
    return buildList {
        if (product != "sugarwear") {
            add("mobile" to phone!!)
            add("wear" to watch)
        }
        if (product != "sugarlicious") add("sugarwear" to watch)
    }
}

fun packageAllowed(text: String, pkg: String) = pkg in text.trim().split(Regex("[:\\s,]+"))

fun componentCommands(text: String, pkg: String): List<List<String>> {
    val data =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            throw MigrationError("rollback_component_state_invalid", e)
        }
    if (data == null || data.size > 500) throw MigrationError("rollback_component_state_invalid")
    val modes = listOf("default-state", "enable", "disable", "disable-user", "disable-until-used")
    return data.map { (name, value) ->
        val primitive = value as? JsonPrimitive
        val state = primitive?.takeUnless { it.isString }?.intOrNull
        if (!Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+").matches(name) || state == null || state !in modes.indices) throw MigrationError("rollback_component_state_invalid")
        listOf("shell", "pm", modes[state], "--user", "0", "$pkg/$name")
    }
}

fun shellQuote(token: String) = if (Regex("[A-Za-z0-9_@%+=:,./-]+").matches(token)) token else "'" + token.replace("'", "'\"'\"'") + "'"

class Adb(
    val executable: String,
    val serial: String,
    val runner: CommandRunner = SystemCommandRunner,
) {
    init {
        if (!Regex("[A-Za-z0-9_.:\\[\\]-]+").matches(serial)) throw MigrationError("invalid_serial")
    }

    fun run(vararg args: String, optional: Boolean = false, timeout: Long = 30): String {
        val remote = if (args.firstOrNull() == "shell") listOf("shell") + args.drop(1).map(::shellQuote) else args.toList()
        val result = runner.execute(listOf(executable, "-s", serial) + remote, timeout)
        if (result.exitCode != 0 && !optional) throw MigrationError("adb_command_failed")
        return if (result.exitCode == 0) result.output.trim() else ""
    }

    fun installed(pkg: String) = run("shell", "pm", "path", pkg, optional = true).startsWith("package:")

    fun marker(pkg: String, name: String) = run("shell", "run-as", pkg, "cat", "no_backup/$name", optional = true)

    fun launch(pkg: String, activity: String) {
        val output = run("shell", "am", "start", "-W", "-n", "$pkg/$activity", timeout = 45)
        if ("Error:" in output || "Exception" in output) throw MigrationError("activity_launch_failed")
    }

    fun install(path: Path) {
        if (run("install", "-r", "-t", path.toString(), timeout = 180).lineSequence().none { it.trim() == "Success" }) throw MigrationError("apk_install_failed")
    }
}

data class Access(
    val grants: Set<String>,
    val dnd: Boolean,
    val battery: Boolean,
    val exact: Boolean,
)

fun deviceInfo(adb: Adb, watch: Boolean): JsonObject {
    if (adb.run("get-state") != "device") throw MigrationError("device_not_ready")
    if (("watch" in adb.run("shell", "getprop", "ro.build.characteristics").split(',').map(String::trim)) != watch) throw MigrationError("device_role_mismatch")
    return buildJsonObject {
        put("model", adb.run("shell", "getprop", "ro.product.model"))
        put("role", if (watch) "watch" else "phone")
    }
}

fun existingAccess(adb: Adb, pkg: String) = Access(extractGrants(adb.run("shell", "dumpsys", "package", pkg)), packageAllowed(adb.run("shell", "settings", "get", "secure", "enabled_notification_policy_access_packages"), pkg), adb.run("shell", "dumpsys", "deviceidle", "whitelist").lineSequence().any { pkg in it.split(',') }, Regex("SCHEDULE_EXACT_ALARM:\\s*allow\\b").containsMatchIn(adb.run("shell", "cmd", "appops", "get", pkg, "SCHEDULE_EXACT_ALARM", optional = true)))

fun mirrorAccess(adb: Adb, pkg: String, access: Access) {
    access.grants.sorted().forEach { adb.run("shell", "pm", "grant", "--user", "0", pkg, it) }
    if (access.battery) adb.run("shell", "dumpsys", "deviceidle", "whitelist", "+$pkg")
    if (access.dnd) adb.run("shell", "cmd", "notification", "allow_dnd", pkg, "0")
    if (access.exact) adb.run("shell", "cmd", "appops", "set", pkg, "SCHEDULE_EXACT_ALARM", "allow")
}

fun rollback(adb: Adb, old: String, target: String, activity: String): Boolean {
    try {
        adb.run("shell", "am", "force-stop", target)
        adb.run("shell", "run-as", target, "rm", "-f", "no_backup/product-migration-complete", "no_backup/product-migration-complete.bak")
    } catch (e: MigrationError) {
        reportRecoveryFailure(e)
        return false
    }
    var restored = true
    val operations = mutableListOf<() -> Unit>()
    try {
        val snapshot = adb.marker(old, "product-migration-components.json")
        if (snapshot.isNotEmpty()) {
            componentCommands(snapshot, old).forEach { command ->
                operations.add {
                    adb.run(*command.toTypedArray())
                }
            }
        }
    } catch (e: MigrationError) {
        reportRecoveryFailure(e)
        restored = false
    }
    operations.add {
        adb.run("shell", "run-as", old, "rm", "-f", "no_backup/product-migration-quiesced", "no_backup/product-migration-retired")
    }
    operations.add {
        adb.run("shell", "pm", "enable", "--user", "0", old)
    }
    operations.add { adb.launch(old, activity) }
    operations.forEach {
        try {
            it()
        } catch (e: MigrationError) {
            reportRecoveryFailure(e)
            restored = false
        }
    }
    return restored
}

private fun reportRecoveryFailure(failure: Exception) {
    // Do not print subprocess output, command arguments or device identifiers.
    System.err.println("Migration recovery failed: ${if (failure is MigrationError) failure.code else failure.javaClass.simpleName}")
}

fun migrate(adb: Adb, key: String, apk: Path, bridge: Path, nanoTime: () -> Long = System::nanoTime, pause: () -> Unit = { Thread.sleep(1000) }): JsonObject {
    val p = products.getValue(key)
    val legacy = adb.installed(p.old)
    val target = adb.installed(p.target)
    val result =
        linkedMapOf<String, JsonElement>(
            "product_surface" to JsonPrimitive(key),
            "before" to
                buildJsonObject {
                    put("legacy_installed", legacy)
                    put("target_installed", target)
                },
        )
    val access = if (legacy) existingAccess(adb, p.old) else null
    var terminal = if (target) adb.marker(p.target, "product-migration-complete") else ""
    if (terminal in setOf("acknowledged", "fresh")) {
        adb.install(apk)
        access?.let { mirrorAccess(adb, p.target, it) }
        adb.launch(p.target, p.activity)
        result["status"] = JsonPrimitive("updated_existing_target")
        result["after"] =
            buildJsonObject {
                put("target_installed", true)
                put("migration", terminal)
            }
        return JsonObject(result)
    }
    try {
        if (legacy) {
            adb.run("shell", "pm", "enable", "--user", "0", p.old)
            adb.install(bridge)
        }
        adb.install(apk)
        access?.let { mirrorAccess(adb, p.target, it) }
        adb.run("shell", "am", "force-stop", p.old)
        adb.run("shell", "am", "force-stop", p.target)
        if (legacy) {
            if (adb.run("shell", "pidof", p.old, optional = true).isNotEmpty()) throw MigrationError("legacy_process_not_quiescent")
            adb.run("shell", "run-as", p.old, "mkdir", "-p", "no_backup")
            adb.run("shell", "run-as", p.old, "touch", "no_backup/product-migration-quiesced")
            adb.run("shell", "run-as", p.old, "ls", "no_backup/product-migration-quiesced")
            if (adb.run("shell", "pidof", p.old, optional = true).isNotEmpty()) throw MigrationError("legacy_process_restarted_before_import")
        }
        adb.launch(p.target, p.activity)
        val start = nanoTime()
        while (true) {
            if (nanoTime() - start >= TimeUnit.SECONDS.toNanos(90)) throw MigrationError("migration_acknowledgment_timeout")
            if (adb.marker(p.target, "product-migration-failure").isNotEmpty()) throw MigrationError("target_reported_migration_failure")
            terminal = adb.marker(p.target, "product-migration-complete")
            if (terminal in setOf("acknowledged", "fresh")) {
                if (legacy && terminal != "acknowledged") throw MigrationError("legacy_import_not_acknowledged")
                break
            }
            pause()
        }
        if (legacy) {
            adb.run("shell", "am", "force-stop", p.old)
            adb.run("shell", "pm", "disable-user", "--user", "0", p.old)
        }
        result["status"] = JsonPrimitive(if (access != null) "migrated" else "fresh_install")
        result["after"] =
            buildJsonObject {
                put("target_installed", true)
                put("migration", terminal)
                put("legacy_disabled", legacy)
            }
        result["mirrored_runtime_grant_count"] = JsonPrimitive(access?.grants?.size ?: 0)
    } catch (e: Exception) {
        if (e is InterruptedException) Thread.currentThread().interrupt()
        result["status"] = JsonPrimitive("failed")
        result["error_code"] = JsonPrimitive(if (e is MigrationError) e.code else "unexpected_failure")
        if (legacy) result["legacy_restart_attempt_succeeded"] = JsonPrimitive(rollback(adb, p.old, p.target, p.activity)) else adb.run("shell", "am", "force-stop", p.target, optional = true)
    }
    return JsonObject(result)
}

fun artifactDirectory(key: String, root: Path, sugarwearRoot: Path? = null): Path {
    if (key == "sugarwear" && sugarwearRoot == null) throw MigrationError("sugarwear_repository_required")
    return (if (key == "sugarwear") sugarwearRoot!! else root)
        .toAbsolutePath()
        .normalize()
        .resolve(products.getValue(key).module)
        .resolve("build/outputs/apk/debug")
}

fun verifyCodefreeVigil(apk: Path): String {
    ZipFile(apk.toFile()).use { archive -> if (archive.entries().asSequence().any { Regex("(^|/)classes[0-9]*\\.dex$").containsMatchIn(it.name) }) throw IllegalArgumentException("$apk contains executable DEX code") }
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(apk).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun runMigration(args: List<String>, root: Path, runner: CommandRunner = SystemCommandRunner, output: (String) -> Unit = ::println): Int {
    val results = mutableListOf<JsonElement>()
    val report = linkedMapOf<String, JsonElement>()
    try {
        if (args.size % 2 != 0) throw IllegalArgumentException("Expected --option value")
        val allowed = setOf("--phone", "--watch", "--product", "--legacy-dir", "--sugarwear-root", "--adb")
        val options =
            args.chunked(2).associate {
                if (it[0] !in allowed) throw IllegalArgumentException("Unknown option")
                it[0] to it[1]
            }
        val plan = migrationPlan(options["--product"] ?: throw IllegalArgumentException("--product required"), options["--phone"], options["--watch"])
        val legacy = Path.of(options["--legacy-dir"] ?: throw IllegalArgumentException("--legacy-dir required")).toAbsolutePath().normalize()
        val sdk = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        val executable = options["--adb"] ?: sdk?.let { Path.of(it, "platform-tools", if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb").toString() } ?: "adb"

        data class Prepared(
            val adb: Adb,
            val key: String,
            val apk: Path,
            val bridge: Path,
            val info: JsonObject,
        )
        val prepared =
            plan.map { (key, serial) ->
                val p = products.getValue(key)
                val directory = artifactDirectory(key, root, options["--sugarwear-root"]?.let(Path::of))
                val apk = validateMetadata(Json.parseToJsonElement(directory.resolve("output-metadata.json").readText()).jsonObject, p.target, directory)
                val bridge = legacy.resolve(p.bridge)
                if (!apk.isRegularFile() || !bridge.isRegularFile()) throw MigrationError("required_apk_missing")
                val adb = Adb(executable, serial, runner)
                Prepared(adb, key, apk, bridge, deviceInfo(adb, key != "mobile"))
            }
        for (item in prepared) {
            val result = JsonObject(migrate(item.adb, item.key, item.apk, item.bridge) + ("device" to item.info))
            results.add(result)
            if (result["status"]?.jsonPrimitive?.content == "failed") break
        }
        report["status"] = JsonPrimitive(if (results.any { it.jsonObject["status"]?.jsonPrimitive?.content == "failed" }) "failed" else "complete")
    } catch (e: MigrationError) {
        report["status"] = JsonPrimitive("failed")
        report["error_code"] = JsonPrimitive(e.code)
    } catch (e: IOException) {
        reportRecoveryFailure(e)
        report["status"] = JsonPrimitive("failed")
        report["error_code"] = JsonPrimitive("preflight_failed")
    } catch (e: IllegalArgumentException) {
        reportRecoveryFailure(e)
        report["status"] = JsonPrimitive("failed")
        report["error_code"] = JsonPrimitive("preflight_failed")
    }
    report["results"] = JsonArray(results)
    output(JsonObject(report).toString())
    return if (report["status"]?.jsonPrimitive?.content == "complete") 0 else 1
}

fun runMigrationTool(command: String, args: List<String>): Int? =
    when (command) {
        "migrate-products" -> runMigration(args, Path.of(System.getProperty("maintenance.repositoryRoot", System.getProperty("user.dir"))))
        "verify-codefree-vigil" -> {
            val root = if (args.isEmpty()) Path.of(System.getProperty("maintenance.repositoryRoot", System.getProperty("user.dir"))) else Path.of(args.single())
            val directory = root.resolve("watchfaces/vigil/build/outputs/apk/release")
            val apks = if (directory.exists()) directory.listDirectoryEntries("*.apk") else emptyList()
            if (apks.size != 1) {
                System.err.println("Expected exactly one Vigil release APK, found ${apks.size}")
                1
            } else {
                try {
                    println("PASS code-free: ${apks.single().fileName} SHA-256 ${verifyCodefreeVigil(apks.single())}")
                    0
                } catch (e: IOException) {
                    System.err.println(e.message)
                    1
                } catch (e: IllegalArgumentException) {
                    System.err.println(e.message)
                    1
                }
            }
        }
        else -> null
    }
