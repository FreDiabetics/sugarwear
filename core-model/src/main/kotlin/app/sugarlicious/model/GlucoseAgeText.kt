package app.sugarlicious.model

/** Shared, human-readable elapsed-reading label for Mobile and Wear surfaces. */
object GlucoseAgeText {
    fun fromAgeMinutes(age: String): String {
        val minutes = age.removeSuffix("m").toLongOrNull()?.takeIf { it >= 0 } ?: return "—"
        return when {
            minutes < 60 -> "vor $minutes Min."
            minutes < 1440 -> "vor ${minutes / 60} Std. ${minutes % 60} Min."
            minutes < 2880 -> "vor 1 Tag"
            else -> "vor ${minutes / 1440} Tagen"
        }
    }
}
