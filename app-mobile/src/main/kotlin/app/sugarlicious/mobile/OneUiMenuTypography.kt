package app.sugarlicious.mobile

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.sugarlicious.mobile.ui.theme.SystemFontFamily
import app.sugarlicious.mobile.ui.theme.SystemMaterialTypography

/** Compact settings roles using the device font and normal system font scaling. */
internal val oneUiMenuTypography =
    SystemMaterialTypography.let { base ->
        val row = TextStyle(fontFamily = SystemFontFamily, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
        val secondary = row.copy(fontSize = 13.sp, lineHeight = 18.sp)
        val title = row.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
        base.copy(
            headlineSmall = title,
            titleLarge = title,
            titleMedium = row,
            titleSmall = secondary,
            bodyLarge = row,
            bodyMedium = secondary,
            bodySmall = secondary,
            labelLarge = row.copy(fontSize = 16.sp, lineHeight = 21.sp),
            labelMedium = secondary,
            labelSmall = secondary,
        )
    }
