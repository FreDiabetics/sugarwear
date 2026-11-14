package app.sugarlicious.uishared

import android.app.AlarmManager
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.File

/** Only the legacy process is quiesced; sensor credentials, bonds and histories are untouched. */
object ProductMigrationLifecycle {
    const val RETIRED = "product-migration-retired"
    private const val QUIESCED = "product-migration-quiesced"

    /** Armed only by the migration tool after force-stop and verifying there is no old process. */
    fun isQuiesced(context: Context): Boolean {
        val file = File(context.noBackupFilesDir, QUIESCED)
        return file.exists() && System.currentTimeMillis() - file.lastModified() in 0L..180_000L
    }

    fun isRetired(context: Context): Boolean = File(context.noBackupFilesDir, RETIRED).exists()

    fun requireReady(context: Context) {
        check(ProductDataMigrationProvider.migrationFailure(context) == null) { "Product data migration incomplete" }
    }

    @android.annotation.SuppressLint("UseKtx") // KTX edit does not return the commit result required by this migration barrier.
    @Suppress("DEPRECATION")
    fun prepareSource(context: Context) {
        check(isQuiesced(context) || isRetired(context)) { "Legacy process must be quiesced by the migration tool" }
        val manager = context.packageManager
        val originalFile = File(context.noBackupFilesDir, "product-migration-components.json")
        val info = manager.getPackageInfo(context.packageName, PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES)
        if (!originalFile.exists()) {
            val original = org.json.JSONObject()
            (info.receivers.orEmpty().map { it.name } + info.services.orEmpty().map { it.name }).forEach { name ->
                original.put(name, manager.getComponentEnabledSetting(ComponentName(context.packageName, name)))
            }
            originalFile.writeText(original.toString())
        }
        info.receivers.orEmpty().forEach { receiver -> manager.setComponentEnabledSetting(ComponentName(context.packageName, receiver.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        info.services.orEmpty().forEach { service ->
            context.stopService(Intent().setComponent(ComponentName(context.packageName, service.name)))
            manager.setComponentEnabledSetting(ComponentName(context.packageName, service.name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        context.getSystemService(JobScheduler::class.java)?.cancelAll()
        if (android.os.Build.VERSION.SDK_INT >= 34) context.getSystemService(AlarmManager::class.java)?.cancelAll()
        File(context.dataDir, "shared_prefs").listFiles().orEmpty().filter { it.extension == "xml" }.forEach {
            check(context.getSharedPreferences(it.nameWithoutExtension, Context.MODE_PRIVATE).edit().commit())
        }
    }

    fun restoreSource(context: Context) {
        val file = File(context.noBackupFilesDir, "product-migration-components.json")
        if (!file.exists()) return
        val original = org.json.JSONObject(file.readText())
        original.keys().forEach { name ->
            context.packageManager.setComponentEnabledSetting(ComponentName(context.packageName, name), original.getInt(name), PackageManager.DONT_KILL_APP)
        }
        file.delete()
        File(context.noBackupFilesDir, QUIESCED).delete()
        File(context.noBackupFilesDir, RETIRED).delete()
    }

    fun retireSource(context: Context) {
        File(context.noBackupFilesDir, RETIRED).writeText("migrated")
        prepareSource(context)
    }
}
