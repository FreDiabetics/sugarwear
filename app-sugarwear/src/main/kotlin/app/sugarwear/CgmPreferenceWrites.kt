package app.sugarwear

import android.content.SharedPreferences

/** Acknowledges destructive cleanup only after Android confirms the disk write. */
internal fun SharedPreferences.clearSynchronously(failureMessage: String) {
    val pendingWrite = edit()
    pendingWrite.clear()
    if (!pendingWrite.commit()) {
        error(failureMessage)
    }
}
