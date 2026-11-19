package app.sugarlicious.uishared

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** The outage is outside the value card; neither the last values nor their graph are covered. */
class SignalLossWarningView(
    context: Context,
) : LinearLayout(context) {
    fun setMessage(message: String) {
        val paused = message == context.getString(R.string.shared_reception_paused)
        (getChildAt(1) as TextView).apply {
            text = if (paused) context.getString(R.string.shared_reception_paused_multiline) else message
            textSize = if (paused) 20f else 18f
        }
        contentDescription = message
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        tag = "signal-loss-warning"
        setPadding(0, WearSettingsUi.dp(context, 6), 0, WearSettingsUi.dp(context, 10))
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_alert) }, LayoutParams(WearSettingsUi.dp(context, 36), WearSettingsUi.dp(context, 36)))
        addView(
            TextView(context).apply {
                setText(R.string.shared_signal_loss)
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                SystemTypography.apply(this, true)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = WearSettingsUi.dp(context, 4) },
        )
    }
}
