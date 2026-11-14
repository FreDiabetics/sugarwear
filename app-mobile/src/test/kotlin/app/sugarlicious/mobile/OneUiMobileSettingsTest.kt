package app.sugarlicious.mobile

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OneUiMobileSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `DeviceVendor switch thumb stays inside its taller rounded track`() {
        val toggle = mobileSettingsSwitch(context, true) {}
        val density = context.resources.displayMetrics.density
        val width = (64 * density).toInt()
        val height = (48 * density).toInt()
        toggle.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        toggle.layout(0, 0, width, height)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            toggle.draw(android.graphics.Canvas(bitmap))
            val thumb = toggle.thumbDrawable
            val visibleThumb = if (thumb is android.graphics.drawable.InsetDrawable) thumb.drawable!! else thumb
            assertEquals((16 * density).toInt(), visibleThumb.bounds.width())
            assertEquals((16 * density).toInt(), visibleThumb.bounds.height())
            assertEquals((20 * density).toInt(), toggle.trackDrawable.bounds.height())
            assertEquals((2 * density).toInt(), visibleThumb.bounds.top - toggle.trackDrawable.bounds.top)
            assertEquals((2 * density).toInt(), toggle.trackDrawable.bounds.right - visibleThumb.bounds.right)
            toggle.isChecked = false
            toggle.jumpDrawablesToCurrentState()
            toggle.draw(android.graphics.Canvas(bitmap))
            assertEquals((2 * density).toInt(), visibleThumb.bounds.left - toggle.trackDrawable.bounds.left)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    @Config(sdk = [26])
    fun `native controls and app theme remain available at the minimum supported API`() {
        val themed = android.view.ContextThemeWrapper(context, R.style.AppTheme)
        assertEquals(26, android.os.Build.VERSION.SDK_INT)
        mobileSettingsControlContext(themed, dark = true)
        mobileSettingsControlContext(themed, dark = false)
        val toggle = mobileSettingsSwitch(themed, false) {}
        toggle.toggle()
        assertTrue(toggle.isChecked)
    }

    @Test
    fun `selected native controls use the shared Sugarlicious green seed`() {
        listOf(true, false).forEach { dark ->
            val themed = mobileSettingsControlContext(context, dark)
            val attributes = themed.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlActivated))
            try {
                assertEquals(
                    app.sugarlicious.uishared.SugarliciousAccentPalette.SEED,
                    attributes.getColor(0, 0),
                )
            } finally {
                attributes.recycle()
            }
        }
    }

    @Test
    fun `non touch user adjustments persist immediately but touch dragging commits once at the end`() {
        var draft = 0
        val saved = mutableListOf<Int>()
        val interaction = SettingsSliderInteraction({ draft = it }, { saved.add(draft) })
        interaction.changed(20, fromUser = false)
        assertTrue(saved.isEmpty())
        interaction.changed(30, fromUser = true)
        assertEquals(listOf(30), saved)
        interaction.started()
        interaction.changed(40, fromUser = true)
        interaction.changed(50, fromUser = true)
        assertEquals(listOf(30), saved)
        interaction.stopped()
        assertEquals(listOf(30, 50), saved)
    }

    @Test
    fun `native controls follow the selected app mode without changing device configuration`() {
        val originalMode = context.resources.configuration.uiMode
        val dark = mobileSettingsControlContext(context, dark = true)
        val light = mobileSettingsControlContext(context, dark = false)
        val mask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES, dark.resources.configuration.uiMode and mask)
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO, light.resources.configuration.uiMode and mask)
        assertEquals(originalMode, context.resources.configuration.uiMode)
    }

    @Test
    fun `binding a native switch does not write settings and toggling sends the selected value`() {
        val changes = mutableListOf<Boolean>()
        val toggle = mobileSettingsSwitch(context, true, changes::add)
        assertTrue(toggle.isChecked)
        assertTrue(changes.isEmpty())
        toggle.toggle()
        assertEquals(listOf(false), changes)
        assertEquals(1f, toggle.scaleX)
        assertEquals(1f, toggle.scaleY)
    }

    @Test
    fun `settings page navigation returns from a category before leaving settings`() {
        val navigation =
            MobileSettingsViewModel(
                object : MobileSettingsStore {
                    override fun write(key: String, value: Any) = Unit

                    override fun saveThresholds(value: app.sugarlicious.model.CgmThresholds) = Unit

                    override suspend fun syncHealth(schedule: Boolean): HealthConnectSyncResult? = null

                    override suspend fun sync(therapy: app.sugarlicious.model.TherapyDisplayState) = Unit

                    override suspend fun export(uri: android.net.Uri) = Unit

                    override suspend fun restore(uri: android.net.Uri) = SettingsRestoreResult(0, 0)
                },
            )
        assertEquals(null, navigation.uiState.value.category)
        navigation.open("general", "Allgemein")
        assertEquals("Allgemein", navigation.uiState.value.title)
        assertTrue(navigation.back())
        assertEquals(null, navigation.uiState.value.category)
        assertEquals(false, navigation.back())
    }
}
