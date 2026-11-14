package app.sugarlicious.mobile

import org.junit.Assert.*
import org.junit.Test

class MobileSettingsScrollHeaderTest {
    @Test fun `submenu compact title fades in place during pull expansion`() {
        val initial = settingsSubmenuHeaderState(0f, 0f, 160f, 88f)
        val pulled = settingsSubmenuHeaderState(0f, 40f, 160f, 128f)
        assertEquals(0f, initial.compactTitleOffsetDp, 0f)
        assertEquals(0f, pulled.compactTitleOffsetDp, 0f)
        assertTrue(pulled.compactTitleAlpha < initial.compactTitleAlpha)
        assertEquals(40f, pulled.rowOffsetDp, 0f)
    }

    @Test fun `expanded title only fades when its complete fixed text box fits`() {
        assertEquals(0f, settingsSubmenuHeaderState(0f, 70f, 160f, 134f, 52f, 80f).expandedTitleAlpha, 0f)
        listOf(96f, 110f, 160f).forEach { expansion ->
            val state = settingsSubmenuHeaderState(0f, expansion, 160f, 64f + expansion, 52f, 80f)
            assertTrue(state.expandedTitleAlpha > 0f)
            assertTrue(state.expandedTitleTopDp + 80f <= state.rowOffsetDp)
        }
    }

    @Test fun `back and compact title row follow the card with a small constant gap`() {
        listOf(0f, 32f, 96f, 160f).forEach { expansion ->
            val state = settingsSubmenuHeaderState(0f, expansion, 160f, 64f + expansion)
            assertEquals(expansion, state.rowOffsetDp, 0f)
            assertEquals(12f, 64f + expansion - (52f + state.rowOffsetDp), 0f)
            assertEquals(0f, state.backCircleAlpha, 0f)
        }
        val titleGone = settingsSubmenuHeaderState(6f, 0f, 160f, 64f)
        assertEquals(0f, titleGone.compactTitleAlpha, 0f)
        assertEquals(0f, titleGone.backCircleAlpha, 0f)
        assertEquals(1f, settingsSubmenuHeaderState(8f, 0f, 160f, 64f).backCircleAlpha, 0f)
    }

    @Test fun `return gesture crossfades back and settles at an exact header anchor`() {
        val expanded = settingsSubmenuHeaderState(0f, 160f, 160f, 248f)
        val returning = settingsSubmenuHeaderState(0f, 32f, 160f, 120f)
        val compact = settingsSubmenuHeaderState(0f, 0f, 160f, 88f)
        assertTrue(returning.expandedTitleAlpha < expanded.expandedTitleAlpha)
        assertTrue(returning.compactTitleAlpha > expanded.compactTitleAlpha)
        assertEquals(1f, compact.compactTitleAlpha, 0f)
        assertEquals(32f, settingsSubmenuExpansionTarget(32f, 160f), 0f)
        assertEquals(100f, settingsSubmenuExpansionTarget(100f, 160f), 0f)
        assertEquals(140f, settingsSubmenuExpansionTarget(140f, 160f), 0f)
        assertEquals(0f, settingsSubmenuExpansionTarget(6f, 160f), 0f)
        assertEquals(160f, settingsSubmenuExpansionTarget(154f, 160f), 0f)
    }

    @Test fun `submenu title fades before back surface meets the first card`() {
        val opened = settingsSubmenuHeaderState(0f, 0f, 160f, 88f)
        assertEquals(1f, opened.compactTitleAlpha, 0f)
        assertEquals(0f, opened.expandedTitleAlpha, 0f)
        assertEquals(0f, opened.backCircleAlpha, 0f)
        val titleGone = settingsSubmenuHeaderState(20f, 0f, 160f, 88f)
        assertEquals(0f, titleGone.compactTitleAlpha, 0f)
        assertEquals(0f, titleGone.backCircleAlpha, 0f)
        val beforeTouch = settingsSubmenuHeaderState(32f, 0f, 160f, 88f)
        assertEquals(1f, beforeTouch.backCircleAlpha, 0f)
        assertTrue(88f - 32f > 52f)
    }

    @Test fun `pull down crossfades compact and expanded titles with a small stagger`() {
        val crossing = settingsSubmenuHeaderState(0f, 48f, 160f, 136f)
        assertTrue(crossing.compactTitleAlpha < .3f)
        assertTrue(crossing.expandedTitleAlpha in 0f..0.2f)
        val expanded = settingsSubmenuHeaderState(0f, 160f, 160f, 248f)
        assertEquals(0f, expanded.compactTitleAlpha, 0f)
        assertEquals(1f, expanded.expandedTitleAlpha, 0f)
        assertEquals(0f, expanded.backCircleAlpha, 0f)
        assertEquals(0f, settingsSubmenuHeaderState(160f, 160f, 160f, 248f).expandedTitleAlpha, 0f)
    }

    @Test fun `large font compact title reserves back area and height for two lines`() {
        val layout = settingsCompactTitleLayout(320f, 96f, 58f)
        assertEquals(58f, layout.startDp, 0f)
        assertEquals(246f, layout.widthDp, 0f)
        assertEquals(112f, layout.heightDp, 0f)
        val compact = settingsScrollHeaderState(138f, 250f, 250f, layout.heightDp)
        assertEquals(69f, compact.titleOffsetDp, .01f)
        assertEquals(1f, compact.titleAlpha, 0f)
    }

    @Test fun `title shrinks above first card then scrolls out while back circle waits for content`() {
        val expanded = settingsScrollHeaderState(0f, 250f, 250f)
        val middle = settingsScrollHeaderState(80f, 250f, 250f)
        val collapsed = settingsScrollHeaderState(250f, 250f, 250f)
        assertEquals(1f, expanded.titleAlpha, 0f)
        assertEquals(0f, expanded.backCircleAlpha, 0f)
        assertEquals(1f, middle.titleAlpha, 0f)
        assertTrue(middle.titleOffsetDp > 0)
        assertEquals(0f, middle.backCircleAlpha, 0f)
        assertEquals(0f, collapsed.titleAlpha, 0f)
        assertEquals(1f, collapsed.backCircleAlpha, 0f)
        val compact = settingsScrollHeaderState(194f, 250f, 250f)
        assertEquals(20f / 34f, compact.titleScale, .001f)
        assertEquals(97f, compact.titleOffsetDp, .001f)
        assertEquals(1f, compact.titleAlpha, 0f)
        assertEquals(expanded, settingsScrollHeaderState(-10f, 250f, 250f))
    }
}
