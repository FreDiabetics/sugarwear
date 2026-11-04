package app.sugarwear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.sugarlicious.complications.DirectToWatchAmbientClockComplication
import app.sugarlicious.complications.DirectToWatchClockComplication
import app.sugarlicious.complications.DirectToWatchHandsComplication
import app.sugarlicious.complications.DirectToWatchPreferences
import app.sugarlicious.complications.SugarWearComplicationUpdates
import app.sugarlicious.complications.VigilHands

/** Refresh bitmap clocks from system time events while the foreground collector owns the runtime. */
internal class CgmVigilClockUpdates(
    private val context: Context,
    private val refreshComplications: () -> Unit = { SugarWearComplicationUpdates.refreshActive(context) },
    private val requestUpdate: (Class<*>) -> Unit = { provider ->
        if (provider == DirectToWatchHandsComplication::class.java) {
            VigilHands.requestUpdate(context)
        } else {
            DirectToWatchPreferences.requestClockUpdate(context, provider)
        }
    },
) {
    private var registered = false
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action in actions) refresh()
            }
        }

    fun start() {
        if (registered) return
        context.registerReceiver(receiver, IntentFilter().apply { actions.forEach(::addAction) }, Context.RECEIVER_NOT_EXPORTED)
        registered = true
        refresh(includeHands = true)
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
    }

    private fun refresh(includeHands: Boolean = false) {
        refreshComplications()
        requestUpdate(DirectToWatchClockComplication::class.java)
        requestUpdate(DirectToWatchAmbientClockComplication::class.java)
        if (includeHands) requestUpdate(DirectToWatchHandsComplication::class.java)
    }

    private companion object {
        val actions = setOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    }
}
