package app.sugarlicious.mobile

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.SugarliciousComplicationIds
import app.sugarlicious.model.TrendArrowStyleOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComplicationAppearanceSettingsTest {
    @Test
    fun `catalog preserves readable cell widths on narrow phones and wider displays`() {
        assertEquals(1, complicationCatalogColumns(260f))
        assertEquals(2, complicationCatalogColumns(350f))
        assertEquals(3, complicationCatalogColumns(600f))
    }

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `provider override persists independently and reset restores system default`() {
        val id = SugarliciousComplicationIds.GLUCOSE_TREND
        val override =
            TrendArrowStyleOverride(
                outlineEnabled = false,
                outlineColor = 0xFF123456.toInt(),
                outlineThicknessDp = 1.25f,
                sizePercent = 200,
                alpha = 0.7f,
            )
        val configured = ComplicationAppearanceSettings(200, 12, -8, override)
        ComplicationAppearanceSettingsStore.save(context, id, configured)
        assertEquals(configured, ComplicationAppearanceSettingsStore.load(context, id))
        assertNull(ComplicationAppearanceSettingsStore.load(context, SugarliciousComplicationIds.TREND_ONLY).trendScalePercent)

        ComplicationAppearanceSettingsStore.save(context, id, ComplicationAppearanceSettings())
        assertNull(ComplicationAppearanceSettingsStore.load(context, id).trendScalePercent)
        assertEquals(0, ComplicationAppearanceSettingsStore.load(context, id).trendOffsetXPercent)
        assertEquals(true, ComplicationAppearanceSettingsStore.load(context, id).trendStyleOverride.isEmpty)
    }
}
