package app.sugarlicious.uishared

import android.graphics.Paint
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.SystemTypographyTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemTypographyTest {
    @Test
    fun `native view and bitmap select the same actual bold weight`() {
        val paint = Paint()
        val view = TextView(ApplicationProvider.getApplicationContext())
        SystemTypography.apply(paint, true)
        SystemTypography.apply(view, true)
        assertEquals(700, paint.typeface.weight)
        assertEquals(700, view.typeface.weight)
        paint.textSize = view.paint.textSize
        assertEquals(paint.measureText("538"), view.paint.measureText("538"), .01f)
        SystemTypography.apply(paint, false)
        assertEquals(400, paint.typeface.weight)
    }

    @Test
    @Config(sdk = [26])
    fun `minimum supported api still renders bold without newer typeface api`() {
        val paint = Paint()
        SystemTypography.apply(paint, true)
        assertTrue(paint.typeface.isBold)
    }

    @Test
    fun `device-selected system font is retained across manufacturers`() {
        assertEquals("sans-serif", SystemTypographyTokens.fontFamily("SAMSUNG"))
        assertEquals("sans-serif", SystemTypographyTokens.fontFamily("Google"))
        assertEquals("sans-serif", SystemTypographyTokens.fontFamily(""))
    }

    @Test
    fun `notification emphasis survives parcel transport without changing content`() {
        val original = SystemTypography.emphasizedText("105 → −3")
        val parcel = android.os.Parcel.obtain()
        try {
            android.text.TextUtils.writeToParcel(original, parcel, 0)
            parcel.setDataPosition(0)
            val restored =
                android.text.TextUtils.CHAR_SEQUENCE_CREATOR
                    .createFromParcel(parcel) as android.text.Spanned
            assertEquals("105 → −3", restored.toString())
            assertEquals(android.graphics.Typeface.BOLD, restored.getSpans(0, restored.length, android.text.style.StyleSpan::class.java).single().style)
        } finally {
            parcel.recycle()
        }
    }
}
