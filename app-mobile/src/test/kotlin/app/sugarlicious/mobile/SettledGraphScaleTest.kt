package app.sugarlicious.mobile

import org.junit.Assert.*
import org.junit.Test

class SettledGraphScaleTest {
    private fun scale() = SettledGraphScale<Double> { a, b, f -> a + (b - a) * f }

    @Test fun `pan zoom and fling retain scale until interaction ends then animate`() {
        val scale = scale()
        assertEquals(100.0, scale.resolve(100.0, false, 0), 0.0)
        assertEquals(100.0, scale.resolve(300.0, true, 100), 0.0)
        assertEquals(100.0, scale.resolve(500.0, true, 900), 0.0)
        assertFalse(scale.animating)
        assertEquals(100.0, scale.resolve(500.0, false, 1000), 0.0)
        assertEquals(300.0, scale.resolve(500.0, false, 1210), 0.01)
        assertTrue(scale.animating)
        assertEquals(500.0, scale.resolve(500.0, false, 1420), 0.0)
        assertFalse(scale.animating)
    }

    @Test fun `new gesture freezes in flight geometry and resumes from that geometry`() {
        val scale = scale()
        scale.resolve(100.0, false, 0)
        scale.resolve(300.0, false, 100)
        assertEquals(200.0, scale.resolve(300.0, false, 310), 0.01)
        assertEquals(200.0, scale.resolve(600.0, true, 400), 0.01)
        assertEquals(200.0, scale.resolve(50.0, true, 1000), 0.01)
        assertEquals(200.0, scale.resolve(50.0, false, 1200), 0.01)
        assertEquals(50.0, scale.resolve(50.0, false, 1620), 0.01)
    }

    @Test fun `linked graph cannot end another graphs gesture`() {
        val viewport = ChartViewport(6)
        val first = Any()
        val second = Any()
        viewport.setNavigatingGesture(first, true)
        viewport.setNavigatingGesture(second, false)
        assertTrue(viewport.isNavigatingGesture)
        viewport.setNavigatingGesture(first, false)
        assertFalse(viewport.isNavigatingGesture)
    }
}
