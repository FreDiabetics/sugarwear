package app.aapswear.g7watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aapswear.complications.DirectToWatchPreferences
import app.aapswear.uishared.SugarWearDisplayDefaults

/** Shell-protected display reset for the explicitly requested device verification. No sensor access. */
class SugarWearDisplayResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "app.aapswear.g7watch.DEBUG_RESET_DISPLAY") return
        SugarWearDisplayDefaults.reset(context)
        val appearance = G7AppearanceStore(context)
        appearance.setGraphHours(2)
        appearance.setTileGraphHours(2)
        G7DirectToWatchSettingsStore(context).sync()
        DirectToWatchPreferences.requestUpdates(context)
        G7CollectorTileService.requestUpdate(context)
        resultData = "Display reference restored; graphHours=2; sensor state untouched"
    }
}
