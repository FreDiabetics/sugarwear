package app.aapswear.g7watch

import android.graphics.Color
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.*
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders.*
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders.*

private fun warningIcon(scope: ProtoLayoutScope, size: Float): Image =
    Image
        .Builder(scope)
        .setImageResource(ImageResource.Builder().setAndroidResourceByResId(AndroidImageResourceByResId.Builder().setResourceId(app.aapswear.uishared.R.drawable.ic_alert).build()).build(), "sugarwear_sensor_warning")
        .setWidth(dp(size))
        .setHeight(dp(size))
        .build()

internal fun glucoseTileWarning(scope: ProtoLayoutScope, message: String, widthDp: Float): LayoutElement =
    Column
        .Builder()
        .setWidth(dp(widthDp))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .setModifiers(Modifiers.Builder().setSemantics(Semantics.Builder().setContentDescription(message).build()).build())
        .addContent(warningIcon(scope, 36f))
        .addContent(Spacer.Builder().setHeight(dp(12f)).build())
        .addContent(
            Text
                .Builder()
                .setText(message)
                .setMaxLines(3)
                .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
                .setFontStyle(
                    FontStyle
                        .Builder()
                        .setSize(sp(18f))
                        .setColor(argb(Color.WHITE))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .build(),
                ).build(),
        ).build()

/** The icon center lies on the upper-right 45 degree radius, with a circular-display safe margin. */
internal fun graphTileWarning(scope: ProtoLayoutScope, message: String, widthDp: Float, heightDp: Float): LayoutElement {
    val radius = (minOf(widthDp, heightDp) / 2f - 28f).coerceAtLeast(0f)
    val offset = radius * .70710677f
    return Box
        .Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_LEFT)
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
        .setModifiers(
            Modifiers
                .Builder()
                .setPadding(
                    Padding
                        .Builder()
                        .setStart(dp(widthDp / 2f + offset - 18f))
                        .setTop(dp(heightDp / 2f - offset - 18f))
                        .build(),
                ).setSemantics(Semantics.Builder().setContentDescription(message).build())
                .build(),
        ).addContent(warningIcon(scope, 36f))
        .build()
}
