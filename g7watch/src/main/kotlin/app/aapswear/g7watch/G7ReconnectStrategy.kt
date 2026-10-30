package app.aapswear.g7watch

import android.content.Context
import androidx.core.content.edit

/** Developer-selectable discovery strategy; production defaults to the hardware-tested fix. */
internal object G7ReconnectStrategyStore {
    private const val PREFS = "g7_reconnect_strategy"
    private const val KEY_STRATEGY = "strategy"

    fun read(context: Context): G7ReconnectStrategy =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STRATEGY, null)
            ?.let { runCatching { G7ReconnectStrategy.valueOf(it) }.getOrNull() }
            ?: G7ReconnectStrategy.KNOWN_ADDRESS_DIRECT

    fun write(
        context: Context,
        strategy: G7ReconnectStrategy,
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STRATEGY, strategy.name)
        }
    }
}
