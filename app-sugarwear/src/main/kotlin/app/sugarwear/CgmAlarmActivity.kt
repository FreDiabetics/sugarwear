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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.sugarlicious.model.Trend
import app.sugarlicious.sensor.protocol.CgmAlarmType
import app.sugarlicious.sensor.protocol.CgmReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun cgmAlarmHeadline(type: CgmAlarmType): String = "${cgmAlarmTitle(type)} Alarm!"

internal fun cgmAlarmValue(reading: CgmReading?, now: Long = System.currentTimeMillis()): String {
    if (reading == null) return "Keine aktuellen Daten"
    val arrow =
        when (reading.trend) {
            Trend.DOUBLE_DOWN -> "↓↓"
            Trend.SINGLE_DOWN -> "↓"
            Trend.FORTY_FIVE_DOWN -> "↘"
            Trend.FLAT -> "→"
            Trend.FORTY_FIVE_UP -> "↗"
            Trend.SINGLE_UP -> "↑"
            Trend.DOUBLE_UP -> "↑↑"
            Trend.UNKNOWN -> ""
        }
    val value = "${kotlin.math.round(reading.glucoseMgDl).toInt()} $arrow mg/dL"
    return if (now - reading.timestampEpochMs in 0 until 15 * 60_000L) value else "Letzter Wert: $value"
}

internal fun cgmAlarmReadingText(context: Context): String = cgmAlarmValue(CgmSensorStateStore(context).read().lastReading)

/** Delivery choices apply to this alarm occurrence, never to other alarm types or sensor data. */
internal object CgmAlarmDelivery {
    const val ACTION = "app.sugarwear.ALARM_DELIVERY"

    private fun prefs(context: Context) = context.getSharedPreferences("cgm_alarm_delivery", Context.MODE_PRIVATE)

    fun mode(context: Context, type: CgmAlarmType): String? = prefs(context).getString(type.name, null)

    fun snoozeMinutes(context: Context, type: CgmAlarmType): Int = prefs(context).getInt("duration_${type.name}", 10).coerceIn(5, 120)

    fun setSnoozeMinutes(context: Context, type: CgmAlarmType, minutes: Int) {
        prefs(context).edit { putInt("duration_${type.name}", minutes.coerceIn(5, 120)) }
        CgmAlarmCoordinator.refreshNotification(context, type)
    }

    fun reset(context: Context, type: CgmAlarmType) = prefs(context).edit { remove(type.name) }

    fun pending(context: Context, type: CgmAlarmType, operation: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7_600 + type.ordinal * 4 + listOf("snooze", "mute", "vibrate", "ack").indexOf(operation),
            Intent(context, CgmAlarmAcknowledgeReceiver::class.java)
                .setAction(ACTION)
                .putExtra(CgmAlarmAcknowledgeReceiver.EXTRA_TYPE, type.name)
                .putExtra("operation", operation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun perform(context: Context, type: CgmAlarmType, operation: String) =
        synchronized(CgmSensorStateStore.lifecycleLock) {
            if (!CgmAlarmCoordinator.isVisible(context, type)) return@synchronized
            when (operation) {
                "ack" -> CgmAlarmCoordinator.acknowledge(context, type)
                "snooze" -> CgmAlarmCoordinator.snooze(context, type)
                "mute", "vibrate" -> {
                    prefs(context).edit { putString(type.name, operation) }
                    CgmAlarmAudioService.stop(context, type)
                    context.getSystemService(android.os.Vibrator::class.java)?.cancel()
                    if (operation == "vibrate") {
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

internal data class CgmAlarmScreenState(
    val type: CgmAlarmType? = null,
    val visible: Boolean = false,
    val active: Boolean = false,
    val triggerGlucose: Double? = null,
    val triggeredAtEpochMs: Long = 0L,
    val currentReading: CgmReading? = null,
    val snoozeMinutes: Int = 10,
)

@dagger.hilt.android.lifecycle.HiltViewModel
class CgmAlarmViewModel
    @javax.inject.Inject
    constructor(
        app: Application,
    ) : AndroidViewModel(app) {
        private val mutable = MutableStateFlow(CgmAlarmScreenState())
        internal val state = mutable.asStateFlow()
        private var selected: CgmAlarmType? = null
        private val preferences = listOf("g7_cgm_alarm_state", "g7_collector_state", "cgm_alarm_delivery").map { app.getSharedPreferences(it, Context.MODE_PRIVATE) }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

        init {
            preferences.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        }

        internal fun select(type: CgmAlarmType) {
            selected = type
            refresh()
        }

        private fun refresh() {
            val type = selected ?: return
            viewModelScope.launch {
                val context = getApplication<Application>()
                val next =
                    withContext(Dispatchers.IO) {
                        val pending = CgmAlarmCoordinator.pendingNotices(context).firstOrNull { it.type == type }
                        val active = CgmAlarmCoordinator.isActive(context, type)
                        val visible = pending != null
                        val currentReading = CgmSensorStateStore(context).read().lastReading
                        CgmAlarmScreenState(
                            type = type,
                            visible = visible,
                            active = active,
                            triggerGlucose = pending?.triggerGlucoseMgDl,
                            triggeredAtEpochMs = pending?.triggeredAtEpochMs ?: 0L,
                            currentReading = currentReading,
                            snoozeMinutes = CgmAlarmDelivery.snoozeMinutes(context, type),
                        )
                    }
                if (selected == type) mutable.value = next
            }
        }

        internal fun act(operation: String): Boolean {
            val type = selected ?: return false
            return try {
                CgmAlarmDelivery.pending(getApplication(), type, operation).send()
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
    reading?.let { "${kotlin.math.round(it.glucoseMgDl).toInt()}" } ?: "---"

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

/** Full-screen intent destination; no direct Bluetooth or sensor lifecycle operations. */
@dagger.hilt.android.AndroidEntryPoint
class CgmAlarmActivity : ComponentActivity() {
    private val model by viewModels<CgmAlarmViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        select(intent)
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            LaunchedEffect(state.type, state.visible) { if (state.type != null && !state.visible) finish() }
            val type = state.type ?: return@setContent
            val accent = if (type in setOf(CgmAlarmType.HIGH, CgmAlarmType.VERY_HIGH, CgmAlarmType.RAPID_RISE)) Color(0xFFFFD040) else Color(0xFFFF5C69)
            val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top screen viewport: ONLY 3 centered items fill this section
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(screenHeight)
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // Item 1: Large Alarm Icon (ic_alert)
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(app.sugarlicious.uishared.R.drawable.ic_alert),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent),
                    )

                    // Item 2: Glucose Value + Trend Arrow Icon (side-by-side), mg/dL below
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            BasicText(
                                cgmGlucoseOnlyNumberText(state.currentReading),
                                style = TextStyle(color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                            )
                            val trendRes = trendDrawableRes(state.currentReading?.trend)
                            if (trendRes != null) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(trendRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                                )
                            }
                        }
                        BasicText(
                            "mg/dL",
                            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                        )
                    }

                    // Item 3: Large Alarm Type Title
                    BasicText(
                        cgmAlarmHeadline(type),
                        style = TextStyle(color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                }

                // Scroll down content (controls & details)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText(
                        "Quittieren",
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF242424))
                            .clickable { if (model.act("ack")) finish() }
                            .padding(vertical = 12.dp),
                        style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("−" to -5, "+" to 5).forEachIndexed { index, (label, delta) ->
                            if (index == 1) BasicText("${state.snoozeMinutes} Min.", style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                            BasicText(
                                label,
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF242424))
                                    .clickable { model.adjustSnooze(delta) }
                                    .padding(vertical = 10.dp),
                                style = TextStyle(color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                    BasicText(
                        "Schlummern · ${state.snoozeMinutes} Min.",
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF22EB60))
                            .clickable {
                                if (model.act("snooze")) finish()
                            }.padding(vertical = 12.dp),
                        style = TextStyle(color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Stumm" to "mute", "Vibrieren" to "vibrate").forEach { (label, action) ->
                            BasicText(
                                label,
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF242424))
                                    .clickable {
                                        if (model.act(action)) finish()
                                    }.padding(vertical = 12.dp),
                                style = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        select(intent)
    }

    private fun select(intent: Intent) {
        val type = intent.getStringExtra("type")?.let { runCatching { CgmAlarmType.valueOf(it) }.getOrNull() }
        if (type == null) finish() else model.select(type)
    }

    companion object {
        internal fun intent(context: Context, type: CgmAlarmType) = Intent(context, CgmAlarmActivity::class.java).putExtra("type", type.name)
    }
}
