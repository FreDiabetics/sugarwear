package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CgmGraphYScaleTest {
    @Test fun `optional gentle log reduces low band emphasis and keeps an exact inverse`() {
        val strict = CgmGraphYScale(CgmGraphScaleMode.LOGARITHMIC, 0.0, 400.0)
        val gentle = strict.copy(logarithmicOffsetMgDl = 200.0)
        assertTrue(gentle.ratio(80.0) < strict.ratio(80.0))
        for (value in listOf(0.0, 20.0, 40.0, 50.0, 80.0, 140.0, 220.0, 400.0)) {
            assertEquals(value, gentle.inverseRatio(gentle.ratio(value)), 0.000001)
        }
        val linear = gentle.copy(mode = CgmGraphScaleMode.STATIC)
        assertEquals(strict.copy(mode = CgmGraphScaleMode.STATIC).ratio(80.0), linear.ratio(80.0), 0.0)
    }

    @Test fun `static CGM scale maps real zero to its lower boundary`() {
        val scale =
            CgmGraphYScale.resolve(
                mode = CgmGraphScaleMode.STATIC,
                visibleValuesMgDl = listOf(80.0, 120.0),
                staticMinimumMgDl = 0.0,
                staticMaximumMgDl = 400.0,
            )

        assertEquals(0.0, scale.minimumMgDl, 0.0)
        assertEquals(0.0, scale.ratio(0.0), 0.0)
        assertTrue(scale.ratio(40.0) > 0.0)
    }

    @Test
    fun `all zero based CGM modes reserve only a compact band below sensor floor`() {
        CgmGraphScaleMode.entries.forEach { mode ->
            val scale = CgmGraphYScale(mode, 0.0, 400.0)

            assertEquals(0.0, scale.ratio(0.0), 0.0, mode.name)
            assertEquals(0.06, scale.ratio(40.0), 0.000001, mode.name)
            assertEquals(40.0, scale.inverseRatio(0.06), 0.000001, mode.name)
        }
    }

    @Test fun `custom logarithmic and dynamic CGM lower bounds remain unchanged`() {
        val logarithmic = CgmGraphYScale.resolve(CgmGraphScaleMode.LOGARITHMIC, listOf(80.0, 120.0), 20.0, 400.0)
        val dynamic = CgmGraphYScale.resolve(CgmGraphScaleMode.DYNAMIC, listOf(65.0, 120.0), 20.0, 400.0)

        assertEquals(20.0, logarithmic.minimumMgDl, 0.0)
        assertTrue(dynamic.minimumMgDl < 65.0)
    }

    @Test
    fun `all modes map equal boundary values to equal pixels`() {
        CgmGraphScaleMode.entries.forEach { mode ->
            val scale =
                CgmGraphYScale.resolve(
                    mode,
                    visibleValuesMgDl = listOf(80.0, 120.0, 240.0),
                    requiredValuesMgDl = listOf(80.0, 160.0),
                )
            val historyPixelY = 320.0 - scale.ratio(120.0) * 280.0
            val predictionPixelY = 320.0 - scale.ratio(120.0) * 280.0
            assertEquals(historyPixelY, predictionPixelY, 0.0, mode.name)
        }
    }

    @Test
    fun `dynamic modes include visible predictions and targets with padding`() {
        listOf(CgmGraphScaleMode.DYNAMIC, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC).forEach { mode ->
            val scale = CgmGraphYScale.resolve(mode, listOf(110.0, 260.0), requiredValuesMgDl = listOf(80.0, 160.0))
            assertTrue(scale.minimumMgDl < 80.0)
            assertTrue(scale.maximumMgDl > 260.0)
        }
    }

    @Test
    fun `logarithmic modes use a real logarithmic transform`() {
        val scale = CgmGraphYScale(CgmGraphScaleMode.LOGARITHMIC, 40.0, 400.0)
        assertEquals(0.5, scale.ratio(kotlin.math.sqrt(40.0 * 400.0)), 0.000001)
        assertEquals(kotlin.math.sqrt(40.0 * 400.0), scale.inverseRatio(0.5), 0.000001)
    }

    @Test
    fun `static CGM modes ignore visible viewport while dynamic modes respond`() {
        listOf(CgmGraphScaleMode.STATIC, CgmGraphScaleMode.LOGARITHMIC).forEach { mode ->
            val low = CgmGraphYScale.resolve(mode, listOf(80.0, 120.0), 40.0, 400.0)
            val high = CgmGraphYScale.resolve(mode, listOf(180.0, 320.0), 40.0, 400.0)
            assertEquals(low.minimumMgDl, high.minimumMgDl, 0.0, mode.name)
            assertEquals(low.maximumMgDl, high.maximumMgDl, 0.0, mode.name)
            assertEquals(low.ratio(120.0), high.ratio(120.0), 0.0, mode.name)
        }
        listOf(CgmGraphScaleMode.DYNAMIC, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC).forEach { mode ->
            val low = CgmGraphYScale.resolve(mode, listOf(80.0, 120.0), requiredValuesMgDl = listOf(80.0, 160.0))
            val high = CgmGraphYScale.resolve(mode, listOf(180.0, 320.0), requiredValuesMgDl = listOf(80.0, 160.0))
            assertTrue(low.maximumMgDl != high.maximumMgDl, mode.name)
        }
    }
}
