package app.sugarlicious.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import android.graphics.Color as AndroidColor

internal fun shouldOutlineSugarliciousIcon(
    isLight: Boolean,
    colored: Boolean,
): Boolean = false

/** Shared colored-icon renderer. Outlines are never inferred from the active color mode. */
@Composable
internal fun SugarliciousIcon(
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    colored: Boolean = true,
) {
    val outline = shouldOutlineSugarliciousIcon(SugarliciousColors.palette.isLight, colored)
    val foregroundFilter = tint?.let(ColorFilter::tint)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (outline) {
            val outlineFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.68f))
            listOf(
                -0.65.dp to 0.dp,
                0.65.dp to 0.dp,
                0.dp to -0.65.dp,
                0.dp to 0.65.dp,
                -0.46.dp to -0.46.dp,
                0.46.dp to -0.46.dp,
                -0.46.dp to 0.46.dp,
                0.46.dp to 0.46.dp,
            ).forEach { (x, y) ->
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().offset(x = x, y = y),
                    colorFilter = outlineFilter,
                    alignment = Alignment.Center,
                )
            }
        }
        Image(
            painter = painterResource(drawableRes),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            colorFilter = foregroundFilter,
        )
    }
}

/** Classic-View counterpart used by Settings and dialogs. */
internal fun sugarliciousIconView(
    context: Context,
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    tintArgb: Int? = null,
    colored: Boolean = true,
): View =
    FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        this.contentDescription = contentDescription
        tooltipText = contentDescription
        importantForAccessibility =
            if (contentDescription == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES

        fun image(
            tint: Int?,
            x: Float = 0f,
            y: Float = 0f,
            description: String? = null,
        ) = ImageView(context).apply {
            setImageResource(drawableRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = tint?.let(ColorStateList::valueOf)
            translationX = x
            translationY = y
            this.contentDescription = description
            importantForAccessibility =
                if (description == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        if (shouldOutlineSugarliciousIcon(SugarliciousColors.palette.isLight, colored)) {
            val offset = context.resources.displayMetrics.density * 0.65f
            val outline = AndroidColor.argb(174, 0, 0, 0)
            listOf(
                -offset to 0f,
                offset to 0f,
                0f to -offset,
                0f to offset,
                -offset * 0.7f to -offset * 0.7f,
                offset * 0.7f to -offset * 0.7f,
                -offset * 0.7f to offset * 0.7f,
                offset * 0.7f to offset * 0.7f,
            ).forEach { (x, y) ->
                addView(
                    image(outline, x, y),
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
                )
            }
        }
        addView(
            image(tintArgb, description = contentDescription),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
    }
