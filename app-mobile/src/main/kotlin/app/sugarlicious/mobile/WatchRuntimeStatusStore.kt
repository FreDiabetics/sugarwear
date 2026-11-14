package app.sugarlicious.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.sugarlicious.protocol.WatchRuntimeStatus

internal object WatchRuntimeStatusStore {
    private const val PREFS = "watch_runtime_status"
    private const val FACE = "active_face"
    private const val IDS = "active_complications"
    private const val SENT = "sent_at"

    fun save(
        context: Context,
        status: WatchRuntimeStatus,
    ) {
        val activeFaceIndex = status.activeSugarliciousFaceIndex
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (activeFaceIndex == null) {
                remove(FACE)
            } else {
                putInt(FACE, activeFaceIndex)
            }
            putString(IDS, status.activeComplicationIds.joinToString(","))
            putLong(SENT, status.sentAtEpochMs)
        }
    }

    fun read(context: Context): WatchRuntimeStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val face =
            if (prefs.contains(FACE)) {
                prefs.getInt(FACE, 0).coerceIn(sugarliciousWatchFaceCards.indices)
            } else {
                null
            }
        val ids =
            prefs
                .getString(IDS, "")
                .orEmpty()
                .split(',')
                .mapNotNull(String::toIntOrNull)
                .distinct()
        return WatchRuntimeStatus(face, ids, prefs.getLong(SENT, 0L))
    }

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }
}
