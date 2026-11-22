package app.sugarwear

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(status.state, render = ::render)
        render()
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
                app.sugarlicious.uishared.WearSettingsUi.pagePadding(this)
                setBackgroundColor(background)
                addView(cgmSettingsHeader("Verbindungen", palette), fullWidth())
            }

        val state = snapshot.sensor
        val sensorCard = card(palette).apply {
            addView(label("SENSOR", 9f, palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY), true))
            val sensor = state.sensor
            addView(
                label(
                    sensor?.deviceName?.takeIf { it.isNotBlank() } ?: if (sensor == null) "Kein aktiver Sensor verbunden" else "Dexcom G7",
                    14f,
                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    true,
                ),
            )
            if (sensor != null) {
                val stateText =
                    when {
                        !state.collectorEnabled -> "Datenempfang pausiert"
                        sensor.state == CgmSensorState.WARMUP -> "Sensoraufwärmung"
                        sensor.state == CgmSensorState.GRACE_PERIOD -> "Nachlaufphase"
                        sensor.state == CgmSensorState.ENDED -> "Sensorlaufzeit beendet"
                        sensor.state == CgmSensorState.ERROR -> "Sensorfehler"
                        else -> "Sensor verbunden"
                    }
                addView(label(stateText, 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
            }
            val lifetime = cgmSensorLifetime(state, System.currentTimeMillis())
            addView(segments(lifetime?.remainingDaySegments ?: 0, lifetime?.hasVerifiedEnd == true), cardSpacing(12))
            addView(
                label(
                    when {
                        sensor == null -> "Kein Sensor gekoppelt"
                        lifetime?.hasVerifiedEnd != true -> "Verbleibende Laufzeit unbekannt"
                        lifetime.expired -> "Reguläre Sensorlaufzeit beendet"
                        else -> lifetime.days.toString() + " Tage · " +
                            lifetime.hours.toString() + " Stunden · " +
                            lifetime.minutes.toString() + " Minuten"
                    },
                    11f,
                    palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY),
                    true,
                ),
                cardSpacing(9),
            )
            addView(
                action(
                    if (sensor == null) "Sensor verbinden" else "Sensor verwalten",
                    palette,
                    if (sensor == null) "connections-start-pairing" else "connections-manage-sensor",
                ) {
                    if (sensor == null) {
                        startActivity(
                            Intent(this@CgmConnectionsActivity, CgmWatchActivity::class.java)
                                .putExtra(CgmWatchActivity.EXTRA_OPEN_PAIRING, true)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                    } else {
                        startActivity(Intent(this@CgmConnectionsActivity, CgmSystemStatusActivity::class.java))
                    }
                },
                cardSpacing(7),
            )
            addView(
                action("Sensorstatus & Diagnose", palette, "connections-systemstatus") {
                    startActivity(Intent(this@CgmConnectionsActivity, CgmSystemStatusActivity::class.java))
                },
                cardSpacing(4),
            )
        }
        root.addView(sensorCard, cardSpacing(10))

        root.addView(
            card(palette).apply {
                addView(label("Dexcom Share", 14f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                addView(label("Einrichtung und Uploadstatus", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
                addView(
                    action("Share & Follow öffnen", palette, "connections-share") {
                        startActivity(Intent(this@CgmConnectionsActivity, CgmShareSettingsActivity::class.java))
                    },
                    cardSpacing(6),
                )
            },
            cardSpacing(10),
        )
        root.addView(
            card(palette).apply {
                tag = "connections-health-placeholder"
                addView(label("Health Connect", 14f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_PRIMARY), true))
                addView(label("Noch nicht integriert", 10f, palette.settingsArgb(CgmAppearanceRole.MENU_TEXT_SECONDARY)))
            },
            cardSpacing(10),
        )

        scrollView =
            CgmEdgeFadeScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(background)
                addView(root)
                post { scrollTo(0, oldScrollY.coerceAtLeast(0)) }
            }.applyCgmEdgeFade()
        showSettingsView(checkNotNull(scrollView))
    }

    private fun segments(remaining: Int, known: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "sensor-life-segments"
            repeat(10) { index ->
                addView(
                    View(this@CgmConnectionsActivity).apply {
                        background =
                            GradientDrawable().apply {
                                setColor(
                                    if (known && index < remaining) 0xFF22EB60.toInt()
                                    else 0xFF4A4A50.toInt(),
                                )
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

    private fun action(title: String, palette: CgmAppearancePalette, viewTag: String, onClick: () -> Unit): View =
        TextView(this).apply {
            tag = viewTag
            text = title
            textSize = app.sugarlicious.uishared.WearSettingsUi.textSize(11f)
            setTextColor(palette.settingsArgb(CgmAppearanceRole.MENU_PRIMARY))
            app.sugarlicious.uishared.SystemTypography.apply(this, true)
            gravity = Gravity.CENTER
            minimumHeight = 48.dp
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
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
            textSize = app.sugarlicious.uishared.WearSettingsUi.textSize(size)
            setTextColor(color)
            app.sugarlicious.uishared.SystemTypography.apply(this, bold)
        }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun cardSpacing(top: Int) = fullWidth().apply { topMargin = top.dp }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
