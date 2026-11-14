package app.sugarlicious.mobile.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.sugarlicious.uishared.SugarliciousAccentPalette

@Suppress("UNUSED_PARAMETER")
internal fun mobileMenuAccentArgb(context: Context, dark: Boolean): Int = SugarliciousAccentPalette.primary(dark)

@Suppress("UNUSED_PARAMETER")
internal fun mobileMenuOnAccentArgb(context: Context, dark: Boolean): Int = SugarliciousAccentPalette.onPrimary

@Composable
internal fun mobileMenuAccent(dark: Boolean): Color = Color(SugarliciousAccentPalette.primary(dark))

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun mobileMenuOnAccent(dark: Boolean): Color = Color(SugarliciousAccentPalette.onPrimary)
