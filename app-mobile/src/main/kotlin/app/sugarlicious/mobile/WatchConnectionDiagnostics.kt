package app.sugarlicious.mobile

import android.content.Context
import androidx.core.content.edit
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private const val DIAGNOSTICS_PREFS = "diagnostics"
private const val REACHABLE_WATCHES = "reachableWatches"
private const val REACHABILITY_CHECKED_AT = "watchReachabilityCheckedAt"
private const val LAST_WATCH_CONTACT_AT = "lastWatchContactAt"

internal suspend fun refreshReachableWatchNodeIds(context: Context): List<String> {
    val nodes =
        Wearable
            .getNodeClient(context)
            .connectedNodes
            .await()

    recordReachableWatchCount(context, nodes.size, names = nodes.map { it.displayName })
    return nodes.map { it.id }
}

internal fun recordReachableWatchCount(
    context: Context,
    count: Int,
    now: Long = System.currentTimeMillis(),
    names: List<String>? = null,
) {
    context
        .getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        .edit {
            putInt(REACHABLE_WATCHES, count.coerceAtLeast(0))
            if (count <= 0) {
                remove("connectedWatchModels")
            } else {
                names?.let {
                    putString(
                        "connectedWatchModels",
                        it
                            .map(String::trim)
                            .filter(String::isNotBlank)
                            .distinct()
                            .sorted()
                            .joinToString(" � "),
                    )
                }
            }
            putLong(REACHABILITY_CHECKED_AT, now)
            if (count > 0) putLong(LAST_WATCH_CONTACT_AT, now)
        }
}

internal fun recordWatchContact(
    context: Context,
    now: Long = System.currentTimeMillis(),
) {
    val preferences =
        context.getSharedPreferences(
            DIAGNOSTICS_PREFS,
            Context.MODE_PRIVATE,
        )
    preferences.edit {
        putInt(
            REACHABLE_WATCHES,
            preferences.getInt(REACHABLE_WATCHES, 0).coerceAtLeast(1),
        )
        putLong(LAST_WATCH_CONTACT_AT, now)
    }
}

internal fun isWatchConnected(reachableWatches: Int): Boolean = reachableWatches > 0
