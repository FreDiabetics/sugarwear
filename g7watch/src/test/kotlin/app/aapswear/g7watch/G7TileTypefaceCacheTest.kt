package app.aapswear.g7watch

import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class G7TileTypefaceCacheTest {
    @Test fun `reuses each weight and reloads after configuration change`() {
        val loads = mutableListOf<Boolean>()
        val cache =
            G7TileTypefaceCache { bold ->
                loads += bold
                if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        repeat(4) { assertSame(Typeface.DEFAULT, cache.get(false)) }
        repeat(4) { assertSame(Typeface.DEFAULT_BOLD, cache.get(true)) }
        assertEquals(listOf(false, true), loads)
        cache.clear()
        cache.get(false)
        assertEquals(listOf(false, true, false), loads)
    }
}
