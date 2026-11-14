package app.sugarlicious.mobile

import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal data class LiveNotificationStatus(
    val device: String,
    val deviceVendorNowBar: String,
    val androidLiveUpdates: String,
    val promotedNotifications: String,
    val promotableCharacteristics: String,
    val deviceVendorExtras: String,
    val lastPostAttempt: String,
    val exactFreshnessAlarm: String,
    val hardwareValidation: String,
)

internal fun liveNotificationStatus(
    liveRequested: Boolean,
    sdkInt: Int,
    manufacturer: String,
    canPostPromoted: Boolean?,
    hasPromotableCharacteristics: Boolean? = null,
    deviceVendorExtrasApplied: Boolean = false,
    lastPostAttemptAt: Long = 0L,
    canScheduleExactAlarms: Boolean? = null,
): LiveNotificationStatus {
    val deviceVendor = manufacturer.equals("samsung", ignoreCase = true)
    return LiveNotificationStatus(
        device = if (deviceVendor) "Samsung" else manufacturer.ifBlank { "Unbekannt" },
        deviceVendorNowBar =
            when {
                !deviceVendor -> "nicht verfügbar"
                liveRequested -> "angefordert"
                else -> "aus"
            },
        androidLiveUpdates = if (sdkInt >= 36) "unterstützt" else "nicht unterstützt",
        promotedNotifications =
            when (canPostPromoted) {
                true -> "erlaubt"
                false -> "nicht erlaubt"
                null -> "nicht verfügbar"
            },
        promotableCharacteristics =
            when (hasPromotableCharacteristics) {
                true -> "ja"
                false -> "nein"
                null -> "nicht geprüft"
            },
        deviceVendorExtras = if (deviceVendorExtrasApplied) "angewendet" else "nicht angewendet",
        lastPostAttempt = if (lastPostAttemptAt > 0L) "durchgeführt" else "noch nicht erfolgt",
        exactFreshnessAlarm =
            when (canScheduleExactAlarms) {
                true -> "erlaubt"
                false -> "nicht erlaubt"
                null -> "nicht erforderlich"
            },
        hardwareValidation = "nicht durchgeführt",
    )
}

internal fun liveNotificationStatus(
    context: Context,
    liveRequested: Boolean,
): LiveNotificationStatus =
    context.getSystemService(NotificationManager::class.java).let { manager ->
        val diagnostics =
            AndroidLiveNotificationAdapter.diagnostics(
                manager,
                manager.activeNotifications.firstOrNull { it.id == PersistentBridgeService.NOTIFICATION_ID }?.notification,
            )
        val persisted = context.getSharedPreferences(PersistentBridgeService.LIVE_NOTIFICATION_DIAGNOSTICS, Context.MODE_PRIVATE)
        liveNotificationStatus(
            liveRequested = liveRequested,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            canPostPromoted = diagnostics.canPostPromotedNotifications,
            hasPromotableCharacteristics = diagnostics.hasPromotableCharacteristics,
            deviceVendorExtrasApplied = persisted.getBoolean(PersistentBridgeService.DIAGNOSTIC_DEVICE_VENDOR_EXTRAS_APPLIED, false),
            lastPostAttemptAt = persisted.getLong(PersistentBridgeService.DIAGNOSTIC_LAST_POST_ATTEMPT_AT, 0L),
            canScheduleExactAlarms =
                if (Build.VERSION.SDK_INT >= 31) {
                    context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
                } else {
                    null
                },
        )
    }
