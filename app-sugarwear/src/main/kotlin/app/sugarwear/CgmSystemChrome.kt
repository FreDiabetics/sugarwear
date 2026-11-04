package app.sugarwear

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowInsetsController

internal enum class CgmSystemChromeStrategy {
    EXPLICIT_BAR_COLORS,
    EDGE_TO_EDGE_BACKGROUND,
}

internal fun cgmSystemChromeStrategy(sdkInt: Int): CgmSystemChromeStrategy =
    if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        CgmSystemChromeStrategy.EDGE_TO_EDGE_BACKGROUND
    } else {
        CgmSystemChromeStrategy.EXPLICIT_BAR_COLORS
    }

internal fun applyCgmSystemChrome(
    window: Window,
    background: Int,
) {
    window.decorView.setBackgroundColor(background)
    if (cgmSystemChromeStrategy(Build.VERSION.SDK_INT) == CgmSystemChromeStrategy.EXPLICIT_BAR_COLORS) {
        setLegacyCgmSystemBarColors(window, background)
    }
    val lightIcons = Color.luminance(background) <= 0.5f
    val appearance =
        if (lightIcons) {
            0
        } else {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        }
    val mask =
        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    window.insetsController?.setSystemBarsAppearance(appearance, mask)
}

@Suppress("DEPRECATION")
private fun setLegacyCgmSystemBarColors(
    window: Window,
    background: Int,
) {
    window.statusBarColor = background
    window.navigationBarColor = background
}
