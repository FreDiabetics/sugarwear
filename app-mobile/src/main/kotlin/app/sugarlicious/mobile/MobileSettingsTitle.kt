package app.sugarlicious.mobile

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors

internal fun settingsTitleSplit(title: String): Int = if (title.equals("Datenquellen", ignoreCase = true)) 5 else title.offsetByCodePoints(0, title.codePointCount(0, title.length) / 2)

internal fun brandedSettingsTitle(title: String): CharSequence =
    SpannableString(title).apply {
        if (title.isNotEmpty()) setSpan(ForegroundColorSpan(SugarliciousColors.argb(SugarliciousColorRole.BRAND_GREEN)), settingsTitleSplit(title), title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

internal fun brandedSettingsTitleText(title: String): AnnotatedString =
    buildAnnotatedString {
        val split = settingsTitleSplit(title)
        append(title.substring(0, split))
        withStyle(SpanStyle(color = SugarliciousColors.BrandGreen)) { append(title.substring(split)) }
    }
