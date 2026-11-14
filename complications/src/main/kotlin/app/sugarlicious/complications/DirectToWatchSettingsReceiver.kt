package app.sugarlicious.complications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.sugarlicious.protocol.DirectToWatchSettingsContract

class DirectToWatchSettingsReceiver : BroadcastReceiver() {
    @SuppressLint("ApplySharedPref") // Updates must be durable before providers are invalidated below.
    @Suppress("DEPRECATION") // Bundle is a typed SharedPreferences compatibility envelope.
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DirectToWatchSettingsContract.ACTION_APPLY) return
        val values = intent.getBundleExtra(DirectToWatchSettingsContract.EXTRA_VALUES) ?: Bundle.EMPTY
        val preferences = context.getSharedPreferences(DirectToWatchPreferences.NAME, Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .apply {
                values.keySet().forEach { key ->
                    when (val value = values.get(key)) {
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is Boolean -> putBoolean(key, value)
                        is String -> putString(key, value)
                    }
                }
            }.commit()
        DirectToWatchPreferences.requestUpdates(context)
    }
}
