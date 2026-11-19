package app.sugarlicious.uishared

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.sugarlicious.model.SystemTypographyTokens

/** Device-owned fonts only. All app, bitmap, notification and Wear renderers share this policy. */
object SystemTypography {
    val preferredFontFamily: String get() = SystemTypographyTokens.fontFamily(Build.MANUFACTURER)

    // Typeface.create caches internally; retain no app-owned font across system font changes.
    fun typeface(emphasized: Boolean): Typeface = create(emphasized)

    private fun create(emphasized: Boolean): Typeface {
        val base = Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(base, SystemTypographyTokens.weight(emphasized), false)
        } else {
            Typeface.create(base, if (emphasized) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun apply(paint: Paint, emphasized: Boolean) {
        paint.typeface = typeface(emphasized)
        // A DeviceVendor BOLD alias may still pin the variable face to wght=400.
        paint.setFontVariationSettings("'wght' ${SystemTypographyTokens.weight(emphasized)}")
    }

    fun apply(view: TextView, emphasized: Boolean) {
        view.typeface = typeface(emphasized)
        view.fontVariationSettings = "'wght' ${SystemTypographyTokens.weight(emphasized)}"
    }

    /** Standard parcelable spans also work in system-hosted RemoteViews. */
    fun emphasizedText(text: CharSequence): CharSequence =
        if (text.isEmpty()) {
            text
        } else {
            SpannableString(text).apply {
                setSpan(TypefaceSpan(preferredFontFamily), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

    /** Normalize XML bold declarations once, immediately after inflation. */
    fun applyDeclaredWeights(view: View) {
        if (view is TextView && view.typeface?.isBold == true) apply(view, true)
        if (view is ViewGroup) repeat(view.childCount) { applyDeclaredWeights(view.getChildAt(it)) }
    }
}
