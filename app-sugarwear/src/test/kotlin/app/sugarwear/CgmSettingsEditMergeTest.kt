package app.sugarwear

import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.TrendArrowStyle
import app.sugarlicious.protocol.WatchGraphColors
import app.sugarlicious.uishared.SharedWearCgmGraphStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class CgmSettingsEditMergeTest {
    @Test fun `queued graph edits preserve fields changed by an earlier command`() {
        val baseline = SharedWearCgmGraphStyle()
        val current = baseline.copy(borderEnabled = !baseline.borderEnabled)
        val edited = baseline.copy(dotRadiusDp = 4f)
        val merged = mergeCgmGraphStyleEdit(baseline, edited, current)
        assertEquals(current.borderEnabled, merged.borderEnabled)
        assertEquals(4f, merged.dotRadiusDp, 0f)
    }

    @Test fun `queued color edits preserve an earlier background edit`() {
        val baseline = WatchGraphColors()
        val current = baseline.copy(graphBackground = 123)
        val edited = baseline.copy(outline = 456)
        val merged = mergeCgmGraphColorEdit(baseline, edited, current)
        assertEquals(123, merged.graphBackground)
        assertEquals(456, merged.outline)
    }

    @Test fun `queued trend edits preserve an earlier outline edit`() {
        val baseline = TrendArrowStyle.defaults(AppearanceMode.DARK, 0xFFFFFFFF.toInt())
        val current = baseline.copy(outlineEnabled = !baseline.outlineEnabled)
        val edited = baseline.copy(sizePercent = 130)
        val merged = mergeCgmTrendStyleEdit(baseline, edited, current)
        assertEquals(current.outlineEnabled, merged.outlineEnabled)
        assertEquals(130, merged.sizePercent)
    }
}
