package app.sugarlicious.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.edit
import app.sugarlicious.complications.ActiveComplicationRegistry
import app.sugarlicious.complications.ActiveProviderUpdates
import app.sugarlicious.complications.ComplicationUpdatePlanner
import app.sugarlicious.complications.DirectSensorReadingResolver
import app.sugarlicious.model.CanonicalCgmHistory
import app.sugarlicious.model.DiagnosticSeverity
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.SugarliciousComplicationIds
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.withHistoryDerivedDelta
import app.sugarlicious.protocol.WatchRuntimeStatus
import app.sugarlicious.protocol.WearProtocol
import app.sugarlicious.storage.PersistentPredictionCache
import app.sugarlicious.storage.TherapyStateStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

internal fun shouldAcceptPhoneState(
    previous: TherapyDisplayState?,
    incoming: TherapyDisplayState,
): Boolean {
    if (previous == null) return true
    if (previous.canonicalRevision > 0L || incoming.canonicalRevision > 0L) {
        return incoming.canonicalRevision > previous.canonicalRevision
    }
    if (incoming.receivedAtEpochMs >= previous.receivedAtEpochMs) return true

    val previousGlucoseAt = previous.glucose?.measuredAtEpochMs ?: Long.MIN_VALUE
    val incomingGlucoseAt = incoming.glucose?.measuredAtEpochMs ?: Long.MIN_VALUE
    return incomingGlucoseAt > previousGlucoseAt
}

internal fun hasMeaningfulPhoneStateChange(
    previous: TherapyDisplayState?,
    incoming: TherapyDisplayState,
): Boolean = previous?.copy(receivedAtEpochMs = incoming.receivedAtEpochMs) != incoming

internal data class StatePayloadFingerprint(
    val size: Int,
    val sha256Hex: String,
)

internal fun statePayloadFingerprint(payload: ByteArray): StatePayloadFingerprint =
    StatePayloadFingerprint(
        payload.size,
        MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) },
    )

internal fun isCommittedStatePayload(
    payload: ByteArray,
    committed: StatePayloadFingerprint?,
): Boolean = committed != null && statePayloadFingerprint(payload) == committed

internal data class PendingStateDelivery(
    val payload: ByteArray,
    val transport: String,
    val state: TherapyDisplayState?,
    val eventId: String?,
    val generatedAtEpochMs: Long?,
) {
    companion object {
        fun decode(
            payload: ByteArray,
            transport: String,
        ): PendingStateDelivery {
            val envelope = runCatching { WearProtocol.decodeEnvelope(payload) }.getOrNull()
            return PendingStateDelivery(
                payload = payload,
                transport = transport,
                state = envelope?.state,
                eventId = envelope?.eventId,
                generatedAtEpochMs = envelope?.generatedAtEpochMs,
            )
        }
    }
}

internal class StateDeliveryInbox {
    val notifications = Channel<Unit>(Channel.CONFLATED)
    private var pending: PendingStateDelivery? = null

    @Synchronized
    fun offer(incoming: PendingStateDelivery) {
        pending = newerDelivery(pending, incoming)
        notifications.trySend(Unit)
    }

    @Synchronized
    fun poll(): PendingStateDelivery? = pending.also { pending = null }

    fun close() = notifications.close()

    private fun newerDelivery(
        current: PendingStateDelivery?,
        incoming: PendingStateDelivery,
    ): PendingStateDelivery =
        when {
            current == null -> incoming
            current.state == null -> incoming
            incoming.state == null -> current
            shouldAcceptPhoneState(current.state, incoming.state) -> incoming
            else -> current
        }
}

@dagger.hilt.android.AndroidEntryPoint
class StateDataLayerService : WearableListenerService() {
    @javax.inject.Inject internal lateinit var therapyStore: dagger.Lazy<TherapyStateStore>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateSyncMutex = Mutex()
    private val pendingStateDeliveries = StateDeliveryInbox()
    private val wallClockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action in WALL_CLOCK_ACTIONS) requestTimeSensitiveComplicationUpdates()
            }
        }

    override fun onCreate() {
        super.onCreate()
        ensureRuntimeChannel()
        startForegroundRuntime()
        registerReceiver(
            wallClockReceiver,
            IntentFilter().apply { WALL_CLOCK_ACTIONS.forEach(::addAction) },
        )
        requestTimeSensitiveComplicationUpdates()
        scope.launch {
            for (ignored in pendingStateDeliveries.notifications) {
                while (true) persistTherapyState(pendingStateDeliveries.poll() ?: break)
            }
        }
        scope.launch {
            runCatching { WearStartupStateCoordinator.rehydrate(this@StateDataLayerService) }
                .onSuccess { state ->
                    applicationContext.recordWatchDiagnostic(
                        "STARTUP",
                        "STATE-REHYDRATE-200",
                        "Persisted canonical Watch state restored and consumers refreshed",
                        metadata =
                            mapOf(
                                "stateAvailable" to (state != null),
                                "eventTimestamp" to state?.glucose?.measuredAtEpochMs,
                            ),
                    )
                }.onFailure { error ->
                    applicationContext.recordWatchDiagnostic(
                        "STARTUP",
                        "STATE-REHYDRATE-503",
                        "Persisted Watch state could not be rehydrated",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                }
            if (!WearBackgroundAccess.isBatteryUnrestricted(applicationContext)) {
                applicationContext.recordWatchDiagnostic(
                    "RUNTIME",
                    "WATCH-BG-201",
                    "Wear runtime is active while battery optimization is still enabled",
                    DiagnosticSeverity.WARNING,
                )
            }
            runCatching {
                requestLatestState(this@StateDataLayerService)
            }.onSuccess { applicationContext.recordWatchDiagnostic("SYNC", "SYNC-PHONE-100", "Requested latest state from phone") }
                .onFailure { error ->
                    applicationContext.recordWatchDiagnostic(
                        "SYNC",
                        "SYNC-PHONE-503",
                        "Could not request latest state from phone",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                }
        }
    }

    override fun onPeerConnected(peer: Node) {
        super.onPeerConnected(peer)
        scope.launch {
            runCatching { requestLatestState(this@StateDataLayerService, peer.id) }
                .onSuccess {
                    applicationContext.recordWatchDiagnostic(
                        "SYNC",
                        "SYNC-PHONE-102",
                        "Requested canonical snapshot after phone reconnect",
                        metadata = mapOf("nodeId" to peer.id),
                    )
                }.onFailure { error ->
                    applicationContext.recordWatchDiagnostic(
                        "SYNC",
                        "SYNC-PHONE-504",
                        "Canonical snapshot request after reconnect failed",
                        DiagnosticSeverity.WARNING,
                        mapOf("error" to error.javaClass.simpleName),
                    )
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForegroundRuntime()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(wallClockReceiver) }
        pendingStateDeliveries.close()
        scope.cancel()
        super.onDestroy()
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            when (event.dataItem.uri.path) {
                WearProtocol.COMPLICATION_PRESET_PATH ->
                    persistComplicationPreset(event)

                // Old phone versions published preview controls; these never configure a face.
                WearProtocol.COMPLICATION_APPEARANCE_PATH -> Unit

                WearProtocol.WATCH_CONFIG_PATH ->
                    persistWatchConfig(event)

                WearProtocol.WATCH_COLOR_SYNC_PATH ->
                    persistWatchColors(event)

                WearProtocol.STATE_PATH ->
                    persistTherapyState(event)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.STATE_PATH ->
                enqueueTherapyState(event.data, "message")

            WearProtocol.WATCH_FACE_APPLY_PATH ->
                applyWatchFace(event)

            WearProtocol.WATCH_RUNTIME_REQUEST_PATH ->
                scope.launch {
                    sendRuntimeStatus(
                        applicationContext,
                        event.sourceNodeId,
                    )
                }
            WearProtocol.CGM_SETUP_PATH -> configureCgmCollector(event)
            WearProtocol.DIAGNOSTICS_REQUEST_PATH ->
                scope.launch {
                    runCatching { sendWatchDiagnostics(applicationContext, event.sourceNodeId) }
                        .onSuccess { applicationContext.recordWatchDiagnostic("DIAGNOSTICS", "DIAG-SYNC-200", "Diagnostics sent to phone") }
                        .onFailure { error ->
                            applicationContext.recordWatchDiagnostic(
                                "DIAGNOSTICS",
                                "DIAG-SYNC-503",
                                "Diagnostics could not be sent to phone",
                                DiagnosticSeverity.WARNING,
                                mapOf("error" to error.javaClass.simpleName),
                            )
                        }
                }
        }
    }

    private fun configureCgmCollector(event: MessageEvent) {
        val command = runCatching { WearProtocol.decodeCgmSetup(event.data) }.getOrNull()
        if (command == null) {
            scope.launch {
                applicationContext.recordWatchDiagnostic(
                    "G7",
                    "G7-SETUP-401",
                    "Invalid G7 setup command",
                    DiagnosticSeverity.WARNING,
                )
            }
            return
        }
        val intent =
            Intent("app.aapswear.g7watch.CONFIGURE")
                .setComponent(
                    ComponentName(
                        "app.sugarwear",
                        "app.sugarwear.CgmSetupReceiver",
                    ),
                ).putExtra("pairing_code", command.pairingCode)
                .putExtra("sensor_serial", command.sensorSerial)
                .putExtra("gtin", command.gtin)
        sendBroadcast(intent, "app.sugarwear.permission.CONFIGURE_G7")
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "G7",
                "G7-SETUP-200",
                "G7 setup forwarded to collector",
                metadata =
                    mapOf(
                        "serialAvailable" to !command.sensorSerial.isNullOrBlank(),
                        "gtinAvailable" to !command.gtin.isNullOrBlank(),
                    ),
            )
        }
    }

    private fun applyWatchFace(event: MessageEvent) {
        val index =
            event.data
                .decodeToString()
                .toIntOrNull()
                ?.coerceAtLeast(0)
                ?: return

        val appContext = applicationContext
        val sourceNodeId = event.sourceNodeId

        watchFacePushScope.launch {
            watchFacePushMutex.withLock {
                val status =
                    SugarliciousWatchFacePush.apply(
                        appContext,
                        index,
                    )
                val activated = status.equals("Watchface aktiv", ignoreCase = true)

                applicationContext.recordWatchDiagnostic(
                    "WATCHFACE",
                    if (activated) "WATCHFACE-APPLY-200" else "WATCHFACE-APPLY-409",
                    status,
                    if (activated) DiagnosticSeverity.INFO else DiagnosticSeverity.WARNING,
                    mapOf("index" to index),
                )

                runCatching {
                    Wearable
                        .getMessageClient(appContext)
                        .sendMessage(
                            sourceNodeId,
                            WearProtocol.WATCH_FACE_STATUS_PATH,
                            status.encodeToByteArray(),
                        ).await()
                }

                runCatching {
                    sendRuntimeStatus(
                        appContext,
                        sourceNodeId,
                    )
                }
            }
        }
    }

    private fun persistComplicationPreset(event: DataEvent) {
        val dataMap =
            runCatching {
                DataMapItem.fromDataItem(event.dataItem).dataMap
            }.getOrNull() ?: return
        val ids =
            dataMap
                .getIntegerArrayList("ids")
                .orEmpty()
                .filter { it in SugarliciousComplicationIds.all }
                .distinct()
                .take(MAX_PRESET_ITEMS)
        val graphHours =
            dataMap
                .getInt("graphHours", 3)
                .takeIf { it in WearDisplayPreferences.allowedGraphHours }
                ?: 3

        getSharedPreferences(
            COMPLICATION_SETUP_PREFS,
            Context.MODE_PRIVATE,
        ).edit {
            putString(COMPLICATION_PRESET_KEY, ids.joinToString(","))
            putInt(COMPLICATION_GRAPH_HOURS_KEY, graphHours)
        }

        getSharedPreferences(
            WearDisplayPreferences.PREFS,
            Context.MODE_PRIVATE,
        ).edit {
            putInt(
                "complication_graph_hours",
                graphHours,
            )
        }

        requestAllComplicationUpdates()
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "COMPLICATION",
                "COMP-CONFIG-200",
                "Complication preset saved",
                metadata = mapOf("count" to ids.size, "graphHours" to graphHours),
            )
        }
    }

    private fun persistWatchConfig(event: DataEvent) {
        val config =
            runCatching {
                WearProtocol.decodeConfig(
                    event.dataItem.data ?: return,
                )
            }.getOrNull()
        if (config == null) {
            scope.launch {
                applicationContext.recordWatchDiagnostic(
                    "CONFIG",
                    "CONFIG-401",
                    "Invalid Watch configuration",
                    DiagnosticSeverity.WARNING,
                )
            }
            return
        }

        WearDisplayPreferences.save(
            this,
            config,
        )
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "CONFIG",
                "CONFIG-200",
                "Watch configuration saved",
                metadata =
                    mapOf(
                        "graphHours" to config.graphHours,
                        "showPredictions" to config.showPredictions,
                        "dataSource" to config.dataSource,
                    ),
            )
        }
    }

    private fun persistWatchColors(event: DataEvent) {
        val payload = event.dataItem.data ?: return
        val sync = runCatching { WearProtocol.decodeWatchColorSync(payload) }.getOrNull()
        if (sync == null) {
            scope.launch {
                applicationContext.recordWatchDiagnostic(
                    "CONFIG",
                    "COLOR-SYNC-401",
                    "Invalid Mobile graph color payload rejected",
                    DiagnosticSeverity.WARNING,
                )
            }
            return
        }
        WearDisplayPreferences.applySyncedColors(this, sync)
        requestAllComplicationUpdates()
        requestSugarliciousTileUpdates(this)
        sendBroadcast(
            Intent("app.aapswear.g7watch.APPLY_GRAPH_COLORS")
                .setComponent(
                    ComponentName(
                        "app.sugarwear",
                        "app.sugarwear.CgmColorSyncReceiver",
                    ),
                ).putExtra("color_payload", payload),
            "app.sugarwear.permission.CONFIGURE_G7",
        )
        scope.launch {
            applicationContext.recordWatchDiagnostic(
                "CONFIG",
                "COLOR-SYNC-200",
                "Mobile graph colors applied on Wear and forwarded to G7 Collector",
                metadata = mapOf("schemaVersion" to sync.schemaVersion, "sentAtEpochMs" to sync.sentAtEpochMs),
            )
        }
    }

    private fun persistTherapyState(event: DataEvent) {
        enqueueTherapyState(event.dataItem.data, "data_item")
    }

    private fun enqueueTherapyState(
        payload: ByteArray?,
        transport: String,
    ) {
        val bytes = payload ?: return
        pendingStateDeliveries.offer(PendingStateDelivery.decode(bytes, transport))
    }

    private suspend fun persistTherapyState(delivery: PendingStateDelivery) {
        val bytes = delivery.payload
        val transport = delivery.transport
        stateSyncMutex.withLock {
            val deliveryPreferences = getSharedPreferences(STATE_DELIVERY_PREFS, Context.MODE_PRIVATE)
            val fingerprint = statePayloadFingerprint(bytes)
            val committed =
                StatePayloadFingerprint(
                    size = deliveryPreferences.getInt(STATE_PAYLOAD_SIZE, -1),
                    sha256Hex = deliveryPreferences.getString(STATE_PAYLOAD_SHA256, null).orEmpty(),
                ).takeIf { it.size >= 0 && it.sha256Hex.isNotEmpty() }
            if (fingerprint == committed) return@withLock

            getSharedPreferences("diagnostics", Context.MODE_PRIVATE).edit { putLong("wearReceivedAt", System.currentTimeMillis()) }
            val incoming = delivery.state
            if (incoming == null) {
                applicationContext.recordWatchDiagnostic(
                    "SOURCE",
                    "SRC-PHONE-401",
                    "Invalid phone state payload",
                    DiagnosticSeverity.WARNING,
                    mapOf("transport" to transport),
                )
                return@withLock
            }
            val store = therapyStore.get()
            val old = store.state.first()
            val now = System.currentTimeMillis()

            // MessageClient and DataClient intentionally carry the same state. They are
            // independent transports, so a delayed durable DataItem can arrive after a newer
            // low-latency message. Never let that delayed copy roll the Watch backwards.
            if (!shouldAcceptPhoneState(old, incoming)) {
                applicationContext.recordWatchDiagnostic(
                    "SYNC",
                    "SYNC-PHONE-202",
                    "Older phone state ignored on Watch",
                    metadata =
                        mapOf(
                            "transport" to transport,
                            "incomingReceivedAt" to incoming.receivedAtEpochMs,
                            "storedReceivedAt" to (old?.receivedAtEpochMs ?: 0L),
                        ),
                )
                return@withLock
            }

            val historyInputs =
                buildList {
                    addAll(old?.glucoseHistory.orEmpty())
                    addAll(incoming.glucoseHistory)
                    incoming.glucose?.let {
                        add(
                            GlucoseSample(
                                valueMgDl = it.valueMgDl,
                                measuredAtEpochMs = it.measuredAtEpochMs,
                                source = it.source,
                                sensorId = it.sensorId,
                                sessionId = it.sessionId,
                                sequenceNumber = it.sequenceNumber,
                                receivedAtEpochMs = it.receivedAtEpochMs,
                                quality = it.quality,
                            ),
                        )
                    }
                }

            val history =
                CanonicalCgmHistory.merge(
                    samples = historyInputs,
                    nowEpochMs = now,
                    preferredSource = incoming.source,
                    windowMs = HISTORY_WINDOW_MS,
                    futureToleranceMs = FUTURE_TOLERANCE_MS,
                    maxPoints = MAX_HISTORY_POINTS,
                )

            val merged =
                PersistentPredictionCache
                    .merge(
                        previous = old,
                        incoming = incoming.copy(glucoseHistory = history),
                        nowEpochMs = now,
                    ).withHistoryDerivedDelta(app.sugarlicious.model.TrendRateProfile.SUGARLICIOUS)
            if (!hasMeaningfulPhoneStateChange(old, merged)) return@withLock

            val selectedSource = WearDisplayPreferences.read(this@StateDataLayerService).dataSource
            val canonicalForAlerts =
                DirectSensorReadingResolver.resolve(
                    context = this@StateDataLayerService,
                    fallback = merged,
                    nowEpochMs = now,
                    dataSource = selectedSource,
                )
            publishCgmAlertMode(this@StateDataLayerService, selectedSource, canonicalForAlerts)
            applicationContext.recordWatchDiagnostic(
                "PREDICTION",
                if (incoming.glucosePredictions.isEmpty() &&
                    merged.glucosePredictions.isNotEmpty()
                ) {
                    "PRED-CACHE-203"
                } else {
                    "PRED-DATA-200"
                },
                if (incoming.glucosePredictions.isEmpty() &&
                    merged.glucosePredictions.isNotEmpty()
                ) {
                    "Cached predictions retained on Watch"
                } else {
                    "Phone state merged on Watch"
                },
                metadata =
                    mapOf(
                        "incomingPredictions" to incoming.glucosePredictions.size,
                        "displayPredictions" to merged.glucosePredictions.size,
                        "historyCount" to history.size,
                        "transport" to transport,
                    ),
            )
            store.save(merged)
            deliveryPreferences.edit {
                putInt(STATE_PAYLOAD_SIZE, fingerprint.size)
                putString(STATE_PAYLOAD_SHA256, fingerprint.sha256Hex)
            }
            getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
                .edit {
                    putLong("wearCommittedAt", System.currentTimeMillis())
                        .putString("wearEventId", delivery.eventId)
                        .putLong("wearGeneratedAt", delivery.generatedAtEpochMs ?: 0L)
                }
            WearSurfaceUpdateCoordinator.dispatch(
                updateApp = { WearCanonicalStateEvents.publishLocalReadingUpdate() },
                updateComplications = {
                    requestComplicationUpdates(
                        ComplicationUpdatePlanner.affectedProviders(old, merged),
                    )
                },
                updateTiles = {
                    requestSugarliciousTileUpdates(
                        this@StateDataLayerService,
                        affectedSugarliciousTiles(old, merged),
                    )
                },
            )
            applicationContext.recordWatchDiagnostic(
                "SYNC",
                if (transport == "message") "SYNC-PHONE-201" else "SYNC-PHONE-200",
                if (transport == "message") "Immediate phone state applied on Watch" else "Durable phone state applied on Watch",
                metadata = mapOf("transport" to transport),
            )
        }
    }

    private suspend fun sendRuntimeStatus(
        context: Context,
        nodeId: String,
    ) {
        val status =
            WatchRuntimeStatus(
                activeSugarliciousFaceIndex =
                    SugarliciousWatchFacePush
                        .activeFaceIndex(context),
                activeComplicationIds =
                    ActiveComplicationRegistry
                        .activeCatalogIds(context),
                sentAtEpochMs =
                    System.currentTimeMillis(),
            )

        Wearable
            .getMessageClient(context)
            .sendMessage(
                nodeId,
                WearProtocol.WATCH_RUNTIME_STATUS_PATH,
                WearProtocol.encodeRuntimeStatus(status),
            ).await()
    }

    private fun requestAllComplicationUpdates() {
        requestComplicationUpdates(ComplicationUpdatePlanner.allManagedProviders)
    }

    private fun requestTimeSensitiveComplicationUpdates() = requestComplicationUpdates(ComplicationUpdatePlanner.timeSensitiveProviders)

    private fun requestComplicationUpdates(providers: List<Class<*>>) {
        providers.forEach { provider ->
            ActiveProviderUpdates.request(this, provider)
        }
        if (providers.isNotEmpty()) {
            getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
                .edit {
                    putLong("complicationUpdatedAt", System.currentTimeMillis())
                }
        }
    }

    private fun ensureRuntimeChannel() {
        val channel =
            NotificationChannel(
                RUNTIME_CHANNEL,
                "Sugarlicious Wear Dauerbetrieb",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Permanenter Sugarlicious Wear Datenempfang"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundRuntime() {
        val notification = runtimeNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                RUNTIME_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(RUNTIME_NOTIFICATION_ID, notification)
        }
    }

    internal fun runtimeNotification(): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, WearActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val batteryUnrestricted = WearBackgroundAccess.isBatteryUnrestricted(this)
        return Notification
            .Builder(this, RUNTIME_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sugarlicious Wear")
            .setContentText(
                if (batteryUnrestricted) {
                    "Dauerbetrieb aktiv"
                } else {
                    "Akkuoptimierung aktiv – Dauerbetrieb freigeben"
                },
            ).setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    companion object {
        const val ACTION_START_RUNTIME = "app.aapswear.wear.START_RUNTIME"
        internal const val RUNTIME_CHANNEL = "sugarlicious_wear_runtime"
        internal const val RUNTIME_NOTIFICATION_ID = 6101
        private val WALL_CLOCK_ACTIONS = setOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

        fun start(context: Context) {
            val app = context.applicationContext
            app.startForegroundService(
                Intent(app, StateDataLayerService::class.java)
                    .setAction(ACTION_START_RUNTIME),
            )
        }

        private val watchFacePushScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO,
            )
        private val watchFacePushMutex = Mutex()

        private const val HISTORY_WINDOW_MS =
            24 * 60 * 60_000L
        private const val FUTURE_TOLERANCE_MS =
            5 * 60_000L
        private const val MAX_HISTORY_POINTS = 300

        private const val COMPLICATION_SETUP_PREFS =
            "complication_setup"
        private const val COMPLICATION_PRESET_KEY =
            "selected_ids"
        private const val COMPLICATION_GRAPH_HOURS_KEY =
            "graph_hours"
        private const val MAX_PRESET_ITEMS = 8
        private const val STATE_DELIVERY_PREFS = "wear_state_delivery"
        private const val STATE_PAYLOAD_SIZE = "committed_payload_size"
        private const val STATE_PAYLOAD_SHA256 = "committed_payload_sha256"
    }
}

suspend fun requestLatestState(context: Context): Int {
    val nodes =
        Wearable
            .getNodeClient(context)
            .connectedNodes
            .await()

    nodes.forEach { node -> runCatching { requestLatestState(context, node.id) } }

    return nodes.size
}

internal suspend fun requestLatestState(
    context: Context,
    nodeId: String,
) {
    Wearable
        .getMessageClient(context)
        .sendMessage(nodeId, WearProtocol.REQUEST_PATH, byteArrayOf())
        .await()
    runCatching {
        Wearable
            .getMessageClient(context)
            .sendMessage(nodeId, WearProtocol.WATCH_CONFIG_REQUEST_PATH, byteArrayOf())
            .await()
    }
}
