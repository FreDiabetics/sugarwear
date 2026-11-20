package app.sugarwear

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun cgmAlarmHeadline(type: SugarWearAlarmType): String =
    if (type in setOf(SugarWearAlarmType.RAPID_RISE, SugarWearAlarmType.RAPID_FALL, SugarWearAlarmType.TECHNICAL_WARNING)) cgmAlarmTitle(type) else "${cgmAlarmTitle(type)} Alarm!"

internal fun cgmAlarmValue(reading: CgmReading?, now: Long = System.currentTimeMillis()): String {
    if (reading == null) return "Keine aktuellen Daten"
    if (reading.status != app.sugarlicious.sensor.protocol.CgmReadingStatus.VALID || !reading.glucoseMgDl.isFinite()) return "Keine aktuellen Daten"
    val value = "${kotlin.math.round(reading.glucoseMgDl).toInt()} mg/dL"
    return if ((now - reading.timestampEpochMs) in 0 until (15 * 60_000L)) value else "Letzter Wert: $value"
}

internal fun cgmAlarmReadingText(context: Context): String = cgmAlarmValue(CgmSensorStateStore(context).read().lastReading)

/** Delivery choices apply to this alarm occurrence, never to other alarm types or sensor data. */
internal object CgmAlarmDelivery {
    const val ACTION = "app.sugarwear.ALARM_DELIVERY"

    private fun prefs(context: Context) = context.getSharedPreferences("cgm_alarm_delivery", Context.MODE_PRIVATE)

    fun mode(context: Context, type: SugarWearAlarmType): String? = prefs(context).getString(type.id, null)

    private const val DEFAULT_QUICK_SNOOZE_MINUTES = 10

    fun snoozeMinutes(context: Context, type: SugarWearAlarmType, now: Long = System.currentTimeMillis()): Int {
        val profile = CgmAlarmSettingsStore.read(context).activeProfile(now).profile
        return prefs(context).getInt(durationKey(type, profile), DEFAULT_QUICK_SNOOZE_MINUTES).coerceIn(5, 120)
    }

    private fun durationKey(type: SugarWearAlarmType, profile: SugarWearAlarmProfile): String =
        if (profile == SugarWearAlarmProfile.NIGHT) "night_duration_${type.id}" else "duration_${type.id}"

    fun setSnoozeMinutes(context: Context, type: SugarWearAlarmType, minutes: Int) {
        val profile = CgmAlarmSettingsStore.read(context).activeProfile(System.currentTimeMillis()).profile
        prefs(context).edit { putInt(durationKey(type, profile), minutes.coerceIn(5, 120)) }
        CgmAlarmCoordinator.refreshNotification(context, type)
    }

    fun reset(context: Context, type: SugarWearAlarmType) = prefs(context).edit { remove(type.id) }

    fun pending(context: Context, type: SugarWearAlarmType, operation: String, episode: String? = CgmAlarmCoordinator.pendingNotices(context).firstOrNull { it.type == type }?.let(::cgmAlarmEpisodeKey)): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7_600 + SugarWearAlarmDefaults.priority(type) * 4 + listOf("snooze", "mute", "vibrate", "ack").indexOf(operation),
            Intent(context, CgmAlarmAcknowledgeReceiver::class.java)
                .setAction(ACTION)
                .setData("sugarwear:delivery/${android.net.Uri.encode(episode.orEmpty())}/$operation".toUri())
                .putExtra("episode", episode)
                .putExtra(CgmAlarmAcknowledgeReceiver.EXTRA_TYPE, type.name)
                .putExtra("operation", operation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun perform(context: Context, type: SugarWearAlarmType, operation: String, episode: String? = null) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (!CgmAlarmCoordinator.isVisible(context, type)) return@synchronized
            if (episode != null && !CgmAlarmCoordinator.matchesEpisode(context, type, episode)) return@synchronized
            when (operation) {
                "ack" -> CgmAlarmCoordinator.acknowledge(context, type)
                "snooze" -> CgmAlarmCoordinator.snooze(context, type)
                "mute", "vibrate" -> {
                    prefs(context).edit { putString(type.id, operation) }
                    CgmAlarmAudioService.stop(context, type)
                    context.getSystemService(android.os.Vibrator::class.java)?.cancel()
                    if (operation == "vibrate" && !CgmAlarmSettingsStore.read(context).globalMuteAll) {
                        context.getSystemService(android.os.Vibrator::class.java)?.vibrate(
                            android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1),
                            android.os.VibrationAttributes
                                .Builder()
                                .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                                .build(),
                        )
                    }
                }
            }
        }
}

internal fun cgmAlarmEpisodeKey(alarm: SugarWearAlarmInstance): String =
    listOf(alarm.type.id, alarm.sensorId.orEmpty(), alarm.sessionId.orEmpty(), alarm.triggeredAtEpochMs.toString(), alarm.conditionFirstMetAtEpochMs.toString()).joinToString("|") { android.net.Uri.encode(it) }

internal data class CgmAlarmScreenState(
    val type: SugarWearAlarmType? = null,
    val visible: Boolean = false,
    val active: Boolean = false,
    val triggerGlucose: Double? = null,
    val triggerTrend: Trend? = null,
    val triggeredAtEpochMs: Long = 0L,
    val currentReading: CgmReading? = null,
    val snoozeMinutes: Int = 10,
    val episode: String? = null,
    val glucoseColor: Int = android.graphics.Color.WHITE,
    val trendStyle: app.sugarlicious.model.TrendArrowRenderSpec? = null,
)

@dagger.hilt.android.lifecycle.HiltViewModel
class CgmAlarmViewModel
    @javax.inject.Inject
    constructor(
        app: Application,
    ) : AndroidViewModel(app) {
        private val mutable = MutableStateFlow(CgmAlarmScreenState())
        internal val state = mutable.asStateFlow()
        private var selected: SugarWearAlarmType? = null
        private var selectedEpisode: String? = null
        private var refreshRevision = 0L
        private val preferences = listOf("g7_cgm_alarm_state", "g7_collector_state", "cgm_alarm_delivery").map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

        init {
            preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        }

        internal fun select(type: SugarWearAlarmType, episode: String? = null) {
            selectedEpisode = episode
            selected = type
            refresh()
        }

        internal fun refresh() {
            val type = selected ?: return
            val selection = selectedEpisode
            val revision = ++refreshRevision
            viewModelScope.launch {
                val context = getApplication<Application>()
                val next =
                    withContext(Dispatchers.IO) {
                        val pending = CgmAlarmCoordinator.pendingNotices(context).firstOrNull { it.type == type }
                        val active = CgmAlarmCoordinator.isActive(context, type)
                        val visible = pending != null && (selectedEpisode == null || selectedEpisode == cgmAlarmEpisodeKey(pending))
                        val sensorState = CgmSensorStateStore(context).read()
                        val now = System.currentTimeMillis()
                        val currentReading = sensorState.lastReading?.takeIf { cgmReadingBelongsToSession(sensorState, it) && it.status == app.sugarlicious.sensor.protocol.CgmReadingStatus.VALID && !it.displayOnly && it.timestampEpochMs <= now && now - it.timestampEpochMs < CGM_SIGNAL_LOSS_AFTER_MS }
                        val appearance = CgmAppearanceStore(context)
                        val palette = appearance.load()
                        val thresholds = CgmGraphColorStore(context).readThresholds()
                        val role =
                            when (currentReading?.let { thresholds.classify(it.glucoseMgDl) }) {
                                app.sugarlicious.model.CgmRangeClass.VERY_LOW, app.sugarlicious.model.CgmRangeClass.LOW -> CgmAppearanceRole.GLUCOSE_LOW
                                app.sugarlicious.model.CgmRangeClass.VERY_HIGH, app.sugarlicious.model.CgmRangeClass.HIGH -> CgmAppearanceRole.GLUCOSE_HIGH
                                app.sugarlicious.model.CgmRangeClass.IN_RANGE -> CgmAppearanceRole.GLUCOSE_IN_RANGE
                                null -> CgmAppearanceRole.GLUCOSE_NO_SOURCE
                            }
                        CgmAlarmScreenState(
                            type = type,
                            episode = pending?.let(::cgmAlarmEpisodeKey),
                            glucoseColor = palette.argb(role),
                            trendStyle = appearance.trendArrowStyle().renderSpec(),
                            visible = visible,
                            active = active,
                            triggerGlucose = pending?.triggerGlucoseMgDl,
                            triggerTrend = pending?.triggerTrend?.let { name -> Trend.entries.firstOrNull { it.name == name } },
                            triggeredAtEpochMs = pending?.triggeredAtEpochMs ?: 0L,
                            currentReading = currentReading,
                            snoozeMinutes = CgmAlarmDelivery.snoozeMinutes(context, type),
                        )
                    }
                if (selected == type && selectedEpisode == selection && revision == refreshRevision) mutable.value = next
            }
        }

        internal fun act(operation: String): Boolean {
            val type = selected ?: return false
            val episode = state.value.episode ?: return false
            return try {
                CgmAlarmDelivery.pending(getApplication(), type, operation, episode).send()
                true
            } catch (error: PendingIntent.CanceledException) {
                android.util.Log.e("CgmAlarmAction", "Alarm action could not be delivered", error)
                false
            }
        }

        internal fun adjustSnooze(delta: Int) {
            val type = selected ?: return
            viewModelScope.launch(Dispatchers.IO) {
                synchronized(CgmSensorStateStore.lifecycleLock) {
                    CgmAlarmDelivery.setSnoozeMinutes(getApplication(), type, CgmAlarmDelivery.snoozeMinutes(getApplication(), type) + delta)
                }
            }
        }

        override fun onCleared() {
            preferences.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

internal fun cgmGlucoseNumberText(reading: CgmReading?): String =
    reading?.let { "${kotlin.math.round(it.glucoseMgDl).toInt()} mg/dL" } ?: "---"

internal fun cgmGlucoseOnlyNumberText(reading: CgmReading?): String =
    reading?.let {
        kotlin.math
            .round(it.glucoseMgDl)
            .toInt()
            .toString()
    } ?: "---"

internal fun trendDrawableRes(trend: Trend?): Int? =
    when (trend) {
        Trend.DOUBLE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_doubledown
        Trend.SINGLE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_down
        Trend.FORTY_FIVE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_fortyfivedown
        Trend.FLAT -> app.sugarlicious.uishared.R.drawable.ic_trend_flat
        Trend.FORTY_FIVE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_fortyfiveup
        Trend.SINGLE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_up
        Trend.DOUBLE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_doubleup
        null, Trend.UNKNOWN -> null
    }

@androidx.compose.runtime.Composable
internal fun CgmAlarmScreenContent(
    state: CgmAlarmScreenState,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit,
    onAdjustSnooze: (Int) -> Unit,
    onMute: () -> Unit,
    onVibrate: () -> Unit,
    visualStyle: app.sugarwear.ui.visual.AlarmVisualStyle =
        app.sugarwear.ui.visual.VisualStyleDefaults
            .alarm(),
) {
    val type = state.type ?: return
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

    Column(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val artwork = cgmAlarmArtwork(type) ?: return
        // Only the full-screen high/low alarm uses the persisted trigger snapshot.
        // The live glucose tile and all other alert surfaces retain their own data flow.
        val frozenThresholdAlert =
            type in
                setOf(
                    SugarWearAlarmType.HIGH,
                    SugarWearAlarmType.LOW,
                    SugarWearAlarmType.URGENT_LOW,
                    SugarWearAlarmType.URGENT_LOW_SOON,
                )
        val artworkReading = if (frozenThresholdAlert) null else state.currentReading
        androidx.compose.runtime.key(type, artwork, artworkReading, state.triggerGlucose, state.triggerTrend, state.glucoseColor, state.trendStyle) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = {
                    CgmAlarmFullBleedView(
                        it,
                        artwork,
                        artworkReading,
                        state.glucoseColor,
                        state.trendStyle,
                        titleOverride = if (type == SugarWearAlarmType.TECHNICAL_WARNING) cgmAlarmHeadline(type) else null,
                        frozenGlucoseMgDl = if (frozenThresholdAlert) state.triggerGlucose else null,
                        frozenTrend = if (frozenThresholdAlert) state.triggerTrend else null,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(screenHeight),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = visualStyle.contentPaddingDp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(visualStyle.buttonSpacingDp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("−" to -5, "+" to 5).forEachIndexed { index, (label, delta) ->
                    if (index == 1) BasicText("${state.snoozeMinutes} Min.", style = TextStyle(color = visualStyle.primaryTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    BasicText(
                        label,
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(visualStyle.buttonBackgroundColor)
                            .clickable { onAdjustSnooze(delta) }
                            .padding(vertical = 10.dp),
                        style = TextStyle(color = visualStyle.primaryTextColor, fontSize = 20.sp, textAlign = TextAlign.Center),
                    )
                }
            }
            BasicText(
                "Schlummern · ${state.snoozeMinutes} Min.",
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(visualStyle.buttonCornerRadiusDp))
                    .background(visualStyle.snoozeButtonBackgroundColor)
                    .clickable { onSnooze() }
                    .padding(vertical = 12.dp),
                style = TextStyle(color = visualStyle.snoozeTextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Stumm" to onMute, "Vibrieren" to onVibrate).forEach { (label, action) ->
                    BasicText(
                        label,
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(visualStyle.buttonCornerRadiusDp))
                            .background(visualStyle.buttonBackgroundColor)
                            .clickable { action() }
                            .padding(vertical = 12.dp),
                        style = TextStyle(color = visualStyle.primaryTextColor, fontSize = 14.sp, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

/** Full-screen intent destination; no direct Bluetooth or sensor lifecycle operations. */
@dagger.hilt.android.AndroidEntryPoint
class CgmAlarmActivity : ComponentActivity() {
    private val model by viewModels<CgmAlarmViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyCgmAlarmFullBleedChrome(window)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        select(intent)
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            LaunchedEffect(state.type, state.visible) { if (state.type != null && !state.visible) finish() }
            LaunchedEffect(state.currentReading) {
                state.currentReading?.let {
                    kotlinx.coroutines.delay((it.timestampEpochMs + CGM_SIGNAL_LOSS_AFTER_MS - System.currentTimeMillis()).coerceAtLeast(1L))
                    model.refresh()
                }
            }
            CgmAlarmScreenContent(
                state = state,
                onAcknowledge = { if (model.act("ack")) finish() },
                onSnooze = { if (model.act("snooze")) finish() },
                onAdjustSnooze = { model.adjustSnooze(it) },
                onMute = { if (model.act("mute")) finish() },
                onVibrate = { if (model.act("vibrate")) finish() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        select(intent)
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }

    private fun select(intent: Intent) {
        val type = intent.getStringExtra("type")?.let { runCatching { SugarWearAlarmType.valueOf(it) }.getOrNull() }
        if (type == null) finish() else model.select(type, intent.getStringExtra("episode"))
    }

    companion object {
        internal fun intent(context: Context, type: SugarWearAlarmType) = Intent(context, CgmAlarmActivity::class.java).putExtra("type", type.name)
    }
}
