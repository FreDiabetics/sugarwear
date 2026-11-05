package app.sugarwear

import android.app.Activity
import android.widget.LinearLayout

internal fun Activity.cgmSettingsHeader(
    title: String,
    palette: CgmAppearancePalette,
    back: () -> Unit = { finish() },
): LinearLayout =
    app.sugarlicious.uishared.WearSettingsUi
        .pageHeader(this, title, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), back)

/** Menu chrome stays legible while the edited display palette retains its saved colors. */
internal fun CgmAppearancePalette.settingsArgb(role: CgmAppearanceRole): Int =
    when (role) {
        CgmAppearanceRole.MENU_BACKGROUND -> app.sugarlicious.uishared.WearSettingsUi.MENU_BACKGROUND
        CgmAppearanceRole.MENU_SURFACE -> app.sugarlicious.uishared.WearSettingsUi.MENU_SURFACE
        CgmAppearanceRole.MENU_TEXT_PRIMARY -> app.sugarlicious.uishared.WearSettingsUi.MENU_TEXT
        CgmAppearanceRole.MENU_TEXT_SECONDARY -> app.sugarlicious.uishared.WearSettingsUi.MENU_SECONDARY
        CgmAppearanceRole.MENU_PRIMARY ->
            app.sugarlicious.uishared.SugarliciousAccentPalette
                .primary(mode == app.sugarlicious.model.AppearanceMode.DARK)
        CgmAppearanceRole.MENU_BORDER -> android.graphics.Color.TRANSPARENT
        else -> argb(role)
    }
