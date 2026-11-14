package app.sugarlicious.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.sugarlicious.complications.DirectSensorReadingResolver
import app.sugarlicious.model.CgmSourceState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.protocol.WatchDataSource
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.flow.first

internal fun shouldEnableCgmAlerts(
    selectedSource: WatchDataSource,
    canonicalSource: DataSourceId?,
    resolverState: CgmSourceState?,
): Boolean =
    selectedSource == WatchDataSource.DEXCOM_G7_WATCH ||
        (
            selectedSource == WatchDataSource.AUTOMATIC &&
                (canonicalSource == DataSourceId.DEXCOM_G7_WATCH || resolverState == CgmSourceState.NO_SOURCE)
        )

internal fun publishCgmAlertMode(
    context: Context,
    selectedSource: WatchDataSource,
    resolvedState: TherapyDisplayState?,
) {
    val resolverState = DirectSensorReadingResolver.sourceState(resolvedState)
    val alarmsEnabled = shouldEnableCgmAlerts(selectedSource, resolvedState?.source, resolverState)
    val automaticEnableAt =
        resolvedState
            ?.glucose
            ?.measuredAtEpochMs
            ?.plus(DirectSensorReadingResolver.defaultPolicy.mobileFailoverAfterMs + 1L)
            ?.takeIf { selectedSource == WatchDataSource.AUTOMATIC && !alarmsEnabled }
    val intent =
        Intent(ACTION_SET_CGM_SOURCE)
            .setComponent(ComponentName(CGM_PACKAGE, CGM_SOURCE_RECEIVER))
            .putExtra(EXTRA_CGM_SELECTED, selectedSource == WatchDataSource.DEXCOM_G7_WATCH)
            .putExtra(EXTRA_CGM_ALARMS_ENABLED, alarmsEnabled)
            .putExtra(EXTRA_CGM_AUTOMATIC_ENABLE_AT, automaticEnableAt ?: 0L)
    context.sendBroadcast(intent, CGM_CONFIG_PERMISSION)
}

internal suspend fun resolveAndPublishCurrentCgmAlertMode(
    context: Context,
    selectedSource: WatchDataSource = WearDisplayPreferences.read(context).dataSource,
) {
    val phoneState = TherapyStateStore(context).state.first()
    val resolved =
        DirectSensorReadingResolver.resolve(
            context = context,
            fallback = phoneState,
            dataSource = selectedSource,
        )
    publishCgmAlertMode(context, selectedSource, resolved)
}

private const val ACTION_SET_CGM_SOURCE = "app.aapswear.g7watch.SET_SOURCE"
private const val CGM_PACKAGE = "app.sugarwear"
private const val CGM_SOURCE_RECEIVER = "app.sugarwear.CgmSourceControlReceiver"
private const val EXTRA_CGM_SELECTED = "g7_selected"
private const val EXTRA_CGM_ALARMS_ENABLED = "alarms_enabled"
private const val EXTRA_CGM_AUTOMATIC_ENABLE_AT = "automatic_enable_at"
private const val CGM_CONFIG_PERMISSION = "app.sugarwear.permission.CONFIGURE_G7"
