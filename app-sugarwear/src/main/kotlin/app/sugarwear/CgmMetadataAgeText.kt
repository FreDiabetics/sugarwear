package app.sugarwear

internal fun cgmCompactAgeText(text: String): String = text.replace(Regex("\\b(?:Minuten|Minute|Min)\\b\\.?"), "Min.")
