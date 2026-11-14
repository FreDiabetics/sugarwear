package app.sugarlicious.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.uishared.TrendDrawableResources

/** Optional local cap used by compact complication previews without changing larger app visuals. */
internal val LocalSugarliciousTrendArrowMaxSize = staticCompositionLocalOf<Dp?> { null }

/** Canonical Sugarlicious trend visual. All phone previews use the same vector and geometry. */
@Composable
internal fun SugarliciousTrendIndicator(
    trend: Trend,
    modifier: Modifier = Modifier,
    color: Color = SugarliciousColors.TextPrimary,
    arrowSize: Dp = 25.dp,
    style: TrendArrowStyle = MobileTrendArrowAppearance.style,
) {
    val spec = TrendVisuals.spec(trend) ?: return
    val effectiveArrowSize = LocalSugarliciousTrendArrowMaxSize.current?.let { minOf(arrowSize, it) } ?: arrowSize
    val renderStyle = style.renderSpec()
    // Callers already resolve surface/component size overrides into arrowSize.
    val height = effectiveArrowSize
    val width = height * spec.aspectRatio
    Box(modifier = modifier.size(width = width, height = height), contentAlignment = Alignment.Center) {
        val drawable = TrendDrawableResources.forAsset(spec.asset)
        if (renderStyle.outlineThicknessDp > 0f) {
            val offset = renderStyle.outlineThicknessDp.dp
            listOf(-offset to 0.dp, offset to 0.dp, 0.dp to -offset, 0.dp to offset).forEach { (x, y) ->
                Image(
                    painterResource(drawable),
                    null,
                    Modifier.size(width, height).offset(x, y),
                    colorFilter = ColorFilter.tint(Color(renderStyle.outlineColor)),
                )
            }
        }
        Image(painterResource(drawable), null, Modifier.size(width, height), colorFilter = ColorFilter.tint(Color(renderStyle.fillColor)))
    }
}
