package app.aapswear.g7watch

import app.aapswear.model.AppearanceMode
import app.aapswear.model.TrendArrowStyle
import app.aapswear.protocol.WatchGraphColors
import app.aapswear.uishared.SharedWearCgmGraphStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class G7SettingsEditMergeTest {
    @Test fun `queued graph edits preserve fields changed by an earlier command`() {
        val baseline = SharedWearCgmGraphStyle()
        val current = baseline.copy(borderEnabled = !baseline.borderEnabled)
        val edited = baseline.copy(dotRadiusDp = 4f)
        val merged = mergeG7GraphStyleEdit(baseline, edited, current)
        assertEquals(current.borderEnabled, merged.borderEnabled)
        assertEquals(4f, merged.dotRadiusDp, 0f)
    }

    @Test fun `queued color edits preserve an earlier background edit`() {
        val baseline = WatchGraphColors()
        val current = baseline.copy(graphBackground = 123)
        val edited = baseline.copy(outline = 456)
        val merged = mergeG7GraphColorEdit(baseline, edited, current)
        assertEquals(123, merged.graphBackground)
        assertEquals(456, merged.outline)
    }

    @Test fun `queued trend edits preserve an earlier outline edit`() {
        val baseline = TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt())
        val current = baseline.copy(outlineEnabled = !baseline.outlineEnabled)
        val edited = baseline.copy(sizePercent = 130)
        val merged = mergeG7TrendStyleEdit(baseline, edited, current)
        assertEquals(current.outlineEnabled, merged.outlineEnabled)
        assertEquals(130, merged.sizePercent)
    }
}
