package app.sugarlicious.uishared

import app.sugarlicious.model.TrendVisualAsset

/** One canonical resource mapping for every Android Sugarlicious renderer. */
object TrendDrawableResources {
    fun forAsset(asset: TrendVisualAsset): Int =
        when (asset) {
            TrendVisualAsset.DOUBLE_UP -> R.drawable.ic_trend_doubleup
            TrendVisualAsset.UP -> R.drawable.ic_trend_up
            TrendVisualAsset.FORTY_FIVE_UP -> R.drawable.ic_trend_fortyfiveup
            TrendVisualAsset.FLAT -> R.drawable.ic_trend_flat
            TrendVisualAsset.FORTY_FIVE_DOWN -> R.drawable.ic_trend_fortyfivedown
            TrendVisualAsset.DOWN -> R.drawable.ic_trend_down
            TrendVisualAsset.DOUBLE_DOWN -> R.drawable.ic_trend_doubledown
        }
}
