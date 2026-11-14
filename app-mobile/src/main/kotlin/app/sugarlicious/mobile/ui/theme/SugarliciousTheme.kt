package app.sugarlicious.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun SugarliciousTheme(content: @Composable () -> Unit) {
    val palette = SugarliciousColors.palette
    val menuAccent = mobileMenuAccent(dark = !palette.isLight)
    val menuOnAccent = mobileMenuOnAccent(dark = !palette.isLight)

    val colorScheme =
        if (palette.isLight) {
            lightColorScheme(
                primary = menuAccent,
                onPrimary = menuOnAccent,
                primaryContainer = SugarliciousColors.SurfaceHigh,
                onPrimaryContainer = SugarliciousColors.TextPrimary,
                secondary = SugarliciousColors.Secondary,
                onSecondary = SugarliciousColors.OnSecondary,
                secondaryContainer = SugarliciousColors.SurfaceHigh,
                onSecondaryContainer = SugarliciousColors.TextPrimary,
                background = SugarliciousColors.Background,
                onBackground = SugarliciousColors.TextPrimary,
                surface = SugarliciousColors.Surface,
                onSurface = SugarliciousColors.TextPrimary,
                surfaceVariant = SugarliciousColors.SurfaceHigh,
                onSurfaceVariant = SugarliciousColors.TextSecondary,
                outline = SugarliciousColors.Border,
                error = SugarliciousColors.Red,
                onError = SugarliciousColors.OnPrimary,
            )
        } else {
            darkColorScheme(
                primary = menuAccent,
                onPrimary = menuOnAccent,
                primaryContainer = SugarliciousColors.SurfaceHigh,
                onPrimaryContainer = SugarliciousColors.TextPrimary,
                secondary = SugarliciousColors.Secondary,
                onSecondary = SugarliciousColors.OnSecondary,
                secondaryContainer = SugarliciousColors.SurfaceHigh,
                onSecondaryContainer = SugarliciousColors.TextPrimary,
                background = SugarliciousColors.Background,
                onBackground = SugarliciousColors.TextPrimary,
                surface = SugarliciousColors.Surface,
                onSurface = SugarliciousColors.TextPrimary,
                surfaceVariant = SugarliciousColors.SurfaceHigh,
                onSurfaceVariant = SugarliciousColors.TextSecondary,
                outline = SugarliciousColors.Border,
                error = SugarliciousColors.Red,
                onError = SugarliciousColors.OnPrimary,
            )
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SugarliciousTypography,
        shapes = SugarliciousShapes,
        content = content,
    )
}
