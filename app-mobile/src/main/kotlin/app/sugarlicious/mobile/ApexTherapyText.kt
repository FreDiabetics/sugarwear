package app.sugarlicious.mobile

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val apexTherapyValue = Regex("(-?\\d+(?:\\.\\d{1,2})?)([Ug])")

/** ApeX-only presentation; unknown provider strings must pass through unchanged. */
internal fun apexTherapyText(value: String, locale: Locale = Locale.getDefault()): Pair<String, String>? {
    val match = apexTherapyValue.matchEntire(value) ?: return null
    val number = match.groupValues[1]
    if (number.length > 9) return null
    val unit = match.groupValues[2]
    return (if (unit == "U") DecimalFormat("#.#", DecimalFormatSymbols(locale)).format(number.toDouble()) else number) to unit
}
