package app.sugarwear

import android.util.Patterns
import org.json.JSONObject

internal data class DexcomShareInvitation(
    val contactName: String,
    val email: String,
    val displayName: String,
) {
    fun valid(): Boolean = contactName.isNotBlank() && displayName.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    // Share's graph permission and initial Follow alert settings match the xDrip+ invitation format.
    fun payload(): JSONObject =
        JSONObject()
            .put("DisplayName", displayName)
            .put("Permissions", 1)
            .put(
                "AlertSettings",
                JSONObject()
                    .put("HighAlert", alert(1, 200, 401, "PT1H", "PT2H", "High.wav"))
                    .put("LowAlert", alert(2, 39, 70, "PT30M", "PT2H", "Low.wav"))
                    .put("FixedLowAlert", alert(3, 39, 55, "PT0M", "PT30M", "UrgentLow.wav"))
                    .put("NoDataAlert", alert(4, 39, 401, "PT1H", "PT0M", "NoData.wav")),
            )

    private fun alert(type: Int, min: Int, max: Int, delay: String, repeat: String, sound: String): JSONObject =
        JSONObject()
            .put("AlertType", type)
            .put("MinValue", min)
            .put("MaxValue", max)
            .put("AlarmDelay", delay)
            .put("RealarmDelay", repeat)
            .put("Sound", sound)
            .put("IsEnabled", false)
}

internal enum class DexcomShareInviteResult {
    REQUESTED,
    CONTACT_EXISTS,
    ALREADY_INVITED,
}
