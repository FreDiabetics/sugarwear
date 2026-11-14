package app.sugarlicious.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.complications.DirectToWatchPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal fun cgmReadingUpdateApplicationContext(context: Context): Context = context.applicationContext

/** Process-local invalidation bus retained for binary/source compatibility. */
internal object WearCanonicalStateEvents {
    private val mutableUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates = mutableUpdates.asSharedFlow()

    fun publishLocalReadingUpdate() {
        mutableUpdates.tryEmit(Unit)
    }
}

/**
 * Collector-to-Vigil invalidation bridge.
 *
 * SugarWear is a separate local collector application. Its LIVE and BACKFILL readings do not
 * enter Sugarlicious Wear and are not forwarded to Mobile. Vigil is the deliberately direct-only
 * exception: its private providers read the collector database and must be invalidated immediately
 * after the database transaction commits, including after a backfill batch.
 */
class CgmReadingUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_CGM_READING_UPDATED) return
        DirectToWatchPreferences.requestUpdates(cgmReadingUpdateApplicationContext(context))
    }

    private companion object {
        const val ACTION_CGM_READING_UPDATED = "app.aapswear.g7watch.READING_UPDATED"
    }
}
