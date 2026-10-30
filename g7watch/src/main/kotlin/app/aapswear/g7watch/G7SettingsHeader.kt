package app.aapswear.g7watch

import android.app.Activity
import android.widget.LinearLayout

internal fun Activity.g7SettingsHeader(
    title: String,
    palette: G7AppearancePalette,
): LinearLayout =
    app.aapswear.uishared.WearSettingsUi
        .header(this, title, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY)) { finish() }
        .apply {
            val inset =
                app.aapswear.uishared.WearSettingsUi
                    .dp(context, 11)
            setPadding(inset, 0, inset, 0)
        }

/** Menu chrome stays legible while the edited display palette retains its saved colors. */
internal fun G7AppearancePalette.settingsArgb(role: G7AppearanceRole): Int =
    when (role) {
        G7AppearanceRole.MENU_BACKGROUND -> 0xFF000000.toInt()
        G7AppearanceRole.MENU_SURFACE -> 0xFF202020.toInt()
        G7AppearanceRole.MENU_TEXT_PRIMARY -> 0xFFFFFFFF.toInt()
        G7AppearanceRole.MENU_TEXT_SECONDARY -> 0xFFB6B6BB.toInt()
        G7AppearanceRole.MENU_PRIMARY ->
            app.aapswear.uishared.SugarliciousAccentPalette
                .primary(mode == app.aapswear.model.AppearanceMode.DARK)
        G7AppearanceRole.MENU_BORDER -> android.graphics.Color.TRANSPARENT
        else -> argb(role)
    }
