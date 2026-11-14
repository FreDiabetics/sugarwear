package app.sugarlicious.model

import kotlinx.serialization.Serializable
import java.util.Locale

enum class TherapyBridgeDisplayField {
    IOB,
    BOLUS_IOB,
    BASAL_IOB,
    COB,
    FUTURE_CARBS,
    BASAL,
    TEMP_BASAL,
    INSULIN_ACTIVITY,
    DIA,
    RESERVOIR,
    BATTERY,
}

/** AndroidAPS-owned unit and visible precision contract for non-G7 values. */
@Serializable
data class TherapyBridgeDisplaySemantics(
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val insulinDigits: Int = 2,
    val carbDigits: Int = 0,
    val basalDigits: Int = 2,
    val activityDigits: Int = 4,
    val diaDigits: Int = 1,
    val reservoirDigits: Int = 0,
    val batteryDigits: Int = 0,
)

/** Single formatter shared by Mobile, Wear, widgets and complications. */
object TherapyBridgeDisplayFormatter {
    fun format(
        field: TherapyBridgeDisplayField,
        value: Double?,
        semantics: TherapyBridgeDisplaySemantics = TherapyBridgeDisplaySemantics(),
        includeUnit: Boolean = true,
    ): String {
        val finite = value?.takeIf(Double::isFinite) ?: return "—"
        val (digits, unit) =
            when (field) {
                TherapyBridgeDisplayField.IOB, TherapyBridgeDisplayField.BOLUS_IOB, TherapyBridgeDisplayField.BASAL_IOB -> semantics.insulinDigits to "U"
                TherapyBridgeDisplayField.COB, TherapyBridgeDisplayField.FUTURE_CARBS -> semantics.carbDigits to "g"
                TherapyBridgeDisplayField.BASAL, TherapyBridgeDisplayField.TEMP_BASAL -> semantics.basalDigits to "U/h"
                TherapyBridgeDisplayField.INSULIN_ACTIVITY -> semantics.activityDigits to "U/min"
                TherapyBridgeDisplayField.DIA -> semantics.diaDigits to "h"
                TherapyBridgeDisplayField.RESERVOIR -> semantics.reservoirDigits to "U"
                TherapyBridgeDisplayField.BATTERY -> semantics.batteryDigits to "%"
            }
        val formatted = String.format(Locale.US, "%.${digits.coerceIn(0, 8)}f", finite)
        return if (includeUnit) formatted + unit else formatted
    }
}
