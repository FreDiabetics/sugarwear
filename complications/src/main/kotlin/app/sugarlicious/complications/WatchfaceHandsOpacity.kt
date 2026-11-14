package app.sugarlicious.complications

import android.app.Activity
import android.content.Context
import android.os.Bundle

internal object WatchfaceHandsOpacity {
    private const val PREFERENCES = "native_watchface_hands"

    fun text(context: Context, profile: WatchfaceGraphProfile): String =
        if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(profile.name, false)) "dim" else "full"

    fun toggle(context: Context, profile: WatchfaceGraphProfile) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().putBoolean(profile.name, !preferences.getBoolean(profile.name, false)).apply()
        ActiveProviderUpdates.request(
            context,
            if (profile == WatchfaceGraphProfile.ORBIT) NativeOrbitGraphComplication::class.java else NativeApexGraphComplication::class.java,
        )
        // Existing native instances may retain their previous graph component
        // during the in-place migration; refresh it only while it is active.
        ActiveProviderUpdates.requestKnownActive(
            context,
            if (profile == WatchfaceGraphProfile.ORBIT) OrbitGlucoseGraphComplication::class.java else GlucoseGraphComplication::class.java,
        )
    }
}

/** An explicit, center-only WFF launch; no visible window or background work. */
class WatchfaceHandsOpacityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alias = intent.component?.className.orEmpty()
        val profile =
            when {
                alias.endsWith("OrbitHandsOpacityActivity") -> WatchfaceGraphProfile.ORBIT
                alias.endsWith("ApexHandsOpacityActivity") -> WatchfaceGraphProfile.APEX
                else -> null
            }
        if (profile != null) WatchfaceHandsOpacity.toggle(this, profile)
        finish()
    }
}
