package app.sugarlicious.model

/** Display-only boundary state. Raw CGM values remain untouched in storage and transport. */
enum class CgmBoundaryDisplay(
    val label: String,
) {
    LOW("NIEDRIG"),
    HIGH("HOCH"),
}

fun cgmBoundaryDisplay(valueMgDl: Double?): CgmBoundaryDisplay? =
    when {
        valueMgDl == null || !valueMgDl.isFinite() -> null
        valueMgDl < 40.0 -> CgmBoundaryDisplay.LOW
        valueMgDl > 400.0 -> CgmBoundaryDisplay.HIGH
        else -> null
    }
