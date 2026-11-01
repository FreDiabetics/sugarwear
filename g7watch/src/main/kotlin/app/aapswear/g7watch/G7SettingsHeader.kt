package app.aapswear.g7watch

import android.app.Activity
import android.widget.LinearLayout

internal fun Activity.g7SettingsHeader(
    title: String,
    palette: G7AppearancePalette,
    back: () -> Unit = { finish() },
): LinearLayout =
    app.aapswear.uishared.WearSettingsUi
        .pageHeader(this, title, palette.settingsArgb(G7AppearanceRole.MENU_TEXT_PRIMARY), back)

/** Menu chrome stays legible while the edited display palette retains its saved colors. */
internal fun G7AppearancePalette.settingsArgb(role: G7AppearanceRole): Int =
    when (role) {
        G7AppearanceRole.MENU_BACKGROUND -> app.aapswear.uishared.WearSettingsUi.MENU_BACKGROUND
        G7AppearanceRole.MENU_SURFACE -> app.aapswear.uishared.WearSettingsUi.MENU_SURFACE
        G7AppearanceRole.MENU_TEXT_PRIMARY -> app.aapswear.uishared.WearSettingsUi.MENU_TEXT
        G7AppearanceRole.MENU_TEXT_SECONDARY -> app.aapswear.uishared.WearSettingsUi.MENU_SECONDARY
        G7AppearanceRole.MENU_PRIMARY ->
            app.aapswear.uishared.SugarliciousAccentPalette
                .primary(mode == app.aapswear.model.AppearanceMode.DARK)
        G7AppearanceRole.MENU_BORDER -> android.graphics.Color.TRANSPARENT
        else -> argb(role)
    }
