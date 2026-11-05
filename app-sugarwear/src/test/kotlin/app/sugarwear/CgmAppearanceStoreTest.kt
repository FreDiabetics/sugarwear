package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CgmAppearanceStoreTest {
    @Test fun `fresh installation defaults to dark mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        assertEquals(AppearanceMode.DARK, CgmAppearanceStore(context).activeMode())
        assertEquals(AppearanceMode.DARK, CgmAppearanceStore(context).load().mode)
    }

    @Test fun `colors persist alpha and reset to defaults`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)
        val custom = 0x12345678

        store.save(CgmAppearanceRole.GRAPH_TARGET_AREA, custom)
        assertEquals(custom, CgmAppearanceStore(context).load().argb(CgmAppearanceRole.GRAPH_TARGET_AREA))

        store.reset(CgmAppearanceRole.GRAPH_TARGET_AREA)
        assertEquals(
            CgmAppearanceRole.GRAPH_TARGET_AREA.defaultArgb,
            CgmAppearanceStore(context).load().argb(CgmAppearanceRole.GRAPH_TARGET_AREA),
        )
    }

    @Test fun `graph period cycles and survives new store instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)
        assertEquals(2, store.graphHours())
        assertEquals(3, store.nextGraphHours())
        assertEquals(3, CgmAppearanceStore(context).graphHours())
        assertEquals(6, store.nextGraphHours())
        assertEquals(12, store.nextGraphHours())
        assertEquals(24, store.nextGraphHours())
        assertEquals(1, store.nextGraphHours())
        assertEquals(2, store.nextGraphHours())
    }

    @Test fun `light and dark profiles are independent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)
        store.save(AppearanceMode.LIGHT, CgmAppearanceRole.MENU_BACKGROUND, 0xFFEEDDCC.toInt())
        store.save(AppearanceMode.DARK, CgmAppearanceRole.MENU_BACKGROUND, 0xFF112233.toInt())
        assertEquals(0xFFEEDDCC.toInt(), store.load(AppearanceMode.LIGHT).argb(CgmAppearanceRole.MENU_BACKGROUND))
        assertEquals(0xFF112233.toInt(), store.load(AppearanceMode.DARK).argb(CgmAppearanceRole.MENU_BACKGROUND))
    }

    @Test fun `delta and unit color is independent between SugarWear light and dark`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)
        store.save(AppearanceMode.LIGHT, CgmAppearanceRole.GLUCOSE_DELTA, 0xAA102030.toInt())
        store.save(AppearanceMode.DARK, CgmAppearanceRole.GLUCOSE_DELTA, 0xCC405060.toInt())

        assertEquals(0xAA102030.toInt(), store.load(AppearanceMode.LIGHT).argb(CgmAppearanceRole.GLUCOSE_DELTA))
        assertEquals(0xCC405060.toInt(), store.load(AppearanceMode.DARK).argb(CgmAppearanceRole.GLUCOSE_DELTA))
    }

    @Test fun `explicit dark mode survives activity and store recreation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmAppearanceStore(context).setActiveMode(AppearanceMode.DARK)
        assertEquals(AppearanceMode.DARK, CgmAppearanceStore(context).activeMode())
        assertEquals(AppearanceMode.DARK, CgmAppearanceStore(context).load().mode)
    }

    @Test fun `glucose and trend scales persist independently through 200 percent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CgmAppearanceStore(context).apply {
            setGlucoseScalePercent(200)
            setTrendScalePercent(125)
        }
        assertEquals(200, CgmAppearanceStore(context).glucoseScalePercent())
        assertEquals(125, CgmAppearanceStore(context).trendScalePercent())
    }

    @Test fun `tile glucose appearance persists independently from in app appearance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)
        store.setGlucoseScalePercent(110)
        store.setTileGlucoseScalePercent(175)
        store.saveTileGlucoseColor(AppearanceMode.DARK, CgmAppearanceRole.GLUCOSE_IN_RANGE, 0xFF12AB34.toInt())
        store.saveTileTrendArrowStyle(
            AppearanceMode.DARK,
            store.tileTrendArrowStyle(AppearanceMode.DARK).copy(sizePercent = 140, fillColor = 0xFFABCDEF.toInt()),
        )

        val recreated = CgmAppearanceStore(context)
        assertEquals(110, recreated.glucoseScalePercent())
        assertEquals(175, recreated.tileGlucoseScalePercent())
        assertEquals(0xFF12AB34.toInt(), recreated.tileGlucosePalette(AppearanceMode.DARK).argb(CgmAppearanceRole.GLUCOSE_IN_RANGE))
        assertEquals(140, recreated.tileTrendArrowStyle(AppearanceMode.DARK).sizePercent)
        assertEquals(0xFFABCDEF.toInt(), recreated.tileTrendArrowStyle(AppearanceMode.DARK).fillColor)
    }

    @Test fun `in app graph time axis setting reaches the renderer style and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences("g7_appearance", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val store = CgmAppearanceStore(context)

        assertTrue(store.inAppGraphStyle().timeAxisEnabled)
        store.setInAppGraphTimeAxisEnabled(false)
        assertFalse(CgmAppearanceStore(context).inAppGraphStyle().timeAxisEnabled)
        store.setInAppGraphTimeAxisEnabled(true)
        assertTrue(CgmAppearanceStore(context).inAppGraphStyle().timeAxisEnabled)
    }
}
