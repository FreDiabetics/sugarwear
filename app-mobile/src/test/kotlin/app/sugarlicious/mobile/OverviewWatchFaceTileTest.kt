package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class OverviewWatchFaceTileTest {
    @Test
    fun `even a long swipe advances exactly one watchface`() {
        assertEquals(201, carouselTargetPage(200, -2_000f, 400))
        assertEquals(199, carouselTargetPage(200, 2_000f, 400))
    }

    @Test
    fun `short movement stays on current watchface and bounds are respected`() {
        assertEquals(200, carouselTargetPage(200, 12f, 400))
        assertEquals(0, carouselTargetPage(0, 500f, 400))
        assertEquals(399, carouselTargetPage(399, -500f, 400))
    }

    @Test
    fun `only the centered carousel face is visible`() {
        assertEquals(1f, carouselPageVisibility(0f))
        assertEquals(1f, carouselPageVisibility(0.5f))
        assertEquals(0f, carouselPageVisibility(0.5001f))
        assertEquals(0f, carouselPageVisibility(1f))
    }

    @Test
    fun `wall clock helper still calculates physical clock angles`() {
        val utc = TimeZone.getTimeZone("UTC")
        val calendar =
            Calendar.getInstance(utc).apply {
                set(2026, Calendar.AUGUST, 14, 10, 10, 30)
                set(Calendar.MILLISECOND, 0)
            }

        val angles = watchPreviewHandAngles(calendar.timeInMillis, utc)

        assertEquals(305.25f, angles.hour, 0.001f)
        assertEquals(63f, angles.minute, 0.001f)
        assertEquals(180f, angles.second, 0.001f)
    }

    @Test
    fun `analog hand rotations stay correct at cardinal and mixed times`() {
        val utc = TimeZone.getTimeZone("UTC")

        data class Case(
            val hour: Int,
            val minute: Int,
            val second: Int,
            val hourAngle: Float,
            val minuteAngle: Float,
            val secondAngle: Float,
        )
        listOf(
            Case(0, 0, 0, 0f, 0f, 0f),
            Case(3, 0, 0, 90f, 0f, 0f),
            Case(6, 0, 0, 180f, 0f, 0f),
            Case(9, 0, 0, 270f, 0f, 0f),
            Case(12, 30, 0, 15f, 180f, 0f),
            Case(18, 45, 0, 202.5f, 270f, 0f),
        ).forEach { case ->
            val calendar =
                Calendar.getInstance(utc).apply {
                    set(2026, Calendar.JANUARY, 1, case.hour, case.minute, case.second)
                    set(Calendar.MILLISECOND, 0)
                }
            val angles = watchPreviewHandAngles(calendar.timeInMillis, utc)
            assertEquals(case.hourAngle, angles.hour, 0.001f)
            assertEquals(case.minuteAngle, angles.minute, 0.001f)
            assertEquals(case.secondAngle, angles.second, 0.001f)
        }
    }

    @Test
    fun `mobile watch previews use the fixed requested hand positions`() {
        assertEquals(303.5f, fixedWatchPreviewHandAngles.hour, 0.001f)
        assertEquals(42f, fixedWatchPreviewHandAngles.minute, 0.001f)
        assertEquals(192f, fixedWatchPreviewHandAngles.second, 0.001f)
    }

    @Test
    fun `watch menu lists every installable Sugarlicious face as a card`() {
        assertEquals(sugarliciousWatchFaceNames, sugarliciousWatchFaceCards.map { it.name })
        assertEquals(sugarliciousWatchFaceNames.size, sugarliciousWatchFaceCards.size)
        assertTrue(sugarliciousWatchFaceCards.all { it.slots > 0 && "AOD" in it.features })
    }

    @Test
    fun `multi type complications expose one selectable provider per type`() {
        val variants = SugarliciousComplicationCatalog.flatMap { it.variants }

        assertEquals(40, variants.size)
        assertEquals(40, variants.map { it.id }.distinct().size)
        assertTrue(
            SugarliciousComplicationCatalog.first { it.name == "TIR" }.variants.any {
                it.type == ComplicationVariantType.RANGED_VALUE
            },
        )
        assertEquals(
            listOf(
                ComplicationVariantType.SHORT_TEXT,
                ComplicationVariantType.LONG_TEXT,
                ComplicationVariantType.RANGED_VALUE,
            ),
            SugarliciousComplicationCatalog.first { it.name == "Glukose + Trend" }.variants.map { it.type },
        )
    }
}
