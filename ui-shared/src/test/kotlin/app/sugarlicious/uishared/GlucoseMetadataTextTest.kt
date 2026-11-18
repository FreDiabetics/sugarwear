package app.sugarlicious.uishared

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GlucoseMetadataTextTest {
    @Test
    fun `minute abbreviation includes its period in the secondary text color`() {
        assertEquals(listOf("vor", "Min."), secondaryText("vor 2 Min."))
    }

    @Test
    fun `delta decimal stays with the primary value`() {
        assertEquals(listOf("mg/dL"), secondaryText("+2.5 mg/dL"))
    }

    private fun secondaryText(value: String): List<String> {
        val styled = glucoseMetadataText(value) as Spanned
        return styled.getSpans(0, styled.length, ForegroundColorSpan::class.java).map { span ->
            assertEquals(0xFFB5B5B5.toInt(), span.foregroundColor)
            styled.subSequence(styled.getSpanStart(span), styled.getSpanEnd(span)).toString()
        }
    }
}
