package app.sugarwear

import android.util.Patterns
import org.json.JSONObject

internal data class DexcomShareInvitation(
    val contactName: String,
    val email: String,
    val displayName: String,
    val sharing: DexcomShareDataSharing = DexcomShareDataSharing.TREND_GRAPH,
    val alerts: Set<DexcomShareFollowerAlert> = emptySet(),
) {
    fun valid(): Boolean = contactName.isNotBlank() && displayName.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun payload(): JSONObject =
        JSONObject()
            .put("DisplayName", displayName)
            .put("Permissions", sharing.permission)
            .put(
                "AlertSettings",
                JSONObject().apply {
                    DexcomShareFollowerAlert.entries.forEach { put(it.field, it.payload(it in alerts)) }
                },
            )
}

internal enum class DexcomShareDataSharing(
    val permission: Int,
    val label: String,
) {
    CURRENT_VALUE(0, "Aktueller Sensorwert"),
    TREND_GRAPH(1, "Sensorwert und Trenddiagramm"),
}

// Share invitation defaults match xDrip+. These settings apply to Follow independently of watch alarms.
internal enum class DexcomShareFollowerAlert(
    val field: String,
    val label: String,
    private val type: Int,
    private val min: Int,
    private val max: Int,
    private val delay: String,
    private val repeat: String,
    private val sound: String,
) {
    URGENT_LOW("FixedLowAlert", "Niedriger Wert (dringend)", 3, 39, 55, "PT0M", "PT30M", "UrgentLow.wav"),
    LOW("LowAlert", "Niedrig", 2, 39, 70, "PT30M", "PT2H", "Low.wav"),
    HIGH("HighAlert", "Hoch", 1, 200, 401, "PT1H", "PT2H", "High.wav"),
    NO_DATA("NoDataAlert", "Keine Daten", 4, 39, 401, "PT1H", "PT0M", "NoData.wav"),
    ;

    fun payload(enabled: Boolean): JSONObject =
        JSONObject()
            .put("AlertType", type)
            .put("MinValue", min)
            .put("MaxValue", max)
            .put("AlarmDelay", delay)
            .put("RealarmDelay", repeat)
            .put("Sound", sound)
            .put("IsEnabled", enabled)
}

internal data class DexcomShareFollower(
    val contactId: String,
    val subscriptionId: String?,
    val name: String,
    val email: String?,
    val accepted: Boolean?,
    val enabled: Boolean?,
    val sharing: DexcomShareDataSharing?,
    val alerts: Map<DexcomShareFollowerAlert, Boolean>,
)

internal enum class DexcomShareInviteResult {
    REQUESTED,
    CONTACT_EXISTS,
    ALREADY_INVITED,
}
