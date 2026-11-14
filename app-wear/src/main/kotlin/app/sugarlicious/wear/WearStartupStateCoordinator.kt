package app.sugarlicious.wear

import android.content.Context
import app.sugarlicious.complications.ActiveProviderUpdates
import app.sugarlicious.complications.ComplicationUpdatePlanner
import app.sugarlicious.model.DataCapability
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.flow.first

/**
 * Rehydrates Sugarlicious Wear from its persisted phone-fed state.
 *
 * SugarWear is a separate application and is not a Sugarlicious CGM input. Legacy
 * DEXCOM_G7_WATCH rows written by the temporary bridge are removed during rehydration so they
 * cannot survive reboot/app update and appear as a second graph series.
 */
internal object WearStartupStateCoordinator {
    suspend fun rehydrate(context: Context): TherapyDisplayState? {
        val app = context.applicationContext
        val store = TherapyStateStore(app)
        val persisted = store.state.first()
        val sanitized = persisted?.withoutDirectToWatchInput()
        if (persisted != null && sanitized != persisted) {
            sanitized?.let { store.save(it) }
        }

        val snapshot = prepareStartupSnapshot(sanitized, System.currentTimeMillis())
        WearCanonicalStateEvents.publishLocalReadingUpdate()
        ComplicationUpdatePlanner.allManagedProviders.forEach { provider ->
            ActiveProviderUpdates.request(app, provider)
        }
        requestSugarliciousTileUpdates(app)
        return snapshot.state
    }
}

internal fun TherapyDisplayState.withoutDirectToWatchInput(): TherapyDisplayState {
    val filteredHistory = glucoseHistory.filter { it.source != DataSourceId.DEXCOM_G7_WATCH }
    val currentIsDirect =
        source == DataSourceId.DEXCOM_G7_WATCH || glucose?.source == DataSourceId.DEXCOM_G7_WATCH
    val safeGlucose = glucose?.takeUnless { currentIsDirect || it.source == DataSourceId.DEXCOM_G7_WATCH }
    val safeCapabilities =
        if (currentIsDirect && safeGlucose == null) {
            capabilities -
                setOf(
                    DataCapability.GLUCOSE,
                    DataCapability.TREND,
                    DataCapability.DELTA,
                    DataCapability.AVERAGE_DELTA,
                )
        } else {
            capabilities
        }

    return copy(
        // Sugarlicious Wear is phone-fed. Removing a legacy SugarWear snapshot must not turn
        // the configured phone source into the runtime pseudo-source OTHER. There may be no
        // current phone measurement yet, but the expected input remains AndroidAPS.
        source = if (source == DataSourceId.DEXCOM_G7_WATCH) DataSourceId.ANDROID_APS else source,
        sourceVersion = if (currentIsDirect) null else sourceVersion,
        sourceContract = if (currentIsDirect) "WEAR_PHONE_ONLY:NO_DIRECT_WATCH_CGM" else sourceContract,
        glucose = safeGlucose,
        glucoseHistory = filteredHistory,
        capabilities = safeCapabilities,
    )
}

internal data class StartupStateSnapshot(
    val state: TherapyDisplayState?,
    val freshness: Freshness,
)

/** Re-evaluates time-derived presentation without mutating measurement identity. */
internal fun prepareStartupSnapshot(
    persisted: TherapyDisplayState?,
    nowEpochMs: Long,
): StartupStateSnapshot =
    StartupStateSnapshot(
        state = persisted,
        freshness =
            persisted?.let { TherapyDisplayFormatter.freshness(it, nowEpochMs) }
                ?: Freshness.NO_DATA,
    )
