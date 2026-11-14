package app.sugarlicious.mobile

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal data class HealthConnectMetricItem(
    val label: String,
    val value: String,
    val iconRes: Int,
)

/** Compact, read-only overview of every Health Connect record type Sugarlicious imports. */
internal object HealthConnectDataDialog {
    private const val DASH = "—"

    internal fun metricItems(snapshot: HealthConnectSnapshot?): List<HealthConnectMetricItem> =
        listOf(
            HealthConnectMetricItem("Herzfrequenz", snapshot?.latestHeartRate?.let { "$it bpm" } ?: DASH, R.drawable.ic_health_heart),
            HealthConnectMetricItem("Ruhepuls", snapshot?.restingHeartRate?.let { "$it bpm" } ?: DASH, R.drawable.ic_health_heart),
            HealthConnectMetricItem(
                "HRV",
                snapshot?.heartRateVariabilityMs?.let { "${oneDecimal(it)} ms" } ?: DASH,
                R.drawable.ic_health_heart,
            ),
            HealthConnectMetricItem("Schritte", snapshot?.steps?.takeIf { it > 0 }?.let(::grouped) ?: DASH, R.drawable.ic_health_steps),
            HealthConnectMetricItem(
                "Aktive Kalorien",
                snapshot?.activeCaloriesKcal?.takeIf { it > 0 }?.let { "${it.roundToInt()} kcal" } ?: DASH,
                R.drawable.ic_health_activity,
            ),
            HealthConnectMetricItem(
                "Gesamtkalorien",
                snapshot
                    ?.totalCaloriesKcal
                    ?.takeIf {
                        it > 0
                    }?.let { "${it.roundToInt()} kcal" } ?: DASH,
                R.drawable.ic_health_activity,
            ),
            HealthConnectMetricItem(
                "Distanz",
                snapshot
                    ?.distanceMeters
                    ?.takeIf {
                        it > 0
                    }?.let { "${oneDecimal(it / 1000.0)} km" } ?: DASH,
                R.drawable.ic_health_distance,
            ),
            HealthConnectMetricItem(
                "Höhenmeter",
                snapshot
                    ?.elevationMeters
                    ?.takeIf {
                        it != 0.0
                    }?.let { "${it.roundToInt()} m" } ?: DASH,
                R.drawable.ic_health_distance,
            ),
            HealthConnectMetricItem(
                "Etagen",
                snapshot?.floorsClimbed?.takeIf { it > 0 }?.let { oneDecimal(it) } ?: DASH,
                R.drawable.ic_health_stairs,
            ),
            HealthConnectMetricItem(
                "Training",
                snapshot?.activeMinutes?.takeIf { it > 0 }?.let { "$it min" } ?: DASH,
                R.drawable.ic_health_activity,
            ),
            HealthConnectMetricItem(
                "Schlaf",
                snapshot?.sleepMinutes?.takeIf { it > 0 }?.let(::minutesLabel) ?: DASH,
                R.drawable.ic_health_sleep,
            ),
            HealthConnectMetricItem(
                "Trinken",
                snapshot
                    ?.hydrationLiters
                    ?.takeIf {
                        it > 0
                    }?.let { "${oneDecimal(it)} l" } ?: DASH,
                R.drawable.ic_health_water,
            ),
            HealthConnectMetricItem(
                "Ernährung",
                snapshot?.let { s ->
                    if (s.nutritionCarbohydratesGrams > 0 || s.nutritionEnergyKcal > 0) {
                        "${s.nutritionCarbohydratesGrams.roundToInt()} g KH · ${s.nutritionEnergyKcal.roundToInt()} kcal"
                    } else {
                        DASH
                    }
                } ?: DASH,
                R.drawable.ic_health_nutrition,
            ),
            HealthConnectMetricItem("Gewicht", snapshot?.weightKg?.let { "${oneDecimal(it)} kg" } ?: DASH, R.drawable.ic_health_body),
            HealthConnectMetricItem(
                "Größe",
                snapshot?.heightMeters?.let { "${(it * 100).roundToInt()} cm" } ?: DASH,
                R.drawable.ic_health_body,
            ),
            HealthConnectMetricItem(
                "Körperfett",
                snapshot?.bodyFatPercent?.let { "${oneDecimal(it)} %" } ?: DASH,
                R.drawable.ic_health_body,
            ),
            HealthConnectMetricItem(
                "Körperwasser",
                snapshot?.bodyWaterKg?.let { "${oneDecimal(it)} kg" } ?: DASH,
                R.drawable.ic_health_water,
            ),
            HealthConnectMetricItem(
                "Fettfreie Masse",
                snapshot?.leanBodyMassKg?.let { "${oneDecimal(it)} kg" } ?: DASH,
                R.drawable.ic_health_body,
            ),
            HealthConnectMetricItem(
                "Grundumsatz",
                snapshot?.basalMetabolicRateKcalPerDay?.let {
                    "${it.roundToInt()} kcal/Tag"
                } ?: DASH,
                R.drawable.ic_health_activity,
            ),
            HealthConnectMetricItem(
                "Blutdruck",
                snapshot?.let { s ->
                    if (s.systolicMmHg != null && s.diastolicMmHg != null) {
                        "${s.systolicMmHg.roundToInt()}/${s.diastolicMmHg.roundToInt()} mmHg"
                    } else {
                        DASH
                    }
                } ?: DASH,
                R.drawable.ic_health_pressure,
            ),
            HealthConnectMetricItem(
                "Blutzucker",
                snapshot?.bloodGlucoseMgDl?.let { "${it.roundToInt()} mg/dL" } ?: DASH,
                R.drawable.ic_health_glucose,
            ),
            HealthConnectMetricItem(
                "Sauerstoffsättigung",
                snapshot?.oxygenSaturationPercent?.let {
                    "${oneDecimal(it)} %"
                } ?: DASH,
                R.drawable.ic_health_oxygen,
            ),
            HealthConnectMetricItem(
                "Atemfrequenz",
                snapshot?.respiratoryRate?.let { "${oneDecimal(it)} /min" } ?: DASH,
                R.drawable.ic_health_breath,
            ),
            HealthConnectMetricItem("VO₂max", snapshot?.vo2Max?.let { "${oneDecimal(it)} ml/kg/min" } ?: DASH, R.drawable.ic_health_vo2),
        )

    fun show(context: Context) {
        val snapshot = HealthConnectIntegration.snapshot(context)
        val status = HealthConnectIntegration.status(context)
        val textPrimary = SugarliciousColors.argb(SugarliciousColorRole.TEXT_PRIMARY)
        val textSecondary = SugarliciousColors.argb(SugarliciousColorRole.TEXT_SECONDARY)
        val accent =
            app.sugarlicious.mobile.ui.theme
                .mobileMenuAccentArgb(context, dark = !SugarliciousColors.palette.isLight)
        val border = SugarliciousColors.argb(SugarliciousColorRole.BORDER)
        val surface = SugarliciousColors.argb(SugarliciousColorRole.SURFACE)

        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(18), context.dp(8), context.dp(18), context.dp(10))

                addView(
                    TextView(context).apply {
                        setText(R.string.health_connect_data_title)
                        textSize = 20f
                        setTextColor(textPrimary)
                        app.sugarlicious.uishared.SystemTypography
                            .apply(this, true)
                    },
                )
                addView(
                    TextView(context).apply {
                        val synced =
                            snapshot?.syncedAtEpochMs?.takeIf { it > 0 }?.let {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                            } ?: context.getString(R.string.health_connect_not_synchronized)
                        text =
                            context.getString(
                                R.string.health_connect_sync_summary,
                                status.grantedReadPermissionCount,
                                HealthConnectIntegration.readableRecordTypes.size,
                                synced,
                            )
                        textSize = 11f
                        setTextColor(textSecondary)
                        setPadding(0, context.dp(4), 0, context.dp(10))
                    },
                )

                metricItems(snapshot).forEachIndexed { index, item ->
                    if (index >
                        0
                    ) {
                        addView(
                            View(context).apply { setBackgroundColor(border) },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1),
                        )
                    }
                    addView(metricRow(context, item, textPrimary, textSecondary, accent))
                }
            }

        val scroll =
            ScrollView(context).apply {
                isFillViewport = true
                addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

        val dialog =
            AlertDialog
                .Builder(context)
                .setView(scroll)
                .setPositiveButton("Schließen", null)
                .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = context.dp(26).toFloat()
                    setColor(surface)
                    setStroke(context.dp(1), border)
                },
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        }
        dialog.show()
    }

    private fun metricRow(
        context: Context,
        item: HealthConnectMetricItem,
        textPrimary: Int,
        textSecondary: Int,
        accent: Int,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(50)
            setPadding(0, context.dp(6), 0, context.dp(6))

            addView(
                sugarliciousIconView(context, item.iconRes, item.label, tintArgb = accent),
                LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(12) },
            )

            addView(
                TextView(context).apply {
                    text = item.label
                    textSize = 14f
                    setTextColor(textPrimary)
                    maxLines = 2
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            addView(
                TextView(context).apply {
                    text = item.value
                    textSize = 13f
                    setTextColor(if (item.value == DASH) textSecondary else textPrimary)
                    gravity = Gravity.END
                    maxLines = 2
                    setPadding(context.dp(10), 0, 0, 0)
                },
            )
        }

    private fun oneDecimal(value: Double): String = String.format(Locale.GERMANY, "%.1f", value)

    private fun grouped(value: Long): String = String.format(Locale.GERMANY, "%,d", value)

    private fun minutesLabel(minutes: Long): String = "${minutes / 60} h ${minutes % 60} min"

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
