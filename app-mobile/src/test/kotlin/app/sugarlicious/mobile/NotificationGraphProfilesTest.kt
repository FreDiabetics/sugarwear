package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.model.AppearanceMode
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationGraphProfilesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

    @Test
    fun `fresh install uses approved notification graph colors`() {
        preferences.edit().clear().commit()

        val dark = SugarliciousColorStore.load(preferences, AppearanceMode.DARK)
        val light = SugarliciousColorStore.load(preferences, AppearanceMode.LIGHT)

        assertEquals(0xFF282828.toInt(), notificationGraphColor(preferences, dark, SugarliciousColorRole.RANGE_IN_RANGE))
        assertEquals(Color.WHITE, notificationGraphColor(preferences, light, SugarliciousColorRole.GRAPH_BACKGROUND))
        assertEquals(Color.BLACK, notificationGraphColor(preferences, light, SugarliciousColorRole.CGM_DOT_IN_RANGE))
        assertEquals(0xFF747474.toInt(), notificationGraphColor(preferences, light, SugarliciousColorRole.GRAPH_DIVIDER))
        assertEquals(0xFFF0F0F0.toInt(), notificationGraphColor(preferences, light, SugarliciousColorRole.TARGET_VALUE))
    }

    @Test
    fun `notification graph advances a fixed reading with the wall clock`() {
        val measuredAt = 2_000_000_000_000L
        val initial = NotificationGraphRenderer.notificationGraphWindow(measuredAt, 3)
        val later = NotificationGraphRenderer.notificationGraphWindow(measuredAt + 60_000L, 3)

        assertTrue(later.xFraction(measuredAt) < initial.xFraction(measuredAt))
        assertEquals(1f, initial.xFraction(measuredAt), 0.0001f)
    }

    @Test
    fun `notification graph positions delayed backfill by measurement time`() {
        val now = 2_000_000_000_000L
        val window: GraphTimeWindow = NotificationGraphRenderer.notificationGraphWindow(now, 3)
        val measuredAt = now - 45 * 60_000L
        val receivedAt = now

        assertTrue(window.xFraction(measuredAt) < window.xFraction(receivedAt))
    }

    @Test
    fun `remote views preserve bitmap aspect ratio instead of stretching`() {
        val collapsed =
            LayoutInflater
                .from(context)
                .inflate(R.layout.notification_sugarlicious_collapsed, null)
                .findViewById<ImageView>(R.id.notification_graph)
        val expanded =
            LayoutInflater
                .from(context)
                .inflate(R.layout.notification_sugarlicious_expanded, null)
                .findViewById<ImageView>(R.id.notification_graph)

        assertEquals(ImageView.ScaleType.FIT_CENTER, collapsed.scaleType)
        assertEquals(ImageView.ScaleType.FIT_CENTER, expanded.scaleType)
        assertEquals(704, NotificationGraphProfile.COLLAPSED.bitmapWidth)
        assertEquals(192, NotificationGraphProfile.COLLAPSED.bitmapHeight)
        assertEquals(720, NotificationGraphProfile.EXPANDED.bitmapWidth)
        assertEquals(296, NotificationGraphProfile.EXPANDED.bitmapHeight)
        assertEquals(
            NotificationGraphProfile.COLLAPSED.displayWidthDp / NotificationGraphProfile.COLLAPSED.displayHeightDp,
            NotificationGraphProfile.COLLAPSED.bitmapWidth.toFloat() / NotificationGraphProfile.COLLAPSED.bitmapHeight,
            0.0001f,
        )
        assertEquals(
            NotificationGraphProfile.EXPANDED.displayWidthDp / NotificationGraphProfile.EXPANDED.displayHeightDp,
            NotificationGraphProfile.EXPANDED.bitmapWidth.toFloat() / NotificationGraphProfile.EXPANDED.bitmapHeight,
            0.0001f,
        )
    }

    @Test
    fun `migration snapshots previous collapsed notification look and separates expanded defaults`() {
        preferences
            .edit()
            .clear()
            .putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS, 2.7f)
            .putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED, false)
            .putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH, 1.2f)
            .putFloat("cgm.dotRadiusDp", 5.5f)
            .putBoolean("cgm.dotOutlineEnabled", true)
            .putFloat("cgm.dotOutlineWidthDp", 2.6f)
            .commit()

        val collapsed = NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED)
        val expanded = NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.EXPANDED)

        assertEquals(2.7f, collapsed.cgmRadiusDp, 0.0001f)
        assertFalse(collapsed.cgmOutlineEnabled)
        assertEquals(1.2f, collapsed.cgmOutlineWidthDp, 0.0001f)
        assertEquals(3.2f, expanded.cgmRadiusDp, 0.0001f)
        assertFalse(expanded.cgmOutlineEnabled)
        assertEquals(1.2f, expanded.cgmOutlineWidthDp, 0.0001f)
        assertTrue(preferences.getBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED, false))

        preferences
            .edit()
            .putFloat("cgm.dotRadiusDp", 1.5f)
            .putBoolean("cgm.dotOutlineEnabled", true)
            .putFloat("cgm.dotOutlineWidthDp", 0.25f)
            .commit()

        val unchanged = NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED)
        assertEquals(collapsed, unchanged)
    }

    @Test
    fun `collapsed and expanded styles remain independent and copy is one time`() {
        preferences.edit().clear().commit()
        NotificationGraphDotStyleStore.resetProfiles(preferences)
        val collapsed = NotificationGraphDotStyle(1.8f, true, 0.5f)
        val expanded = NotificationGraphDotStyle(5.4f, false, 2.2f)

        NotificationGraphDotStyleStore.save(preferences, NotificationGraphProfile.COLLAPSED, collapsed)
        NotificationGraphDotStyleStore.save(preferences, NotificationGraphProfile.EXPANDED, expanded)

        assertEquals(collapsed, NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED))
        assertEquals(expanded, NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.EXPANDED))

        NotificationGraphDotStyleStore.copyCollapsedToExpanded(preferences)
        assertEquals(collapsed, NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.EXPANDED))

        val changedExpanded = collapsed.copy(cgmRadiusDp = 4.8f)
        NotificationGraphDotStyleStore.save(preferences, NotificationGraphProfile.EXPANDED, changedExpanded)
        assertEquals(collapsed, NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED))
        assertEquals(changedExpanded, NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.EXPANDED))
    }

    @Test
    fun `notification dot style bounds are clamped`() {
        preferences.edit().clear().commit()
        NotificationGraphDotStyleStore.resetProfiles(preferences)

        NotificationGraphDotStyleStore.save(
            preferences,
            NotificationGraphProfile.COLLAPSED,
            NotificationGraphDotStyle(99f, true, 99f),
        )
        var style = NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED)
        assertEquals(6f, style.cgmRadiusDp, 0.0001f)
        assertEquals(3f, style.cgmOutlineWidthDp, 0.0001f)

        NotificationGraphDotStyleStore.save(
            preferences,
            NotificationGraphProfile.COLLAPSED,
            NotificationGraphDotStyle(-10f, true, -10f),
        )
        style = NotificationGraphDotStyleStore.read(preferences, NotificationGraphProfile.COLLAPSED)
        assertEquals(1.5f, style.cgmRadiusDp, 0.0001f)
        assertEquals(0.25f, style.cgmOutlineWidthDp, 0.0001f)
    }

    @Test
    fun `configured graph background continues underneath translucent right scale lane`() {
        val now = System.currentTimeMillis()
        val background = Color.rgb(63, 21, 117)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .putInt("notification.color.override.${SugarliciousColorRole.GRAPH_BACKGROUND.preferenceKey}", background)
            .putInt("notification.graph.scale_lane_opacity_percent", 30)
            .commit()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucoseHistory = listOf(GlucoseSample(120.0, now - 30 * 60_000L)),
                target = TargetState(80.0, 160.0),
            )

        listOf(
            NotificationGraphRenderer.renderCollapsed(context, state, preferences),
            NotificationGraphRenderer.renderExpanded(context, state, preferences),
        ).forEach { bitmap ->
            val plotPixel = bitmap.getPixel(bitmap.width / 2, 20)
            val lanePixel = bitmap.getPixel(bitmap.width - 20, 20)
            assertEquals(255, Color.alpha(plotPixel))
            assertEquals("scale lane must have the real graph background beneath it", 255, Color.alpha(lanePixel))
        }
    }

    @Test
    fun `collapsed and expanded rendered dots stay circular and concentric in light and dark mode`() {
        val outlineColor = Color.rgb(29, 211, 231)
        val now = System.currentTimeMillis()
        val state =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucoseHistory = listOf(GlucoseSample(120.0, now - 60 * 60_000L)),
                target = TargetState(80.0, 160.0),
            )

        listOf("LIGHT", "DARK").forEach { mode ->
            val dotColor = if (mode == "LIGHT") Color.BLACK else Color.rgb(231, 37, 191)
            val modePrefix = "notification.color.${mode.lowercase()}."
            preferences
                .edit()
                .clear()
                .putString("themeMode", mode)
                .putInt("$modePrefix${SugarliciousColorRole.CGM_DOT_IN_RANGE.preferenceKey}", dotColor)
                .putInt("$modePrefix${SugarliciousColorRole.GRAPH_CURRENT_OUTLINE.preferenceKey}", outlineColor)
                .commit()
            NotificationGraphDotStyleStore.resetProfiles(preferences)
            NotificationGraphDotStyleStore.save(
                preferences,
                NotificationGraphProfile.COLLAPSED,
                NotificationGraphDotStyle(2.4f, true, 1.0f),
            )
            NotificationGraphDotStyleStore.save(
                preferences,
                NotificationGraphProfile.EXPANDED,
                NotificationGraphDotStyle(3.2f, true, 1.2f),
            )

            listOf(
                NotificationGraphRenderer.renderCollapsed(context, state, preferences),
                NotificationGraphRenderer.renderExpanded(context, state, preferences),
            ).forEach { bitmap ->
                val dot = boundsForColor(bitmap, dotColor)
                val outline = boundsForColor(bitmap, outlineColor)
                assertTrue("dot color missing in $mode", dot != null)
                assertTrue("outline color missing in $mode", outline != null)
                dot!!
                outline!!
                assertTrue("dot=${dot.width}x${dot.height}", abs(dot.width - dot.height) <= 2)
                assertTrue("outline=${outline.width}x${outline.height}", abs(outline.width - outline.height) <= 2)
                assertTrue("centerX", abs(dot.centerX - outline.centerX) <= 1.0f)
                assertTrue("centerY", abs(dot.centerY - outline.centerY) <= 1.0f)
            }
        }
    }

    private data class PixelBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    private fun boundsForColor(
        bitmap: Bitmap,
        color: Int,
    ): PixelBounds? {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == color) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right >= left && bottom >= top) PixelBounds(left, top, right, bottom) else null
    }
}
