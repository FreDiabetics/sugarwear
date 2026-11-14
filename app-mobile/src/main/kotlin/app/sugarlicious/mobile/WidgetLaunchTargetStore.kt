package app.sugarlicious.mobile

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit

internal data class WidgetLaunchTarget(
    val packageName: String,
    val label: String,
)

internal object WidgetLaunchTargetStore {
    private const val PREFERENCES = "dashboard_ui"
    private const val KEY = "widget.launch.package"

    private val knownTargets =
        listOf(
            WidgetLaunchTarget("app.sugarlicious", "Sugarlicious"),
            WidgetLaunchTarget("info.nightscout.androidaps", "AndroidAPS"),
            WidgetLaunchTarget("info.nightscout.androidaps.dev", "AndroidAPS Dev"),
            WidgetLaunchTarget("info.nightscout.aaps", "AndroidAPS"),
            WidgetLaunchTarget("com.dexcom.g7", "Dexcom G7"),
        )

    fun available(context: Context): List<WidgetLaunchTarget> =
        knownTargets
            .filter { it.packageName == context.packageName || context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .distinctBy(WidgetLaunchTarget::packageName)

    fun selected(context: Context): WidgetLaunchTarget {
        val packageName = legacySelectedPackage(context)
        return available(context).firstOrNull { it.packageName == packageName }
            ?: WidgetLaunchTarget(context.packageName, "Sugarlicious")
    }

    internal fun legacySelectedPackage(context: Context): String =
        context
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY, context.packageName)
            .orEmpty()
            .ifBlank { context.packageName }

    fun select(
        context: Context,
        target: WidgetLaunchTarget,
    ) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(KEY, target.packageName)
        }
    }

    fun launchComponent(
        context: Context,
        packageName: String? = null,
    ): ComponentName =
        context.packageManager
            .getLaunchIntentForPackage(packageName ?: selected(context).packageName)
            ?.resolveActivity(context.packageManager)
            ?: ComponentName(context, MainActivity::class.java)
}
