package app.aapswear.g7watch

import android.graphics.Color
import android.os.Build
import android.view.Window
import android.view.WindowInsetsController

internal enum class G7SystemChromeStrategy {
    EXPLICIT_BAR_COLORS,
    EDGE_TO_EDGE_BACKGROUND,
}

internal fun g7SystemChromeStrategy(sdkInt: Int): G7SystemChromeStrategy =
    if (sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        G7SystemChromeStrategy.EDGE_TO_EDGE_BACKGROUND
    } else {
        G7SystemChromeStrategy.EXPLICIT_BAR_COLORS
    }

internal fun applyG7SystemChrome(
    window: Window,
    background: Int,
) {
    window.decorView.setBackgroundColor(background)
    if (g7SystemChromeStrategy(Build.VERSION.SDK_INT) == G7SystemChromeStrategy.EXPLICIT_BAR_COLORS) {
        setLegacyG7SystemBarColors(window, background)
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
private fun setLegacyG7SystemBarColors(
    window: Window,
    background: Int,
) {
    window.statusBarColor = background
    window.navigationBarColor = background
}
