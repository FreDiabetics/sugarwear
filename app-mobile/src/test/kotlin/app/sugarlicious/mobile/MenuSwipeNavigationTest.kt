package app.sugarlicious.mobile

import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuSwipeNavigationTest {
    @Test
    fun `horizontal swipes move through dashboard screens without wrapping`() {
        assertEquals(
            DashboardScreen.WATCH,
            menuSwipeTarget(DashboardScreen.OVERVIEW, -120f, 8f, 72f),
        )
        assertEquals(
            DashboardScreen.SETTINGS,
            menuSwipeTarget(DashboardScreen.WATCH, -120f, 8f, 72f),
        )
        assertEquals(
            DashboardScreen.OVERVIEW,
            menuSwipeTarget(DashboardScreen.WATCH, 120f, 8f, 72f),
        )
        assertEquals(
            DashboardScreen.WATCH,
            menuSwipeTarget(DashboardScreen.SETTINGS, 120f, 8f, 72f),
        )
        assertNull(menuSwipeTarget(DashboardScreen.OVERVIEW, 120f, 8f, 72f))
        assertNull(menuSwipeTarget(DashboardScreen.SETTINGS, -120f, 8f, 72f))
    }

    @Test
    fun `short or mostly vertical gestures do not navigate`() {
        assertNull(menuSwipeTarget(DashboardScreen.WATCH, 60f, 0f, 72f))
        assertNull(menuSwipeTarget(DashboardScreen.WATCH, -110f, 100f, 72f))
    }

    @Test
    fun `cgm specific color controls hide with cgm graph`() {
        assertFalse(
            colorRoleVisible(
                SugarliciousColorRole.CGM_DOT_IN_RANGE,
                showCgmGraph = false,
                showMetabolicGraph = false,
            ),
        )
        assertFalse(
            colorRoleVisible(
                SugarliciousColorRole.RANGE_IN_RANGE,
                showCgmGraph = false,
                showMetabolicGraph = false,
            ),
        )
        assertTrue(
            colorRoleVisible(
                SugarliciousColorRole.PROGRESS_IN_RANGE,
                showCgmGraph = false,
                showMetabolicGraph = false,
            ),
        )
        assertTrue(
            colorRoleVisible(
                SugarliciousColorRole.GRAPH_BACKGROUND,
                showCgmGraph = false,
                showMetabolicGraph = true,
            ),
        )
        assertFalse(
            colorRoleVisible(
                SugarliciousColorRole.GRAPH_BACKGROUND,
                showCgmGraph = false,
                showMetabolicGraph = false,
            ),
        )
    }
}
