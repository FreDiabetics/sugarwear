package app.sugarlicious.complications

import app.sugarlicious.model.DailyTir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbitDailyTirTest {
    @Test
    fun `five day arc is transparent theme neutral and contains text`() {
        val bitmap = renderDailyTir((4 downTo 0).map { DailyTir(LocalDate.of(2026, 10, 23).minusDays(it.toLong()), if (it == 2) null else 89, 300_000) })
        assertEquals(660, bitmap.width)
        assertEquals(330, bitmap.height)
        assertEquals(0, bitmap.getPixel(0, 0))
        val pixels = IntArray(660 * 330)
        bitmap.getPixels(pixels, 0, 660, 0, 0, 660, 330)
        assertTrue(pixels.count { it ushr 24 > 0 } > 500)
        assertTrue(pixels.filter { it ushr 24 > 0 }.all { it and 0xFFFFFF == 0xFFFFFF })
        val report = java.io.File("build/reports/orbit-daily-tir.png")
        report.parentFile?.mkdirs()
        report.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
