package app.sugarlicious.datasource.therapybridge

import android.content.Context
import android.os.Build
import app.sugarlicious.datasource.HealthDataSource
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.TherapyDisplayState
import java.util.concurrent.atomic.AtomicReference

enum class TherapyBroadcastContract(
    val id: String?,
) {
    DEV_EXTENDED_STATUS_V1("AAPS_EXTENDED_STATUS_V1"),
    STABLE_LEGACY_STATUS("AAPS_LEGACY_STATUS"),
    UNSUPPORTED(null),
}

data class TherapyBridgeInstallation(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
)

class TherapyBridgeCapabilityDetector {
    fun detect(values: Map<String, Any?>): TherapyBroadcastContract =
        when {
            values.keys.any { it in EXTENDED_KEYS } -> TherapyBroadcastContract.DEV_EXTENDED_STATUS_V1
            "glucoseMgdl" in values && "glucoseTimeStamp" in values -> TherapyBroadcastContract.STABLE_LEGACY_STATUS
            else -> TherapyBroadcastContract.UNSUPPORTED
        }

    companion object {
        private val EXTENDED_KEYS =
            setOf("deltaMgdl", "avgDeltaMgdl", "bolusIob", "basalIob", "baseBasal", "pumpStatus", "pumpReservoir", "therapyEvents")
        val KNOWN_PACKAGES =
            listOf(
                "info.nightscout.androidaps",
            )

        fun detectContract(values: Map<String, Any?>) = TherapyBridgeCapabilityDetector().detect(values)

        @Suppress("DEPRECATION")
        fun detectInstallation(context: Context): TherapyBridgeInstallation? =
            KNOWN_PACKAGES.firstNotNullOfOrNull { packageName ->
                detectInstallation(context, packageName)
            }

        @Suppress("DEPRECATION")
        fun detectInstallation(context: Context, packageName: String): TherapyBridgeInstallation? =
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()?.let { info ->
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)info.longVersionCode else info.versionCode.toLong()
                TherapyBridgeInstallation(packageName, info.versionName, code)
            }
    }
}

class TherapyPayloadValidator {
    fun isValid(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ): Boolean {
        val glucose = values.number("glucoseMgdl") ?: return false
        val timestamp = values.number("glucoseTimeStamp")?.toLong() ?: return false
        return glucose in 20.0..1000.0 && timestamp > 0 && timestamp <= receivedAtEpochMs + FreshnessPolicy.FUTURE_TOLERANCE_MS
    }

    private fun Map<String, Any?>.number(key: String): Double? =
        get(key)
            ?.let {
                when (it) {
                    is Number -> it.toDouble()
                    is String -> it.toDoubleOrNull()
                    else -> null
                }
            }?.takeIf { it.isFinite() }
}

interface TherapyVersionAdapter {
    fun parse(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ): TherapyDisplayState?
}

class DevelopmentTherapyAdapter : TherapyVersionAdapter {
    override fun parse(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ) = TherapyBridgePayloadAdapter.parse(values, receivedAtEpochMs)
}

class StableTherapyAdapter : TherapyVersionAdapter {
    override fun parse(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ) = TherapyBridgePayloadAdapter.parse(values, receivedAtEpochMs)
}

class TherapyBridgeDataSource(
    private val detector: TherapyBridgeCapabilityDetector = TherapyBridgeCapabilityDetector(),
    private val validator: TherapyPayloadValidator = TherapyPayloadValidator(),
    private val dev: TherapyVersionAdapter = DevelopmentTherapyAdapter(),
    private val stable: TherapyVersionAdapter = StableTherapyAdapter(),
) : HealthDataSource {
    private val current = AtomicReference<TherapyDisplayState?>()

    fun accept(
        values: Map<String, Any?>,
        receivedAtEpochMs: Long,
    ): TherapyDisplayState? {
        if (!validator.isValid(values, receivedAtEpochMs))return null
        val parsed =
            when (detector.detect(values)) {
                TherapyBroadcastContract.DEV_EXTENDED_STATUS_V1 -> dev.parse(values, receivedAtEpochMs)
                TherapyBroadcastContract.STABLE_LEGACY_STATUS -> stable.parse(values, receivedAtEpochMs)
                TherapyBroadcastContract.UNSUPPORTED -> null
            }
        if (parsed !=
            null
        ) {
            current.set(parsed)
        }
        return parsed
    }

    override fun latest(): TherapyDisplayState? = current.get()
}
