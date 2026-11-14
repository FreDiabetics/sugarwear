package app.sugarlicious.mobile

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.CgmGraphPolicy
import app.sugarlicious.model.CgmRangeClass
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.Freshness
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.GlucoseGraphScale
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.GraphAxisLayoutSpec
import app.sugarlicious.model.GraphTimeWindow
import app.sugarlicious.model.RangeExcursion
import app.sugarlicious.model.RelativeGraphTimeAxis
import app.sugarlicious.model.TherapyDisplayFormatter
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val EXTERNAL_SURFACE_MINUTE_MS = 60_000L

internal fun delayUntilNextExternalSurfaceMinute(nowEpochMs: Long): Long {
    val remainder = Math.floorMod(nowEpochMs, EXTERNAL_SURFACE_MINUTE_MS)
    return (EXTERNAL_SURFACE_MINUTE_MS - remainder).coerceAtLeast(1L)
}

internal fun notificationRevision(
    state: TherapyDisplayState?,
    nowEpochMs: Long,
    selectedUnit: GlucoseUnit?,
): String = notificationPresentation(state, nowEpochMs, selectedUnit).revision

internal fun shouldPostNotificationOnStart(
    foregroundStarted: Boolean,
    action: String?,
    currentRevision: String,
    notifiedRevision: String?,
): Boolean =
    !foregroundStarted ||
        action == PersistentBridgeService.ACTION_REFRESH ||
        action == PersistentBridgeService.ACTION_DISABLE_LIVE ||
        currentRevision != notifiedRevision

internal fun notificationGlucoseColorRole(
    valueMgDl: Double,
    thresholds: CgmThresholds,
): SugarliciousColorRole =
    when (thresholds.classify(valueMgDl)) {
        CgmRangeClass.VERY_LOW -> SugarliciousColorRole.GLUCOSE_VERY_LOW
        CgmRangeClass.LOW -> SugarliciousColorRole.GLUCOSE_LOW
        CgmRangeClass.IN_RANGE, null -> SugarliciousColorRole.GLUCOSE_IN_RANGE
        CgmRangeClass.HIGH -> SugarliciousColorRole.GLUCOSE_HIGH
        CgmRangeClass.VERY_HIGH -> SugarliciousColorRole.GLUCOSE_VERY_HIGH
    }

@dagger.hilt.android.AndroidEntryPoint
class PersistentBridgeService :
    Service(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    @javax.inject.Inject internal lateinit var therapyStore: dagger.Lazy<TherapyStateStore>
    private lateinit var uiPreferences: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var composedSourceConfiguration: MobileSourceConfiguration? = null
    private lateinit var manufacturerShareSource: CloudShareSource
    private lateinit var cloudEndpointCgmSource: CloudEndpointCgmSource
    private var latestState: TherapyDisplayState? = null
    private var notifiedPresentationRevision: String? = null
    private var foregroundStarted = false
    private var externalSurfaceClockJob: Job? = null
    private var freshnessBoundaryJob: Job? = null
    private val freshnessReconcileReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                reconcileFreshnessBoundary()
            }
        }

    override fun onCreate() {
        super.onCreate()
        uiPreferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        uiPreferences.registerOnSharedPreferenceChangeListener(this)
        manufacturerShareSource = CloudShareSource(applicationContext, scope)
        cloudEndpointCgmSource = CloudEndpointCgmSource(applicationContext, scope)
        ContextCompat.registerReceiver(
            this,
            freshnessReconcileReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        createNotificationChannel()
        scope.launch {
            therapyStore.get().state.collectLatest {
                latestState = it
                if (foregroundStarted) {
                    val revision = currentPresentationRevision()
                    if (revision != notifiedPresentationRevision) {
                        notifyUpdated()
                    } else {
                        scheduleNextFreshnessBoundary()
                    }
                }
            }
        }
        externalSurfaceClockJob =
            scope.launch {
                while (isActive) {
                    delay(delayUntilNextExternalSurfaceMinute(System.currentTimeMillis()))
                    if (foregroundStarted) {
                        // Widget age and its live graph edge are time-sensitive. The foreground
                        // notification intentionally changes only for a new glucose measurement.
                        runCatching { SugarliciousWidgets.update(applicationContext) }
                    }
                }
            }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (foregroundStarted) notifyUpdated()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_DISABLE_LIVE) {
            uiPreferences.edit { putBoolean(PREFERENCE_LIVE_NOTIFICATION, false) }
        }
        val now = System.currentTimeMillis()
        val revision = currentPresentationRevision(now)
        if (!foregroundStarted) {
            promoteToForeground(buildNotification(now))
            notifiedPresentationRevision = revision
            foregroundStarted = true
            scheduleNextFreshnessBoundary(now)
        } else if (shouldPostNotificationOnStart(foregroundStarted, intent?.action, revision, notifiedPresentationRevision)) {
            notifyUpdated()
        } else {
            scheduleNextFreshnessBoundary(now)
        }
        reconcileMobileSources()
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        if (key?.startsWith(MobileSourceConfigurationStore.PREFIX) == true) {
            reconcileMobileSources()
        }
        if (foregroundStarted) notifyUpdated()
    }

    private fun reconcileMobileSources() {
        val configuration = MobileSourceConfigurationStore.read(this)
        if (composedSourceConfiguration == configuration) return
        composedSourceConfiguration = configuration
        manufacturerShareSource.refresh()
        cloudEndpointCgmSource.refresh()
        scope.launch(Dispatchers.IO) { MobileCanonicalStateCoordinator.recompose(applicationContext) }
    }

    override fun onDestroy() {
        manufacturerShareSource.close()
        cloudEndpointCgmSource.close()
        uiPreferences.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(freshnessReconcileReceiver)
        externalSurfaceClockJob?.cancel()
        freshnessBoundaryJob?.cancel()
        cancelFreshnessBoundary()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyUpdated() {
        val now = System.currentTimeMillis()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(now))
        notifiedPresentationRevision = currentPresentationRevision(now)
        scheduleNextFreshnessBoundary(now)
    }

    private fun currentPresentationRevision(nowEpochMs: Long = System.currentTimeMillis()): String =
        notificationRevision(
            latestState,
            nowEpochMs,
            DashboardUiPreferences.read(uiPreferences).unitFor(latestState),
        )

    private fun scheduleNextFreshnessBoundary(nowEpochMs: Long = System.currentTimeMillis()) {
        freshnessBoundaryJob?.cancel()
        val alarmManager = getSystemService(AlarmManager::class.java)
        val pendingIntent = freshnessBoundaryPendingIntent()
        alarmManager.cancel(pendingIntent)
        val visibleBoundary =
            listOfNotNull(
                nextNotificationFreshnessBoundary(latestState, nowEpochMs),
                nextNotificationAgeBoundary(latestState, nowEpochMs),
            ).minOrNull() ?: return
        nextNotificationSafetyBoundary(latestState, nowEpochMs)?.let { safetyBoundary ->
            val triggerAt = SystemClock.elapsedRealtime() + (safetyBoundary - nowEpochMs).coerceAtLeast(1L)
            if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
        freshnessBoundaryJob =
            scope.launch {
                delay((visibleBoundary - System.currentTimeMillis()).coerceAtLeast(1L))
                reconcileFreshnessBoundary()
            }
    }

    private fun reconcileFreshnessBoundary() {
        if (!foregroundStarted) return
        val now = System.currentTimeMillis()
        if (currentPresentationRevision(now) != notifiedPresentationRevision) {
            notifyUpdated()
        } else {
            scheduleNextFreshnessBoundary(now)
        }
    }

    private fun cancelFreshnessBoundary() {
        getSystemService(AlarmManager::class.java).cancel(freshnessBoundaryPendingIntent())
    }

    private fun freshnessBoundaryPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            FRESHNESS_BOUNDARY_REQUEST_CODE,
            Intent(this, PersistentBridgeService::class.java).setAction(ACTION_FRESHNESS_BOUNDARY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(nowEpochMs: Long = System.currentTimeMillis()): Notification {
        val liveRequested = uiPreferences.getBoolean(PREFERENCE_LIVE_NOTIFICATION, false)
        val exactFreshnessAvailable =
            Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val mode = notificationMode(liveRequested && exactFreshnessAvailable, Build.VERSION.SDK_INT, Build.MANUFACTURER.orEmpty())
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        if (mode != NotificationMode.STANDARD_CUSTOM) {
            return buildLiveNotification(mode, openApp, nowEpochMs)
        }
        return buildStandardCustomNotification(openApp, nowEpochMs)
    }

    private fun buildStandardCustomNotification(
        openApp: PendingIntent,
        nowEpochMs: Long,
    ): Notification {
        val notificationGraphEnabled =
            uiPreferences.getBoolean(PREFERENCE_NOTIFICATION_GRAPH_ENABLED, true)
        val display = notificationDisplay(latestState, nowEpochMs)
        val collapsedGraph =
            if (notificationGraphEnabled) {
                NotificationGraphRenderer.renderCollapsed(this, latestState, uiPreferences)
            } else {
                null
            }
        val expandedGraph =
            if (notificationGraphEnabled) {
                NotificationGraphRenderer.renderExpanded(this, latestState, uiPreferences)
            } else {
                null
            }
        val collapsedView =
            notificationRemoteView(
                R.layout.notification_sugarlicious_collapsed,
                NotificationGraphProfile.COLLAPSED,
                display,
                collapsedGraph,
            )
        val expandedView =
            notificationRemoteView(
                R.layout.notification_sugarlicious_expanded,
                NotificationGraphProfile.EXPANDED,
                display,
                expandedGraph,
            )

        val builder =
            Notification
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_outlined)
                .setColor(DeviceVendorLiveNotificationAdapter.systemNotificationIconColor(this))
                .setContentTitle(display.title)
                .setContentText(display.subtitle)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)

        return builder.build()
    }

    private fun buildLiveNotification(
        mode: NotificationMode,
        openApp: PendingIntent,
        nowEpochMs: Long,
    ): Notification {
        val unit = DashboardUiPreferences.read(uiPreferences).unitFor(latestState)
        val presentation = notificationPresentation(latestState, nowEpochMs, unit)
        val disableLive =
            PendingIntent.getService(
                this,
                1,
                Intent(this, PersistentBridgeService::class.java).setAction(ACTION_DISABLE_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_outlined)
                .setColor(DeviceVendorLiveNotificationAdapter.systemNotificationIconColor(this))
                .setContentTitle(presentation.drawerPrimaryText)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(R.drawable.ic_notification, "Live beenden", disableLive)
        if (mode != NotificationMode.SAMSUNG_LIVE) {
            presentation.therapyText?.let(builder::setContentText)
        }
        listOfNotNull(presentation.profileText, presentation.sourceText.takeIf(String::isNotBlank))
            .joinToString(" · ")
            .takeIf(String::isNotBlank)
            ?.let(builder::setSubText)

        var deviceVendorExpandedView: RemoteViews? = null
        if (mode == NotificationMode.SAMSUNG_LIVE) {
            val graphEnabled = uiPreferences.getBoolean(PREFERENCE_NOTIFICATION_GRAPH_ENABLED, true)
            val display = notificationDisplay(latestState, nowEpochMs)
            val collapsedGraph =
                if (graphEnabled) NotificationGraphRenderer.renderCollapsed(this, latestState, uiPreferences) else null
            val expandedGraph =
                if (graphEnabled) NotificationGraphRenderer.renderExpanded(this, latestState, uiPreferences) else null
            deviceVendorExpandedView =
                notificationRemoteView(
                    R.layout.notification_sugarlicious_expanded,
                    NotificationGraphProfile.EXPANDED,
                    display,
                    expandedGraph,
                )
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(
                    notificationRemoteView(
                        R.layout.notification_sugarlicious_collapsed,
                        NotificationGraphProfile.COLLAPSED,
                        display,
                        collapsedGraph,
                    ),
                ).setCustomBigContentView(
                    deviceVendorExpandedView,
                )
        }

        AndroidLiveNotificationAdapter.apply(builder, presentation)
        val deviceVendorTexts =
            if (mode == NotificationMode.SAMSUNG_LIVE) {
                DeviceVendorLiveNotificationAdapter.apply(
                    this,
                    builder,
                    presentation,
                    Build.MANUFACTURER.orEmpty(),
                    expandedRemoteView = deviceVendorExpandedView,
                )
            } else {
                null
            }
        getSharedPreferences(LIVE_NOTIFICATION_DIAGNOSTICS, MODE_PRIVATE).edit {
            putString(DIAGNOSTIC_MODE, mode.name)
            putBoolean(DIAGNOSTIC_DEVICE_VENDOR_EXTRAS_APPLIED, deviceVendorTexts != null)
            putLong(DIAGNOSTIC_LAST_POST_ATTEMPT_AT, nowEpochMs)
        }
        return builder.build()
    }

    private fun notificationRemoteView(
        layoutId: Int,
        profile: NotificationGraphProfile,
        display: NotificationDisplay,
        graph: Bitmap?,
    ): RemoteViews {
        val palette = SugarliciousColorStore.load(uiPreferences)
        val textPrimary = palette.argb(SugarliciousColorRole.TEXT_PRIMARY)
        val textSecondary = palette.argb(SugarliciousColorRole.TEXT_SECONDARY)
        val deltaUnitColor = palette.argb(SugarliciousColorRole.DELTA_UNIT)

        val layout = NotificationLayoutSettingsStore.read(uiPreferences, profile)
        val systemTrendScale = DashboardUiPreferences.read(uiPreferences).trendScalePercent
        val valueBaseSp = if (profile == NotificationGraphProfile.COLLAPSED) 29f else 36f
        val metaBaseSp = if (profile == NotificationGraphProfile.COLLAPSED) 11f else 13f
        val trendBaseDp = if (profile == NotificationGraphProfile.COLLAPSED) 19f else 23f
        val trendSizeDp = trendBaseDp * layout.resolvedTrendPercent(systemTrendScale) / 100f
        val density = resources.displayMetrics.density
        return RemoteViews(packageName, layoutId).apply {
            val styledTitle = SpannableString(display.title)
            setTextViewText(R.id.notification_value, styledTitle)
            val styledSubtitle =
                SpannableString(display.subtitle).apply {
                    display.deltaUnitText?.let { segment ->
                        val start = display.subtitle.indexOf(segment)
                        if (start >=
                            0
                        ) {
                            setSpan(ForegroundColorSpan(deltaUnitColor), start, start + segment.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            setTextViewText(R.id.notification_meta, styledSubtitle)
            setTextViewTextSize(
                R.id.notification_value,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                valueBaseSp * layout.glucoseScalePercent / 100f,
            )
            setTextViewTextSize(
                R.id.notification_meta,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                metaBaseSp * layout.metaScalePercent / 100f,
            )
            setFloat(R.id.notification_value, "setTranslationX", layout.glucoseXPercent / 100f * 40f * density)
            setFloat(R.id.notification_value, "setTranslationY", layout.glucoseYPercent / 100f * 24f * density)
            setFloat(R.id.notification_trend, "setTranslationX", layout.trendXPercent / 100f * 40f * density)
            setFloat(R.id.notification_trend, "setTranslationY", layout.trendYPercent / 100f * 24f * density)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val trendAspect =
                    display.trend?.let {
                        app.sugarlicious.model.TrendVisuals
                            .spec(it)
                            ?.aspectRatio
                    } ?: 1f
                setViewLayoutWidth(R.id.notification_trend, trendSizeDp * trendAspect, android.util.TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.notification_trend, trendSizeDp, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
            setTextColor(R.id.notification_value, if (display.signalLoss) 0xFF9E9E9E.toInt() else palette.argb(display.valueColorRole))
            setTextColor(R.id.notification_meta, textSecondary)
            val trendBitmap =
                display.trend?.let {
                    NotificationTrendRenderer.render(
                        this@PersistentBridgeService,
                        it,
                        sizePx = (trendSizeDp * density).roundToInt(),
                        tint = textPrimary,
                    )
                }
            if (trendBitmap != null) {
                setViewVisibility(R.id.notification_trend, View.VISIBLE)
                setImageViewBitmap(R.id.notification_trend, trendBitmap)
            } else {
                setViewVisibility(R.id.notification_trend, View.GONE)
            }
            if (graph != null) {
                setViewVisibility(R.id.notification_graph, View.VISIBLE)
                setImageViewBitmap(R.id.notification_graph, graph)
            } else {
                setViewVisibility(R.id.notification_graph, View.GONE)
            }
        }
    }

    private fun notificationDisplay(
        state: TherapyDisplayState?,
        now: Long = System.currentTimeMillis(),
    ): NotificationDisplay {
        val glucose = state?.glucose
        val freshness = FreshnessPolicy.classify(glucose?.measuredAtEpochMs, now)
        if (glucose == null || !TherapyDisplayFormatter.isGlucoseKnown(state)) {
            return NotificationDisplay("—", "Keine aktuellen Glukosedaten", null, null, false, SugarliciousColorRole.TEXT_PRIMARY)
        }

        val selectedUnit = DashboardUiPreferences.read(uiPreferences).unitFor(state)
        val value =
            if (selectedUnit == GlucoseUnit.MMOL_L) {
                String.format(Locale.getDefault(), "%.1f", glucose.valueMgDl / 18.0)
            } else {
                glucose.valueMgDl.roundToInt().toString()
            }
        val delta =
            TherapyDisplayFormatter
                .signedDelta(glucose.deltaMgDl, selectedUnit)
                .ifBlank { "—" }
        val age = TherapyDisplayFormatter.ageMinutes(glucose.measuredAtEpochMs, now)
        // Delta intentionally replaces the former mg/dL/mmol/L line in both layouts.
        val unit = if (selectedUnit == GlucoseUnit.MMOL_L) "mmol/L" else "mg/dL"
        val deltaUnit = "$delta $unit"
        val subtitle = "$deltaUnit · $age"
        return NotificationDisplay(
            value,
            subtitle,
            glucose.trend.takeUnless { it == app.sugarlicious.model.Trend.UNKNOWN },
            deltaUnit,
            freshness == Freshness.SIGNAL_LOSS,
            notificationGlucoseColorRole(glucose.valueMgDl, CgmThresholdPreferences.read(uiPreferences)),
        )
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Glukose im Hintergrund",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Zeigt den aktuellen Glukosewert und hält die lokale Watch-Verbindung aktiv"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        val manager = getSystemService(NotificationManager::class.java)
        // Mobile and Wear share an application ID. A past Wear installation on a
        // phone can leave this obsolete channel behind after the Mobile upgrade.
        manager.deleteNotificationChannel("sugarlicious_wear_runtime")
        manager.createNotificationChannel(channel)
    }

    private data class NotificationDisplay(
        val title: String,
        val subtitle: String,
        val trend: app.sugarlicious.model.Trend?,
        val deltaUnitText: String?,
        val signalLoss: Boolean,
        val valueColorRole: SugarliciousColorRole,
    )

    companion object {
        const val PREFERENCE_LIVE_NOTIFICATION = "liveNotification"
        const val PREFERENCE_NOTIFICATION_GRAPH_ENABLED = "notification.graphEnabled"
        const val PREFERENCE_NOTIFICATION_GRAPH_HOURS = "notification.graphHours"

        // Legacy shared notification-dot keys. Kept only so in-place upgrades can snapshot the
        // previous collapsed appearance before collapsed/expanded profiles become independent.
        const val PREFERENCE_NOTIFICATION_DOT_RADIUS = "notification.cgmDotRadiusDp"
        const val PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED = "notification.cgmDotOutlineEnabled"
        const val PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH = "notification.cgmDotOutlineWidthDp"

        const val PREFERENCE_NOTIFICATION_COLLAPSED_DOT_RADIUS = "notification.cgm.dot.collapsed.radiusDp"
        const val PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_ENABLED = "notification.cgm.dot.collapsed.outlineEnabled"
        const val PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_WIDTH = "notification.cgm.dot.collapsed.outlineWidthDp"
        const val PREFERENCE_NOTIFICATION_EXPANDED_DOT_RADIUS = "notification.cgm.dot.expanded.radiusDp"
        const val PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_ENABLED = "notification.cgm.dot.expanded.outlineEnabled"
        const val PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_WIDTH = "notification.cgm.dot.expanded.outlineWidthDp"
        const val PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED = "notification.cgm.dot.profilesMigratedV1"

        const val LIVE_NOTIFICATION_DIAGNOSTICS = "live_notification_diagnostics"
        const val DIAGNOSTIC_MODE = "mode"
        const val DIAGNOSTIC_DEVICE_VENDOR_EXTRAS_APPLIED = "samsungExtrasApplied"
        const val DIAGNOSTIC_LAST_POST_ATTEMPT_AT = "lastPostAttemptAt"
        const val CHANNEL_ID = "sugarlicious_background"
        const val NOTIFICATION_ID = 4101
        private const val PREFERENCES_NAME = "dashboard_ui"
        internal const val ACTION_REFRESH = "app.aapswear.action.REFRESH_PERSISTENT_NOTIFICATION"
        internal const val ACTION_DISABLE_LIVE = "app.aapswear.action.DISABLE_LIVE_NOTIFICATION"
        internal const val ACTION_FRESHNESS_BOUNDARY = "app.aapswear.action.REFRESH_NOTIFICATION_FRESHNESS"
        private const val FRESHNESS_BOUNDARY_REQUEST_CODE = 4102

        fun start(context: Context): Boolean = startWithAction(context, null)

        fun refresh(context: Context): Boolean = startWithAction(context, ACTION_REFRESH)

        private fun startWithAction(
            context: Context,
            action: String?,
        ): Boolean =
            try {
                context.startForegroundService(
                    Intent(context, PersistentBridgeService::class.java).apply { this.action = action },
                )
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
    }
}

internal enum class NotificationGraphProfile(
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val displayWidthDp: Float,
    val displayHeightDp: Float,
    val cornerRadiusDp: Float,
    val defaultDotRadiusDp: Float,
    val defaultOutlineWidthDp: Float,
) {
    COLLAPSED(
        bitmapWidth = 704,
        bitmapHeight = 192,
        displayWidthDp = 176f,
        displayHeightDp = 48f,
        cornerRadiusDp = 10f,
        defaultDotRadiusDp = 2.4f,
        defaultOutlineWidthDp = 0.95f,
    ),
    EXPANDED(
        bitmapWidth = 720,
        bitmapHeight = 296,
        displayWidthDp = 360f,
        displayHeightDp = 148f,
        cornerRadiusDp = 18f,
        defaultDotRadiusDp = 3.2f,
        defaultOutlineWidthDp = 1.0f,
    ),
}

internal fun notificationGraphColor(
    preferences: SharedPreferences,
    palette: SugarliciousPalette,
    role: SugarliciousColorRole,
): Int {
    val notificationColorPrefix = if (palette.isLight) "notification.color.light." else "notification.color.dark."
    val overrideKey = "notification.color.override." + role.preferenceKey
    val legacyModeKey = notificationColorPrefix + role.preferenceKey
    return when {
        preferences.contains(legacyModeKey) -> preferences.getInt(legacyModeKey, palette.argb(role))
        !palette.isLight && preferences.contains(overrideKey) -> preferences.getInt(overrideKey, palette.argb(role))
        else -> NotificationGraphColorDefaults.argb(palette, role)
    }
}

internal object NotificationGraphColorDefaults {
    fun argb(
        palette: SugarliciousPalette,
        role: SugarliciousColorRole,
    ): Int =
        if (palette.isLight) {
            when (role) {
                SugarliciousColorRole.GRAPH_BACKGROUND -> Color.WHITE
                SugarliciousColorRole.CGM_DOT_LOW,
                SugarliciousColorRole.CGM_DOT_IN_RANGE,
                SugarliciousColorRole.CGM_DOT_HIGH,
                SugarliciousColorRole.GRAPH_CURRENT_OUTLINE,
                -> Color.BLACK
                SugarliciousColorRole.GRAPH_DIVIDER -> 0xFF747474.toInt()
                SugarliciousColorRole.TARGET_VALUE -> 0xFFF0F0F0.toInt()
                else -> palette.argb(role)
            }
        } else {
            when (role) {
                SugarliciousColorRole.RANGE_IN_RANGE -> 0xFF282828.toInt()
                else -> palette.argb(role)
            }
        }
}

internal data class NotificationGraphDotStyle(
    val cgmRadiusDp: Float,
    val cgmOutlineEnabled: Boolean,
    val cgmOutlineWidthDp: Float,
    val historicalOutlineEnabled: Boolean = cgmOutlineEnabled,
    val historicalOutlineWidthDp: Float = cgmOutlineWidthDp,
    val currentOutlineWidthDp: Float = cgmOutlineWidthDp,
)

internal object NotificationGraphDotStyleStore {
    private const val MOBILE_RADIUS = "cgm.dotRadiusDp"
    private const val MOBILE_OUTLINE_ENABLED = "cgm.dotOutlineEnabled"
    private const val MOBILE_OUTLINE_WIDTH = "cgm.dotOutlineWidthDp"

    fun read(
        preferences: SharedPreferences,
        profile: NotificationGraphProfile,
    ): NotificationGraphDotStyle = read(preferences, SugarliciousColorStore.activeMode(preferences), profile)

    fun read(
        preferences: SharedPreferences,
        mode: app.sugarlicious.model.AppearanceMode,
        profile: NotificationGraphProfile,
    ): NotificationGraphDotStyle {
        ensureMigrated(preferences)
        return NotificationGraphDotStyle(
            cgmRadiusDp =
                preferences
                    .getFloat(
                        modeKey(mode, radiusKey(profile)),
                        preferences.getFloat(radiusKey(profile), profile.defaultDotRadiusDp),
                    ).coerceIn(1.5f, 6.0f),
            cgmOutlineEnabled =
                preferences.getBoolean(
                    modeKey(mode, outlineEnabledKey(profile)),
                    preferences.getBoolean(outlineEnabledKey(profile), true),
                ),
            cgmOutlineWidthDp =
                preferences
                    .getFloat(
                        modeKey(mode, outlineWidthKey(profile)),
                        preferences.getFloat(outlineWidthKey(profile), profile.defaultOutlineWidthDp),
                    ).coerceIn(0.25f, 3.0f),
            historicalOutlineEnabled =
                preferences.getBoolean(
                    modeKey(mode, historicalOutlineEnabledKey(profile)),
                    preferences.getBoolean(modeKey(mode, outlineEnabledKey(profile)), preferences.getBoolean(outlineEnabledKey(profile), true)),
                ),
            historicalOutlineWidthDp =
                preferences
                    .getFloat(
                        modeKey(mode, historicalOutlineWidthKey(profile)),
                        preferences.getFloat(modeKey(mode, outlineWidthKey(profile)), preferences.getFloat(outlineWidthKey(profile), profile.defaultOutlineWidthDp)),
                    ).coerceIn(0.25f, 3f),
            currentOutlineWidthDp =
                preferences
                    .getFloat(
                        modeKey(mode, currentOutlineWidthKey(profile)),
                        preferences.getFloat(modeKey(mode, outlineWidthKey(profile)), preferences.getFloat(outlineWidthKey(profile), profile.defaultOutlineWidthDp)),
                    ).coerceIn(0.25f, 3f),
        )
    }

    fun save(
        preferences: SharedPreferences,
        profile: NotificationGraphProfile,
        style: NotificationGraphDotStyle,
    ) = save(preferences, SugarliciousColorStore.activeMode(preferences), profile, style)

    fun save(
        preferences: SharedPreferences,
        mode: app.sugarlicious.model.AppearanceMode,
        profile: NotificationGraphProfile,
        style: NotificationGraphDotStyle,
    ) {
        ensureMigrated(preferences)
        preferences.edit {
            putFloat(modeKey(mode, radiusKey(profile)), style.cgmRadiusDp.coerceIn(1.5f, 6.0f))
            putBoolean(modeKey(mode, outlineEnabledKey(profile)), style.cgmOutlineEnabled)
            putFloat(modeKey(mode, outlineWidthKey(profile)), style.cgmOutlineWidthDp.coerceIn(0.25f, 3.0f))
            putBoolean(modeKey(mode, historicalOutlineEnabledKey(profile)), style.historicalOutlineEnabled)
            putFloat(modeKey(mode, historicalOutlineWidthKey(profile)), style.historicalOutlineWidthDp.coerceIn(0.25f, 3f))
            putFloat(modeKey(mode, currentOutlineWidthKey(profile)), style.currentOutlineWidthDp.coerceIn(0.25f, 3f))
        }
    }

    fun copyCollapsedToExpanded(preferences: SharedPreferences) {
        val collapsed = read(preferences, NotificationGraphProfile.COLLAPSED)
        save(preferences, NotificationGraphProfile.EXPANDED, collapsed)
    }

    fun resetProfiles(preferences: SharedPreferences) {
        preferences.edit {
            putFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_RADIUS,
                NotificationGraphProfile.COLLAPSED.defaultDotRadiusDp,
            )
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_ENABLED, true)
            putFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_WIDTH,
                NotificationGraphProfile.COLLAPSED.defaultOutlineWidthDp,
            )
            putFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_RADIUS,
                NotificationGraphProfile.EXPANDED.defaultDotRadiusDp,
            )
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_ENABLED, true)
            putFloat(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_WIDTH,
                NotificationGraphProfile.EXPANDED.defaultOutlineWidthDp,
            )
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED, true)
            app.sugarlicious.model.AppearanceMode.entries.forEach { mode ->
                NotificationGraphProfile.entries.forEach { profile ->
                    putFloat(modeKey(mode, radiusKey(profile)), profile.defaultDotRadiusDp)
                    putBoolean(modeKey(mode, outlineEnabledKey(profile)), true)
                    putFloat(modeKey(mode, outlineWidthKey(profile)), profile.defaultOutlineWidthDp)
                }
            }
            remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS)
            remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED)
            remove(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH)
        }
    }

    private fun ensureMigrated(preferences: SharedPreferences) {
        if (preferences.getBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED, false)) return

        val hasLegacy =
            listOf(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS,
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED,
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH,
                MOBILE_RADIUS,
                MOBILE_OUTLINE_ENABLED,
                MOBILE_OUTLINE_WIDTH,
            ).any(preferences::contains)
        if (!hasLegacy) return

        val collapsedRadius =
            preferences
                .getFloat(
                    PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_RADIUS,
                    preferences.getFloat(MOBILE_RADIUS, NotificationGraphProfile.COLLAPSED.defaultDotRadiusDp),
                ).coerceIn(1.5f, 6.0f)
        val collapsedOutline =
            preferences.getBoolean(
                PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_ENABLED,
                preferences.getBoolean(MOBILE_OUTLINE_ENABLED, true),
            )
        val collapsedOutlineWidth =
            preferences
                .getFloat(
                    PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_OUTLINE_WIDTH,
                    preferences.getFloat(MOBILE_OUTLINE_WIDTH, NotificationGraphProfile.COLLAPSED.defaultOutlineWidthDp),
                ).coerceIn(0.25f, 3.0f)

        // Expanded is deliberately independent after migration. Its larger graph gets a modestly
        // larger physical default without scaling dots proportionally to graph height.
        val expandedRadius = max(collapsedRadius, NotificationGraphProfile.EXPANDED.defaultDotRadiusDp)
        val expandedOutlineWidth = max(collapsedOutlineWidth, NotificationGraphProfile.EXPANDED.defaultOutlineWidthDp)

        preferences.edit {
            putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_RADIUS, collapsedRadius)
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_ENABLED, collapsedOutline)
            putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_WIDTH, collapsedOutlineWidth)
            putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_RADIUS, expandedRadius)
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_ENABLED, collapsedOutline)
            putFloat(PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_WIDTH, expandedOutlineWidth)
            putBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED, true)
            app.sugarlicious.model.AppearanceMode.entries.forEach { mode ->
                putFloat(modeKey(mode, radiusKey(NotificationGraphProfile.COLLAPSED)), collapsedRadius)
                putBoolean(modeKey(mode, outlineEnabledKey(NotificationGraphProfile.COLLAPSED)), collapsedOutline)
                putFloat(modeKey(mode, outlineWidthKey(NotificationGraphProfile.COLLAPSED)), collapsedOutlineWidth)
                putFloat(modeKey(mode, radiusKey(NotificationGraphProfile.EXPANDED)), expandedRadius)
                putBoolean(modeKey(mode, outlineEnabledKey(NotificationGraphProfile.EXPANDED)), collapsedOutline)
                putFloat(modeKey(mode, outlineWidthKey(NotificationGraphProfile.EXPANDED)), expandedOutlineWidth)
            }
        }
    }

    private fun modeKey(
        mode: app.sugarlicious.model.AppearanceMode,
        key: String,
    ): String = "notification.${mode.storageKey}.$key"

    private fun radiusKey(profile: NotificationGraphProfile): String =
        when (profile) {
            NotificationGraphProfile.COLLAPSED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_RADIUS
            NotificationGraphProfile.EXPANDED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_RADIUS
        }

    private fun outlineEnabledKey(profile: NotificationGraphProfile): String =
        when (profile) {
            NotificationGraphProfile.COLLAPSED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_ENABLED
            NotificationGraphProfile.EXPANDED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_ENABLED
        }

    private fun historicalOutlineEnabledKey(profile: NotificationGraphProfile) = outlineEnabledKey(profile) + ".historical"

    private fun historicalOutlineWidthKey(profile: NotificationGraphProfile) = outlineWidthKey(profile) + ".historical"

    private fun currentOutlineWidthKey(profile: NotificationGraphProfile) = outlineWidthKey(profile) + ".current"

    private fun outlineWidthKey(profile: NotificationGraphProfile): String =
        when (profile) {
            NotificationGraphProfile.COLLAPSED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_COLLAPSED_DOT_OUTLINE_WIDTH
            NotificationGraphProfile.EXPANDED -> PersistentBridgeService.PREFERENCE_NOTIFICATION_EXPANDED_DOT_OUTLINE_WIDTH
        }
}

internal object NotificationGraphRenderer {
    const val COLLAPSED_WIDTH = 704
    const val COLLAPSED_HEIGHT = 192
    const val EXPANDED_WIDTH = 720
    const val EXPANDED_HEIGHT = 296
    const val WIDTH = EXPANDED_WIDTH
    const val HEIGHT = EXPANDED_HEIGHT

    fun renderCollapsed(
        context: Context,
        state: TherapyDisplayState?,
        preferences: SharedPreferences,
    ): Bitmap =
        render(
            context = context,
            state = state,
            preferences = preferences,
            profile = NotificationGraphProfile.COLLAPSED,
            graphHoursOverride = notificationGraphHours(preferences),
        )

    fun renderExpanded(
        context: Context,
        state: TherapyDisplayState?,
        preferences: SharedPreferences,
    ): Bitmap =
        render(
            context = context,
            state = state,
            preferences = preferences,
            profile = NotificationGraphProfile.EXPANDED,
            graphHoursOverride = notificationGraphHours(preferences),
        )

    internal fun notificationGraphHours(preferences: SharedPreferences): Int =
        preferences
            .getInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 3)
            .takeIf { it in 1..3 }
            ?: 3

    internal fun notificationGraphWindow(
        nowEpochMs: Long,
        graphHours: Int,
    ): GraphTimeWindow = GraphTimeWindow.live(nowEpochMs, graphHours * 60L * 60_000L)

    private fun render(
        context: Context,
        state: TherapyDisplayState?,
        preferences: SharedPreferences,
        profile: NotificationGraphProfile,
        graphHoursOverride: Int? = null,
    ): Bitmap {
        val width = profile.bitmapWidth
        val height = profile.bitmapHeight
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val palette = SugarliciousColorStore.load(preferences)

        fun graphColor(role: SugarliciousColorRole): Int = notificationGraphColor(preferences, palette, role)

        val scaleX = width / profile.displayWidthDp
        val scaleY = height / profile.displayHeightDp
        val renderDensity = min(scaleX, scaleY)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val cornerRadius = profile.cornerRadiusDp * renderDensity
        val clip =
            Path().apply {
                addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
            }
        canvas.clipPath(clip)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Background is drawn with the range layers below so the scale lane can have its own alpha.

        val now = System.currentTimeMillis()
        val graphHours =
            graphHoursOverride ?: preferences
                .getInt("graphHours", 3)
                .takeIf { it in OVERVIEW_GRAPH_HOUR_OPTIONS }
                ?: 3
        val windowMs = graphHours * 60L * 60L * 1000L
        val timeWindow = notificationGraphWindow(now, graphHours)
        val start = timeWindow.startEpochMs
        val validSamples =
            CanonicalCgmHistory.merge(
                samples =
                    buildList {
                        addAll(state?.glucoseHistory.orEmpty())
                        state?.glucose?.let { glucose ->
                            add(
                                GlucoseSample(
                                    valueMgDl = glucose.valueMgDl,
                                    measuredAtEpochMs = glucose.measuredAtEpochMs,
                                    source = glucose.source,
                                    sensorId = glucose.sensorId,
                                    sessionId = glucose.sessionId,
                                    sequenceNumber = glucose.sequenceNumber,
                                    receivedAtEpochMs = glucose.receivedAtEpochMs,
                                    quality = glucose.quality,
                                ),
                            )
                        }
                    },
                nowEpochMs = now,
                preferredSource = state?.source,
                windowMs = windowMs,
            )
        val points = validSamples.associate { it.measuredAtEpochMs to it.valueMgDl }.entries.sortedBy { it.key }
        if (points.isEmpty()) return bitmap

        val thresholds = CgmThresholdPreferences.read(preferences)
        val targetLow = thresholds.lowMgDl
        val targetHigh = thresholds.highMgDl
        val excursion =
            CgmGraphPolicy.rangeExcursion(
                validSamples,
                thresholds,
            )
        val axis = if (profile == NotificationGraphProfile.COLLAPSED) GraphAxisLayoutSpec.COMPACT else GraphAxisLayoutSpec.DEFAULT

        fun dp(value: Float) = value * renderDensity
        val axisText =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = graphColor(SugarliciousColorRole.GRAPH_LABEL)
                textSize = dp(if (profile == NotificationGraphProfile.COLLAPSED) 7f else 8.5f)
                app.sugarlicious.uishared.SystemTypography
                    .apply(this, true)
            }
        val widestYLabel =
            maxOf(axisText.measureText(targetHigh.roundToInt().toString()), axisText.measureText(targetLow.roundToInt().toString()))
        val timeBand =
            if (profile == NotificationGraphProfile.EXPANDED) {
                (axisText.fontMetrics.descent - axisText.fontMetrics.ascent) +
                    dp(axis.plotToTickGapDp + axis.tickLengthDp + axis.tickToLabelGapDp)
            } else {
                0f
            }
        val plotLeft = bounds.left + dp(axis.outerEdgePaddingDp)
        val labelLaneLeft = bounds.right - dp(axis.outerEdgePaddingDp) - widestYLabel
        val plotRight = labelLaneLeft - dp(4f)
        val plotTop = bounds.top + dp(axis.outerEdgePaddingDp)
        val plotBottom = bounds.bottom - dp(axis.outerEdgePaddingDp) - timeBand
        val visualLeft = bounds.left
        val visualRight = bounds.right
        val visualTop = bounds.top
        val scaleLaneOpacity = preferences.getInt("notification.graph.scale_lane_opacity_percent", 30).coerceIn(0, 100)

        fun laneColor(color: Int): Int {
            val alpha = (color ushr 24) * scaleLaneOpacity / 100
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }

        fun drawSplitArea(
            top: Float,
            bottom: Float,
            color: Int,
        ) {
            paint.color = color
            canvas.drawRect(visualLeft, top, labelLaneLeft, bottom, paint)
            paint.color = laneColor(color)
            canvas.drawRect(labelLaneLeft, top, visualRight, bottom, paint)
        }

        fun y(value: Double): Float {
            val fraction = GlucoseGraphScale.ratio(value)
            return (plotBottom - fraction * (plotBottom - plotTop)).toFloat()
        }

        // The configured graph background is the base layer for the complete visual graph,
        // including the lane underneath the existing translucent scale-area layers. Time-based
        // content remains clipped/projected to plotRight below; only the background reaches right.
        paint.color = graphColor(SugarliciousColorRole.GRAPH_BACKGROUND)
        canvas.drawRect(visualLeft, visualTop, visualRight, bounds.bottom, paint)
        // Preserve the established translucent scale-lane composition exactly as before. These
        // draws are now overlays rather than pixels composited against a transparent bitmap.
        paint.color = laneColor(graphColor(SugarliciousColorRole.GRAPH_BACKGROUND))
        canvas.drawRect(labelLaneLeft, visualTop, visualRight, y(targetHigh), paint)
        canvas.drawRect(labelLaneLeft, y(targetLow), visualRight, bounds.bottom, paint)

        fun x(timestamp: Long): Float = timeWindow.plotX(timestamp, plotLeft, plotRight - plotLeft)

        paint.style = Paint.Style.FILL
        if (excursion == RangeExcursion.HIGH) {
            drawSplitArea(visualTop, y(targetHigh), graphColor(SugarliciousColorRole.RANGE_HIGH))
        }
        drawSplitArea(y(targetHigh), y(targetLow), graphColor(SugarliciousColorRole.RANGE_IN_RANGE))
        if (excursion == RangeExcursion.LOW) {
            drawSplitArea(y(targetLow), plotBottom, graphColor(SugarliciousColorRole.RANGE_LOW))
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeWidth = max(1f, renderDensity)
        paint.color = graphBoundaryColor(graphColor(SugarliciousColorRole.GRAPH_HIGH_LINE))
        canvas.drawLine(visualLeft, y(targetHigh), labelLaneLeft, y(targetHigh), paint)
        paint.color = laneColor(graphBoundaryColor(graphColor(SugarliciousColorRole.GRAPH_HIGH_LINE)))
        canvas.drawLine(labelLaneLeft, y(targetHigh), visualRight, y(targetHigh), paint)
        paint.color = graphBoundaryColor(graphColor(SugarliciousColorRole.GRAPH_LOW_LINE))
        canvas.drawLine(visualLeft, y(targetLow), labelLaneLeft, y(targetLow), paint)
        paint.color = laneColor(graphBoundaryColor(graphColor(SugarliciousColorRole.GRAPH_LOW_LINE)))
        canvas.drawLine(labelLaneLeft, y(targetLow), visualRight, y(targetLow), paint)

        fun drawYLabel(
            value: Double,
            aboveLine: Boolean,
        ) {
            val py = y(value)
            axisText.textAlign = Paint.Align.LEFT
            val gap = dp(2f)
            val baseline = if (aboveLine) py - gap - axisText.fontMetrics.descent else py + gap - axisText.fontMetrics.ascent
            canvas.drawText(value.roundToInt().toString(), labelLaneLeft, baseline, axisText)
        }
        drawYLabel(targetHigh, aboveLine = true)
        drawYLabel(targetLow, aboveLine = false)

        val dotStyle = NotificationGraphDotStyleStore.read(preferences, profile)
        val dotRadius = dotStyle.cgmRadiusDp * renderDensity
        val currentExtra = 0.18f * renderDensity

        points.forEachIndexed { index, point ->
            val px = x(point.key)
            val py = y(point.value)
            val current = index == points.lastIndex
            val outlineEnabled = current || dotStyle.historicalOutlineEnabled
            val outlineRadius =
                (dotStyle.cgmRadiusDp + if (current) dotStyle.currentOutlineWidthDp else dotStyle.historicalOutlineWidthDp) * renderDensity
            if (outlineEnabled) {
                paint.style = Paint.Style.FILL
                paint.color = graphColor(SugarliciousColorRole.GRAPH_CURRENT_OUTLINE)
                canvas.drawCircle(
                    px,
                    py,
                    outlineRadius + if (current) currentExtra else 0f,
                    paint,
                )
            }

            paint.style = Paint.Style.FILL
            paint.color =
                when {
                    point.value < targetLow -> graphColor(SugarliciousColorRole.CGM_DOT_LOW)
                    point.value > targetHigh -> graphColor(SugarliciousColorRole.CGM_DOT_HIGH)
                    else -> graphColor(SugarliciousColorRole.CGM_DOT_IN_RANGE)
                }
            canvas.drawCircle(
                px,
                py,
                dotRadius + if (current) currentExtra else 0f,
                paint,
            )
        }

        if (profile == NotificationGraphProfile.EXPANDED) {
            RelativeGraphTimeAxis.ticks(start, now, now, RelativeGraphTimeAxis.intervalHours(graphHours.toDouble())).forEach { tick ->
                val px = x(tick.timestampEpochMs)
                if (px in plotLeft..plotRight) {
                    val tickTop = plotBottom + dp(axis.plotToTickGapDp)
                    val tickBottom = tickTop + dp(axis.tickLengthDp)
                    paint.color = graphColor(SugarliciousColorRole.GRAPH_AXIS_TICK)
                    canvas.drawLine(px, tickTop, px, tickBottom, paint)
                    axisText.textAlign =
                        when {
                            tick.hoursBack == 0 -> Paint.Align.RIGHT
                            tick.timestampEpochMs <= start + 30_000L -> Paint.Align.LEFT
                            else -> Paint.Align.CENTER
                        }
                    canvas.drawText(tick.label, px, tickBottom + dp(axis.tickToLabelGapDp) - axisText.fontMetrics.ascent, axisText)
                }
            }
        }

        return bitmap
    }
}

internal fun graphBoundaryColor(color: Int): Int = color
