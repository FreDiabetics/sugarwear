package app.sugarwear

import android.content.Context
import androidx.core.content.edit

/** Developer-selectable discovery strategy; production defaults to the hardware-tested fix. */
internal object CgmReconnectStrategyStore {
    private const val PREFS = "g7_reconnect_strategy"
    private const val KEY_STRATEGY = "strategy"

    fun read(context: Context): CgmReconnectStrategy =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STRATEGY, null)
            ?.let { runCatching { CgmReconnectStrategy.valueOf(it) }.getOrNull() }
            ?: CgmReconnectStrategy.KNOWN_ADDRESS_DIRECT

    fun write(
        context: Context,
        strategy: CgmReconnectStrategy,
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STRATEGY, strategy.name)
        }
    }
}
