package app.sugarlicious.complications

/** Optional host-provided semantic color; no dependency from providers back to the Wear UI. */
interface ComplicationGlucoseColorSource {
    fun outOfRangeGlucoseColor(valueMgDl: Double): Int?
}
