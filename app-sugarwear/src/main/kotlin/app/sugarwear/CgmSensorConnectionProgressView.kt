package app.sugarwear

import android.content.Context
import android.graphics.PorterDuff
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout

/** Shared, button-free graphic for first pairing and explicit resume. Progress comes from the collector. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in code with required status colors and progress.
internal class CgmSensorConnectionProgressView(
    context: Context,
    iconColor: Int,
    progressColor: Int,
    animate: Boolean,
    iconSizeDp: Int = 28,
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        fun icon(resource: Int, description: String) =
            ImageView(context).apply {
                setImageResource(resource)
                setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                contentDescription = description
            }
        addView(icon(R.drawable.ic_sensor_outline, "Sensor"), LayoutParams(iconSizeDp.dp, iconSizeDp.dp))
        addView(
            CgmConnectionDotsView(context).apply {
                contentDescription = "7 Verbindungsdots"
                color = progressColor
                isAnimating = animate
            },
            LayoutParams(0, 24.dp, 1f).apply { setMargins(8.dp, 0, 8.dp, 0) },
        )
        addView(icon(R.drawable.ic_watch_device, "Smartwatch"), LayoutParams(iconSizeDp.dp, iconSizeDp.dp))
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
