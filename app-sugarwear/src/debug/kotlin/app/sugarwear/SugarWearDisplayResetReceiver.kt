package app.sugarwear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sugarlicious.complications.DirectToWatchPreferences
import app.sugarlicious.uishared.SugarWearDisplayDefaults

/** Shell-protected display reset for the explicitly requested device verification. No sensor access. */
class SugarWearDisplayResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.aapswear.g7watch.DEBUG_RESET_DISPLAY") return
        SugarWearDisplayDefaults.reset(context)
        val appearance = CgmAppearanceStore(context)
        appearance.setGraphHours(2)
        appearance.setTileGraphHours(2)
        CgmDirectToWatchSettingsStore(context).sync()
        DirectToWatchPreferences.requestUpdates(context)
        CgmCollectorTileService.requestUpdate(context)
        resultData = "Display reference restored; graphHours=2; sensor state untouched"
    }
}
