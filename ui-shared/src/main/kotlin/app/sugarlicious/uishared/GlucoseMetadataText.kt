package app.sugarlicious.uishared

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan

/** Matches the widget hierarchy while retaining real, system-scaled text. */
fun glucoseMetadataText(text: String): CharSequence =
    SpannableStringBuilder(text).apply {
        Regex("[A-Za-z/]+|vor|Min\\.").findAll(text).forEach { match ->
            setSpan(ForegroundColorSpan(0xFFB5B5B5.toInt()), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(.82f), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
