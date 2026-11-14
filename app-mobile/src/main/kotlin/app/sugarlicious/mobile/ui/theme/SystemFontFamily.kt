package app.sugarlicious.mobile.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.sugarlicious.model.SystemTypographyTokens

/** Compose resolves normal/bold from the same device family as bitmap and View rendering. */
val SystemFontFamily = systemFontFamilyForDevice()

internal fun systemFontFamilyForDevice(manufacturer: String = Build.MANUFACTURER): FontFamily {
    val family = SystemTypographyTokens.fontFamily(manufacturer)
    if (family != "sec") return FontFamily.Default

    fun systemFont(weight: FontWeight) =
        Font(
            familyName = DeviceFontFamilyName(family),
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    return FontFamily(systemFont(FontWeight.Normal), systemFont(FontWeight.Medium), systemFont(FontWeight.Bold))
}

private fun TextStyle.withSystemFont(): TextStyle =
    copy(
        fontFamily = SystemFontFamily,
        fontWeight = if ((fontWeight?.weight ?: 400) >= 600) FontWeight.Bold else fontWeight,
    )

/** Preserve Material's sizes and spacing while replacing only font family and emphasis. */
val SystemMaterialTypography =
    Typography().let { source ->
        source.copy(
            displayLarge = source.displayLarge.withSystemFont(),
            displayMedium = source.displayMedium.withSystemFont(),
            displaySmall = source.displaySmall.withSystemFont(),
            headlineLarge = source.headlineLarge.withSystemFont(),
            headlineMedium = source.headlineMedium.withSystemFont(),
            headlineSmall = source.headlineSmall.withSystemFont(),
            titleLarge = source.titleLarge.withSystemFont(),
            titleMedium = source.titleMedium.withSystemFont(),
            titleSmall = source.titleSmall.withSystemFont(),
            bodyLarge = source.bodyLarge.withSystemFont(),
            bodyMedium = source.bodyMedium.withSystemFont(),
            bodySmall = source.bodySmall.withSystemFont(),
            labelLarge = source.labelLarge.withSystemFont(),
            labelMedium = source.labelMedium.withSystemFont(),
            labelSmall = source.labelSmall.withSystemFont(),
        )
    }
