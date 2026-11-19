package app.sugarlicious.uishared

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Register a concrete subclass in both products, with a literal per-package authority.
 * Target must run before any preferences/database/collector initialization. The source hook
 * must stop writers, checkpoint/close databases and synchronously commit preferences.
 * Never log exported secrets. Android Keystore keys themselves cannot be transferred.
 */
abstract class ProductDataMigrationProvider : ContentProvider() {
    protected abstract val legacyPackage: String
    protected abstract val targetPackage: String

    protected open fun exportSecrets(): Map<String, String> = emptyMap()

    protected open fun importSecrets(values: Map<String, String>) {}

    protected open fun beforeSnapshot() {}

    protected open fun afterMigrationAccepted() {}

    protected open fun afterMigrationAborted() {}

    final override fun onCreate(): Boolean {
        val ctx = requireNotNull(context)
        if (ctx.packageName != targetPackage) return true
        val marker = File(ctx.noBackupFilesDir, COMPLETE)
        var verifiedAttemptSource: Uri? = null
        try {
            val markerText =
                try {
                    AtomicFile(marker).openRead().bufferedReader().use { it.readText() }
                } catch (_: FileNotFoundException) {
                    null
                }
            val state = migrationState(markerText)
            if (state == MigrationState.FINISHED) {
                File(ctx.noBackupFilesDir, FAILURE).delete()
                return true
            }
            val authority = "$legacyPackage.product-migration"
            if (state == MigrationState.ACKNOWLEDGE) {
                checkNotNull(ctx.contentResolver.call("content://$authority".toUri(), "accepted", null, null))
                writeMarker(marker, "acknowledged")
                File(ctx.noBackupFilesDir, FAILURE).delete()
                return true
            }
            val source = ctx.packageManager.resolveContentProvider(authority, 0)
            if (source == null) {
                val legacyInstalled =
                    try {
                        ctx.packageManager.getApplicationInfo(legacyPackage, 0)
                        true
                    } catch (_: PackageManager.NameNotFoundException) {
                        false
                    }
                requireFreshInstallAllowed(legacyInstalled)
                writeMarker(marker, "fresh")
            } else {
                check(source.packageName == legacyPackage) { "Unexpected migration source" }
                check(ctx.packageManager.checkSignatures(legacyPackage, targetPackage) == PackageManager.SIGNATURE_MATCH) {
                    "Migration signature mismatch"
                }
                verifiedAttemptSource = "content://$authority".toUri()
                ctx.contentResolver.openInputStream("content://$authority/snapshot".toUri())!!.use { input ->
                    importArchive(ctx, ZipInputStream(input))
                }
                writeMarker(marker, "imported")
                checkNotNull(ctx.contentResolver.call("content://$authority".toUri(), "accepted", null, null))
                writeMarker(marker, "acknowledged")
            }
            File(ctx.noBackupFilesDir, FAILURE).delete()
        } catch (error: Exception) {
            // The old package must resume normal operation after a failed snapshot/import.
            // Only a resolved, signature-verified source is eligible for this callback.
            verifiedAttemptSource?.let { source ->
                val restored = runCatching { checkNotNull(ctx.contentResolver.call(source, "aborted", null, null)) }.isSuccess
                // A resumed source may gain new readings/session keys. Retry a fresh snapshot.
                if (restored) AtomicFile(marker).delete()
            }
            // No credentials or exception messages in persistent diagnostics.
            File(ctx.noBackupFilesDir, FAILURE).writeText(error.javaClass.simpleName)
        }
        return true
    }

    private fun authorize() {
        val ctx = requireNotNull(context)
        if (ctx.packageName != legacyPackage ||
            ctx.packageManager.getPackagesForUid(Binder.getCallingUid())?.toList() != listOf(targetPackage) ||
            ctx.packageManager.checkSignatures(legacyPackage, targetPackage) != PackageManager.SIGNATURE_MATCH
        ) {
            throw SecurityException("Migration caller rejected")
        }
    }

    final override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        authorize()
        if (mode != "r" || uri.path != "/snapshot") throw FileNotFoundException("Read-only snapshot")
        beforeSnapshot()
        val secrets = JSONObject(exportSecrets()).toString().toByteArray(Charsets.UTF_8)
        check(secrets.size <= MAX_BYTES)
        val pipes = ParcelFileDescriptor.createReliablePipe()
        Thread({
            try {
                ZipOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])).use { zip ->
                    var bytes = secrets.size.toLong()
                    var entries = 0
                    val root = requireNotNull(context).dataDir.canonicalFile
                    for (folder in listOf("shared_prefs", "databases", "files")) {
                        val directory = File(root, folder)
                        if (!directory.exists()) continue
                        directory
                            .walkTopDown()
                            .onEnter { dir ->
                                dir.canonicalFile == dir.absoluteFile &&
                                    (dir == directory || allowedPath(dir.relativeTo(root).invariantSeparatorsPath))
                            }.filter { it.isFile }
                            .forEach { file ->
                                val path = file.relativeTo(root).invariantSeparatorsPath
                                if (file.canonicalFile != file.absoluteFile || !allowedPath(path)) return@forEach
                                check(++entries <= MAX_ENTRIES)
                                zip.putNextEntry(ZipEntry(path))
                                file.inputStream().use { input ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        bytes += size
                                        check(bytes <= MAX_BYTES)
                                        zip.write(buffer, 0, size)
                                    }
                                }
                                zip.closeEntry()
                            }
                    }
                    zip.putNextEntry(ZipEntry(SECRETS))
                    zip.write(secrets)
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(END))
                    zip.closeEntry()
                }
            } catch (error: Exception) {
                runCatching { pipes[1].closeWithError("Snapshot failed (${error.javaClass.simpleName})") }
            } finally {
                secrets.fill(0)
            }
        }, "product-data-migration").start()
        return pipes[0]
    }

    private fun importArchive(ctx: Context, zip: ZipInputStream) {
        val root = ctx.dataDir.canonicalFile
        var total = 0L
        var count = 0
        var secrets: Map<String, String>? = null
        var complete = false
        val seen = mutableSetOf<String>()
        zip.use {
            while (true) {
                val entry = zip.nextEntry ?: break
                check(!complete && ++count <= MAX_ENTRIES + 2 && seen.add(entry.name))
                check(!entry.isDirectory)
                val output =
                    if (entry.name == SECRETS || entry.name == END) {
                        java.io.ByteArrayOutputStream()
                    } else {
                        check(allowedPath(entry.name)) { "Invalid migration path" }
                        val destination = safeDestination(root, entry.name)
                        check(destination.parentFile!!.mkdirs() || destination.parentFile!!.isDirectory)
                        destination.outputStream()
                    }
                output.use {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = zip.read(buffer)
                        if (size < 0) break
                        total += size
                        check(total <= MAX_BYTES)
                        it.write(buffer, 0, size)
                    }
                }
                if (entry.name == SECRETS) {
                    val json = JSONObject((output as java.io.ByteArrayOutputStream).toString("UTF-8"))
                    secrets = json.keys().asSequence().associateWith { json.getString(it) }
                }
                if (entry.name == END) {
                    check((output as java.io.ByteArrayOutputStream).size() == 0 && secrets != null)
                    complete = true
                }
            }
        }
        check(complete) { "Incomplete snapshot" }
        importSecrets(requireNotNull(secrets))
    }

    final override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        authorize()
        when (method) {
            "accepted" -> afterMigrationAccepted()
            "aborted" -> afterMigrationAborted()
            else -> throw UnsupportedOperationException()
        }
        return Bundle()
    }

    final override fun getType(uri: Uri): String = "application/zip"

    final override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()

    final override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    final override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        private const val COMPLETE = "product-migration-complete"
        private const val FAILURE = "product-migration-failure"
        private const val SECRETS = "__migration_secrets.json"
        private const val END = "__migration_complete"
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val MAX_ENTRIES = 10000

        internal enum class MigrationState { IMPORT, ACKNOWLEDGE, FINISHED }

        internal fun migrationState(marker: String?): MigrationState =
            when (marker) {
                null -> MigrationState.IMPORT
                "imported" -> MigrationState.ACKNOWLEDGE
                "fresh", "acknowledged" -> MigrationState.FINISHED
                else -> error("Invalid migration marker")
            }

        internal fun requireFreshInstallAllowed(legacyInstalled: Boolean) {
            check(!legacyInstalled) { "Legacy app installed without migration bridge" }
        }

        private fun writeMarker(file: File, value: String) {
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(value.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }

        fun migrationFailure(context: Context): String? =
            File(context.noBackupFilesDir, FAILURE)
                .takeIf { it.exists() }
                ?.readText()

        internal fun allowedPath(path: String): Boolean {
            if (path.length > 1024 || path.contains('\\') || path.contains('\u0000')) return false
            val parts = path.split('/')
            if (parts.size < 2 || parts.any { it.isEmpty() || it == "." || it == ".." }) return false
            if (parts.first() !in setOf("shared_prefs", "databases", "files")) return false
            return parts.none {
                val name = it.lowercase(java.util.Locale.ROOT)
                name == "cache" ||
                    name.contains("diagnostic") ||
                    name == "logs" ||
                    name.endsWith(".apk") ||
                    name.endsWith(".log")
            }
        }

        /** [root] is the canonical data directory, resolved once before processing entries. */
        internal fun safeDestination(root: File, path: String): File {
            require(allowedPath(path))
            val file = File(root, path)
            require(file.canonicalFile == file.absoluteFile)
            require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
            return file
        }
    }
}
