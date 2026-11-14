package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TherapyProgressResponsiveLayoutTest {
    private val appearance = TherapyProgressAppearance()

    @Test
    fun `wide therapy widget lays indicators out from left to right`() {
        val indicators = resolveResponsiveTherapyLayout(220f, 90f, appearance)

        assertEquals(TherapyProgressMetric.entries, indicators.map { it.metric })
        assertTrue(indicators.zipWithNext().all { (left, right) -> left.slot.centerXDp < right.slot.centerXDp })
        assertTrue(indicators.map { it.slot.centerYDp }.distinct().size == 1)
    }

    @Test
    fun `tall therapy widget lays indicators out from top to bottom`() {
        listOf(90f to 180f, 90f to 270f).forEach { (width, height) ->
            val indicators = resolveResponsiveTherapyLayout(width, height, appearance)

            assertEquals(TherapyProgressMetric.entries, indicators.map { it.metric })
            assertTrue(indicators.zipWithNext().all { (top, bottom) -> top.slot.centerYDp < bottom.slot.centerYDp })
            assertTrue(indicators.map { it.slot.centerXDp }.distinct().size == 1)
        }
    }

    @Test
    fun `therapy widget switches orientation exactly at portrait aspect`() {
        val square = resolveResponsiveTherapyLayout(100f, 100f, appearance)
        val portrait = resolveResponsiveTherapyLayout(99f, 100f, appearance)

        assertTrue(square[0].slot.centerXDp < square[1].slot.centerXDp)
        assertTrue(portrait[0].slot.centerYDp < portrait[1].slot.centerYDp)
    }

    @Test
    fun `display sized tile rings are centered and stay within circular display`() {
        listOf(180f, 212f, 228f).forEach { side ->
            (1..3).forEach { count ->
                val config = appearance.copy(enabledMetrics = TherapyProgressMetric.entries.take(count).toSet())
                val rings = resolveRoundTherapyLayout(side, config, baseDiameterDp = side * .58f)
                val center = side / 2f
                rings.forEach { item ->
                    val slot = item.slot
                    listOf(slot.leftDp to slot.topDp, slot.rightDp to slot.topDp, slot.leftDp to slot.bottomDp, slot.rightDp to slot.bottomDp).forEach { (x, y) ->
                        assertTrue(kotlin.math.hypot(x - center, y - center) <= center + .01f)
                    }
                }
                val left = rings.minOf { it.slot.leftDp }
                val right = rings.maxOf { it.slot.rightDp }
                val top = rings.minOf { it.slot.topDp }
                val bottom = rings.maxOf { it.slot.bottomDp }
                assertEquals(center, (left + right) / 2f, .01f)
                assertEquals(center, (top + bottom) / 2f, .01f)
            }
        }
    }
}
