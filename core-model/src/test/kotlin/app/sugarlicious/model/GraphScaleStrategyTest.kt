package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphScaleStrategyTest {
    @Test
    fun `static linear keeps identical value at identical height across viewports and new data`() {
        val session = GraphScaleSession()
        val first =
            session.resolve(
                axis = GraphAxis.COB,
                mode = CgmGraphScaleMode.STATIC,
                seedValues = listOf(0.0, 20.0, 40.0),
                visibleValues = listOf(0.0, 20.0),
                fallbackBounds = GraphBounds(0.0, 1.0),
                minimumSpan = 1.0,
            )
        val afterPanAndNewData =
            session.resolve(
                axis = GraphAxis.COB,
                mode = CgmGraphScaleMode.STATIC,
                seedValues = listOf(0.0, 20.0, 40.0, 120.0),
                visibleValues = listOf(40.0, 120.0),
                fallbackBounds = GraphBounds(0.0, 1.0),
                minimumSpan = 1.0,
            )

        assertEquals(first.bounds, afterPanAndNewData.bounds)
        assertEquals(first.ratio(20.0), afterPanAndNewData.ratio(20.0), 0.0)
    }

    @Test
    fun `static logarithmic keeps bounds while supporting zero and negative metabolic values`() {
        val session = GraphScaleSession()
        val first =
            session.resolve(
                axis = GraphAxis.IOB,
                mode = CgmGraphScaleMode.LOGARITHMIC,
                seedValues = listOf(-0.5, 0.0, 1.0, 4.0),
                visibleValues = listOf(-0.5, 0.0, 1.0),
                fallbackBounds = GraphBounds(-1.0, 1.0),
                minimumSpan = 0.1,
            )
        val afterPan =
            session.resolve(
                axis = GraphAxis.IOB,
                mode = CgmGraphScaleMode.LOGARITHMIC,
                seedValues = listOf(-0.5, 0.0, 1.0, 4.0),
                visibleValues = listOf(1.0, 4.0),
                fallbackBounds = GraphBounds(-1.0, 1.0),
                minimumSpan = 0.1,
            )

        assertEquals(first.bounds, afterPan.bounds)
        assertTrue(first.ratio(-0.5).isFinite())
        assertTrue(first.ratio(0.0).isFinite())
        assertEquals(0.0, first.inverseRatio(first.ratio(0.0)), 0.000001)
    }

    @Test
    fun `dynamic linear and logarithmic modes recompute bounds from visible viewport`() {
        listOf(CgmGraphScaleMode.DYNAMIC, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC).forEach { mode ->
            val session = GraphScaleSession()
            val lowViewport =
                session.resolve(
                    axis = GraphAxis.COB,
                    mode = mode,
                    seedValues = listOf(0.0, 20.0, 80.0),
                    visibleValues = listOf(0.0, 20.0),
                    fallbackBounds = GraphBounds(0.0, 1.0),
                    minimumSpan = 1.0,
                )
            val highViewport =
                session.resolve(
                    axis = GraphAxis.COB,
                    mode = mode,
                    seedValues = listOf(0.0, 20.0, 80.0),
                    visibleValues = listOf(0.0, 80.0),
                    fallbackBounds = GraphBounds(0.0, 1.0),
                    minimumSpan = 1.0,
                )

            assertNotEquals(lowViewport.bounds, highViewport.bounds, mode.name)
            assertNotEquals(lowViewport.ratio(20.0), highViewport.ratio(20.0), mode.name)
        }
    }

    @Test
    fun `positive logarithmic CGM uses true logarithmic transform and inverse`() {
        val scale =
            GraphAxisScale(
                mode = CgmGraphScaleMode.LOGARITHMIC,
                bounds = GraphBounds(40.0, 400.0),
                logarithmicDomain = LogarithmicDomain.POSITIVE,
            )
        val value = kotlin.math.sqrt(40.0 * 400.0)
        assertEquals(0.5, scale.ratio(value), 0.000001)
        assertEquals(value, scale.inverseRatio(0.5), 0.000001)
    }
}
