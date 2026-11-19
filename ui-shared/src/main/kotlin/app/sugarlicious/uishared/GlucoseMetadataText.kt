package app.sugarlicious.uishared

import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.ImageView
import android.widget.TextView

private val metadataLabel = Regex("[A-Za-z/]+\\.?")

/** Matches the widget hierarchy while retaining real, system-scaled text. */
fun glucoseMetadataText(text: String): CharSequence =
    SpannableStringBuilder(text).apply {
        metadataLabel.findAll(text).forEach { match ->
            setSpan(ForegroundColorSpan(0xFFB5B5B5.toInt()), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(.82f), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

/** Both children are centered in the row. Align the icon with the visible numeral height. */
fun ImageView.centerOnGlucoseMetadata(text: TextView) {
    val numeral = Rect()
    text.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        val layout = text.layout ?: return@addOnLayoutChangeListener
        if (layout.lineCount == 0) return@addOnLayoutChangeListener

        text.paint.getTextBounds("0", 0, 1, numeral)
        val baseline = (layout.getLineBaseline(0) + layout.getLineBaseline(layout.lineCount - 1)) / 2f
        val glyphCenter = text.totalPaddingTop + baseline + numeral.exactCenterY()
        translationY = glyphCenter - text.height / 2f
    }
}
