package app.sugarlicious.mobile

import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors

class HealthConnectRationaleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousColors.apply(
            SugarliciousColorStore.load(getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)),
        )
        setContentView(
            ScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(color(SugarliciousColorRole.BACKGROUND))
                addView(
                    LinearLayout(this@HealthConnectRationaleActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(20.dp, 28.dp, 20.dp, 28.dp)

                        addView(
                            text("SUGARLICIOUS", 11f, SugarliciousColorRole.PRIMARY, true).apply {
                                letterSpacing = 0.1f
                            },
                        )
                        addView(text("Health Connect", 28f, SugarliciousColorRole.TEXT_PRIMARY, true))
                        addView(
                            LinearLayout(this@HealthConnectRationaleActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(16.dp, 16.dp, 16.dp, 16.dp)
                                background = cardBackground()
                                addView(
                                    text(
                                        "Deine Gesundheitsdaten bleiben unter deiner Kontrolle",
                                        16f,
                                        SugarliciousColorRole.TEXT_PRIMARY,
                                        true,
                                    ),
                                )
                                addView(
                                    text(
                                        "Sugarlicious nutzt freigegebene Aktivitäts- und Gesundheitsdaten ausschließlich lokal für deine Übersicht und dein Diabetes-Management. Du entscheidest für jeden Datentyp getrennt.",
                                        13f,
                                        SugarliciousColorRole.TEXT_SECONDARY,
                                    ).apply { setPadding(0, 8.dp, 0, 0) },
                                )
                                addView(
                                    text(
                                        "Eigene CGM-Werte können als Blutzucker an Health Connect geschrieben werden. Importierte Daten werden niemals als neue Daten zurückgeschrieben.",
                                        13f,
                                        SugarliciousColorRole.TEXT_SECONDARY,
                                    ).apply { setPadding(0, 10.dp, 0, 0) },
                                )
                                addView(
                                    text(
                                        "Therapieentscheidungen oder Insulinsteuerung erfolgen nicht über Health Connect.",
                                        13f,
                                        SugarliciousColorRole.PRIMARY,
                                        true,
                                    ).apply { setPadding(0, 10.dp, 0, 0) },
                                )
                            },
                            LinearLayout
                                .LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                ).apply { topMargin = 16.dp },
                        )
                    },
                )
            },
        )
    }

    private fun text(
        value: String,
        size: Float,
        role: SugarliciousColorRole,
        bold: Boolean = false,
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color(role))
        gravity = Gravity.START
        if (bold) {
            app.sugarlicious.uishared.SystemTypography
                .apply(this, true)
        }
    }

    private fun cardBackground() =
        GradientDrawable().apply {
            cornerRadius = 24.dp.toFloat()
            setColor(color(SugarliciousColorRole.SURFACE))
            setStroke(1.dp, color(SugarliciousColorRole.BORDER))
        }

    private fun color(role: SugarliciousColorRole): Int = SugarliciousColors.argb(role)

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
