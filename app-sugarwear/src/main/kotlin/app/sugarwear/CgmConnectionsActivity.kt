package app.sugarwear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import app.sugarlicious.sensor.protocol.CgmSensorState

/** Home for sensor management and outgoing integrations, without a second collector. */
@dagger.hilt.android.AndroidEntryPoint
class CgmConnectionsActivity : CgmSettingsComposeActivity() {
    private val status by viewModels<CgmSystemStatusViewModel>()
    private var scrollView: ScrollView? = null
    private val sensorStatePreferences by lazy {
        listOf(CGM_COLLECTOR_PREFERENCES, CgmAlarmCoordinator.PREFERENCES)
            .map { getSharedPreferences(it, Context.MODE_PRIVATE) }
    }
    private val sensorStateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> status.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(status.state, render = ::render)
        render()
    }

    override fun onStart() {
        super.onStart()
        sensorStatePreferences.forEach { it.registerOnSharedPreferenceChangeListener(sensorStateListener) }
    }

    override fun onStop() {
        sensorStatePreferences.forEach { it.unregisterOnSharedPreferenceChangeListener(sensorStateListener) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        status.refresh()
        render()
    }

    private fun render() {
        val loaded = status.state.value
        if (!settingsReady(CgmSettingsIoState(loaded.snapshot, loaded.loading, loaded.error), status::refresh)) return
        val snapshot = checkNotNull(loaded.snapshot)
        val palette = snapshot.palette
        val background = palette.settingsArgb(CgmAppearanceRole.MENU_BACKGROUND)
        applyCgmSystemChrome(window, background)
        val oldScrollY = scrollView?.scrollY ?: 0
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                app.sugarlicious.uishared.WearSettingsUi
                    .pagePadding(this)
                setBackgroundColor(background)
                addView(cgmSettingsHeader("Verbindungen", palette), fullWidth())
            }

        val state = snapshot.sensor
        val now = System.currentTimeMillis()
        val sensorIcon = cgmConnectionsSensorIcon(state, now, snapshot.sensorNotices)
        val sensorCard = card(palette)
        sensorCard.apply {
            val sensor = state.sensor
            val details =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("SENSOR", 9f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY), true))
                    addView(
                        label(
                            sensor?.deviceName?.takeIf { it.isNotBlank() } ?: if (sensor == null) "Kein Sensor verbunden" else "Dexcom G7",
                            14f,
                            palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                            true,
                        ),
                    )
                    if (sensor != null) {
                        addView(
                            label(
                                cgmConnectionsSensorStatus(state, now),
                                10f,
                                palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY),
                            ),
                        )
                    }
                }
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(
                        ImageView(context).apply {
                            setImageResource(sensorIcon)
                            if (sensorIcon == R.drawable.ic_sensor_outline) {
                                setColorFilter(palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY))
                            }
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginStart = 8.dp },
                    )
                },
                fullWidth(),
            )
            val lifetime = cgmSensorLifetime(state, now)
            addView(
                segments(
                    remaining = lifetime?.remainingDaySegments ?: 0,
                    known = lifetime?.hasVerifiedEnd == true,
                    graceAvailable =
                        lifetime?.let { it.hasVerifiedGraceEnd && !it.graceExpired } == true &&
                            sensor?.state != CgmSensorState.ENDED,
                ),
                cardSpacing(12),
            )
            if (sensor != null) {
                addView(
                    label(
                        when {
                            lifetime?.hasVerifiedEnd != true -> "Verbleibende Laufzeit unbekannt"
                            sensor.state == CgmSensorState.ENDED || lifetime.graceExpired -> "Sensorlaufzeit beendet"
                            lifetime.expired && !lifetime.inGracePeriod -> "Reguläre Sensorlaufzeit beendet"
                            else -> lifetime.compactRemainingTime()
                        },
                        11f,
                        palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                        true,
                    ),
                    cardSpacing(9),
                )
            }
            if (sensor?.state != CgmSensorState.ENDED) {
                lifetime?.toleranceTimeText()?.let { tolerance ->
                    addView(
                        label(tolerance, 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)),
                        cardSpacing(5),
                    )
                }
            }
            tag = if (sensor == null) "connections-start-pairing" else "connections-manage-sensor"
            isFocusable = true
            setOnClickListener {
                if (sensor == null) {
                    startActivity(
                        Intent(this@CgmConnectionsActivity, CgmWatchActivity::class.java)
                            .putExtra(CgmWatchActivity.EXTRA_OPEN_PAIRING, true)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    )
                } else {
                    startActivity(Intent(this@CgmConnectionsActivity, CgmSystemStatusActivity::class.java))
                }
            }
        }
        root.addView(sensorCard, cardSpacing(10))

        root.addView(
            card(palette).apply {
                tag = "connections-share"
                minimumHeight = 48.dp
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                addView(label("Follower Funktion", 14f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                setOnClickListener {
                    startActivity(Intent(this@CgmConnectionsActivity, CgmShareSettingsActivity::class.java))
                }
            },
            cardSpacing(10),
        )
        root.addView(
            card(palette).apply {
                tag = "connections-health-placeholder"
                addView(label("Health Connect", 14f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
            },
            cardSpacing(10),
        )

        scrollView =
            CgmEdgeFadeScrollView(this)
                .apply {
                    isFillViewport = true
                    setBackgroundColor(background)
                    addView(root)
                    post { scrollTo(0, oldScrollY.coerceAtLeast(0)) }
                }.applyCgmEdgeFade()
        showSettingsView(checkNotNull(scrollView))
    }

    private fun segments(
        remaining: Int,
        known: Boolean,
        graceAvailable: Boolean,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "sensor-life-segments"
            addView(
                View(this@CgmConnectionsActivity).apply {
                    tag = "sensor-life-grace-segment"
                    contentDescription = "12h Toleranzzeit"
                    background =
                        GradientDrawable().apply {
                            setColor(if (graceAvailable) 0xFF22EB60.toInt() else 0xFF4A4A50.toInt())
                            cornerRadius = 3.dp.toFloat()
                        }
                },
                LinearLayout.LayoutParams(0, 7.dp, 0.5f).apply {
                    marginStart = 2.dp
                    marginEnd = 2.dp
                },
            )
            repeat(10) { index ->
                addView(
                    View(this@CgmConnectionsActivity).apply {
                        background =
                            GradientDrawable().apply {
                                setColor(if (known && index < remaining) 0xFF22EB60.toInt() else 0xFF4A4A50.toInt())
                                cornerRadius = 3.dp.toFloat()
                            }
                    },
                    LinearLayout.LayoutParams(0, 7.dp, 1f).apply {
                        marginStart = 2.dp
                        marginEnd = 2.dp
                    },
                )
            }
        }

    private fun card(palette: CgmAppearancePalette): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp, 12.dp, 13.dp, 12.dp)
            background =
                GradientDrawable().apply {
                    setColor(palette.settingsArgb(CgmAppearanceRole.MENU_SURFACE))
                    cornerRadius = 24.dp.toFloat()
                }
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            includeFontPadding = false
            textSize =
                app.sugarlicious.uishared.WearSettingsUi
                    .textSize(size)
            setTextColor(color)
            app.sugarlicious.uishared.SystemTypography
                .apply(this, bold)
        }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardSpacing(top: Int) = fullWidth().apply { topMargin = top.dp }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
