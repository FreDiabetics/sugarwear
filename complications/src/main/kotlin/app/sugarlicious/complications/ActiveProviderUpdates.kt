package app.sugarlicious.complications

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/** Avoid binding every installed provider on every minute/data event. */
object ActiveProviderUpdates {
    private const val DISCOVERY_VERSION = "discovery_retry_v2"
    private const val PROBE_PREFIX = "probe."
    private const val PROBE_RETRY_MS = 60_000L

    private fun preferences(context: Context): android.content.SharedPreferences {
        val prefs = context.getSharedPreferences("provider_update_instances", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(DISCOVERY_VERSION, false)) {
            // v1 wrote empty sets before discovery was delivered. They were not acknowledged
            // inactive instances. Preserve known live IDs and make old empty entries retryable.
            val editor = prefs.edit()
            prefs.all
                .filterValues { it is Set<*> && it.isEmpty() }
                .keys
                .forEach(editor::remove)
            editor.putBoolean(DISCOVERY_VERSION, true).apply()
        }
        return prefs
    }

    @Synchronized
    fun activate(context: Context, provider: Class<*>, instanceId: Int) {
        val prefs = preferences(context)
        val existing = prefs.getStringSet(provider.name, emptySet()).orEmpty()
        if (instanceId.toString() in existing) return
        val ids = existing + instanceId.toString()
        prefs
            .edit()
            .putStringSet(provider.name, ids)
            .remove(PROBE_PREFIX + provider.name)
            .apply()
    }

    @Synchronized
    fun deactivate(context: Context, provider: Class<*>, instanceId: Int) {
        val prefs = preferences(context)
        val ids = prefs.getStringSet(provider.name, emptySet()).orEmpty() - instanceId.toString()
        prefs
            .edit()
            .putStringSet(provider.name, ids)
            .remove(PROBE_PREFIX + provider.name)
            .apply()
    }

    /** Null requests discovery; empty skips a known inactive provider or coalesces an unacknowledged probe. */
    @Synchronized
    internal fun targets(context: Context, provider: Class<*>, nowEpochMs: Long = System.currentTimeMillis()): IntArray? {
        val prefs = preferences(context)
        if (!prefs.contains(provider.name)) {
            val previousProbe = prefs.getLong(PROBE_PREFIX + provider.name, Long.MIN_VALUE)
            if (nowEpochMs >= previousProbe && previousProbe > 0L && nowEpochMs - previousProbe < PROBE_RETRY_MS) return intArrayOf()
            // This is a lease, not an inactive acknowledgement. A lost IPC/process restart
            // must not permanently suppress the next refresh. Clock rollback also retries.
            prefs.edit().putLong(PROBE_PREFIX + provider.name, nowEpochMs).apply()
            return null
        }
        return prefs
            .getStringSet(provider.name, emptySet())
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toIntArray()
    }

    @Synchronized
    fun requestKnownActive(context: Context, provider: Class<*>) {
        val ids = preferences(context).getStringSet(provider.name, emptySet()).orEmpty()
        if (ids.isNotEmpty()) request(context, provider)
    }

    fun request(context: Context, provider: Class<*>) {
        val ids = targets(context, provider)
        if (ids != null && ids.isEmpty()) return
        val requester = ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, provider))
        if (ids == null) requester.requestUpdateAll() else requester.requestUpdate(*ids)
    }
}

abstract class TrackedComplicationService : SuspendingComplicationDataSourceService() {
    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        ActiveProviderUpdates.activate(this, javaClass, complicationInstanceId)
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        ActiveProviderUpdates.deactivate(this, javaClass, complicationInstanceId)
        super.onComplicationDeactivated(complicationInstanceId)
    }
}
