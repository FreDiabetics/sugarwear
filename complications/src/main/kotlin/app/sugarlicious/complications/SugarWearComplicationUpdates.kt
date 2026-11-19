package app.sugarlicious.complications

import android.content.Context

/** Standalone SugarWear's public text/ranged providers, separate from Vigil bitmap internals. */
object SugarWearComplicationUpdates {
    internal val providers: List<Class<*>> =
        listOf(
            GlucoseComplication::class.java,
            GlucoseLongTextComplication::class.java,
            GlucoseRangedValueComplication::class.java,
            GlucoseTrendComplication::class.java,
            GlucoseTrendLongTextComplication::class.java,
            GlucoseTrendRangedValueComplication::class.java,
            TrendOnlyComplication::class.java,
            GlucoseDeltaComplication::class.java,
        )

    fun request(context: Context) = request(context, ActiveProviderUpdates::request)

    internal fun request(context: Context, requestProvider: (Context, Class<*>) -> Unit) {
        if (context.packageName != "app.sugarwear") return
        providers.forEach { requestProvider(context, it) }
    }

    /** Minute ticks never probe providers that have no acknowledged active instance. */
    fun refreshActive(context: Context) {
        if (context.packageName != "app.sugarwear") return
        providers.forEach { ActiveProviderUpdates.requestKnownActive(context, it) }
    }
}
