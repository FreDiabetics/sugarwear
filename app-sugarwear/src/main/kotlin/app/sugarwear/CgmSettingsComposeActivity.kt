package app.sugarwear

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Keeps approved native control geometry while Compose owns the lifecycle-bound screen host. */
@dagger.hilt.android.AndroidEntryPoint
abstract class CgmSettingsComposeActivity : ComponentActivity() {
    private val content = MutableStateFlow<View?>(null)
    private var host: FrameLayout? = null
    private var installed = false
    private var pointerDown = false
    private var finishRequested = false
    private val settingsSources = mutableListOf<StateFlow<*>>()

    override fun finish() {
        if (settingsSources.any { (it.value as? CgmSettingsIoState<*>)?.loading == true }) {
            finishRequested = true
            return
        }
        super.finish()
    }

    private val deferredRenders = linkedSetOf<() -> Unit>()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pointerDown = true
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            pointerDown = false
            val pending = deferredRenders.toList()
            deferredRenders.clear()
            pending.forEach { it() }
        }
        return handled
    }

    internal fun <T> settingsReady(state: CgmSettingsIoState<T>, retry: () -> Unit): Boolean {
        if (state.snapshot != null && state.error == null) return true
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                val spacing = (24 * resources.displayMetrics.density).toInt()
                setPadding(spacing, spacing, spacing, spacing)
                setBackgroundColor(android.graphics.Color.BLACK)
                addView(
                    TextView(this@CgmSettingsComposeActivity).apply {
                        text = if (state.error == null) "Einstellungen werden geladen ..." else "Einstellungen konnten nicht geladen werden."
                        textSize =
                            app.sugarlicious.uishared.WearSettingsUi
                                .textSize(12f)
                        app.sugarlicious.uishared.SystemTypography
                            .apply(this, false)
                        setTextColor(android.graphics.Color.WHITE)
                        gravity = android.view.Gravity.CENTER
                    },
                )
                if (state.error != null) {
                    addView(
                        TextView(this@CgmSettingsComposeActivity).apply {
                            setText(R.string.settings_retry)
                            textSize =
                                app.sugarlicious.uishared.WearSettingsUi
                                    .textSize(14f)
                            gravity = android.view.Gravity.CENTER
                            minimumHeight = (48 * resources.displayMetrics.density).toInt()
                            app.sugarlicious.uishared.SystemTypography
                                .apply(this, true)
                            setTextColor(android.graphics.Color.WHITE)
                            background =
                                android.graphics.drawable.GradientDrawable().apply {
                                    setColor(0xFF363636.toInt())
                                    cornerRadius = 26 * resources.displayMetrics.density
                                }
                            setOnClickListener { retry() }
                        },
                    )
                }
            }
        showSettingsView(root)
        return false
    }

    private val stateBindings = mutableListOf<@Composable () -> Unit>()

    /** Bind feature state before mounting the native renderer. Slider values update in place;
     * only structure-changing values should request a rebuild while a gesture is in flight. */
    protected fun <T> bindSettingsState(
        source: StateFlow<T>,
        renderWhen: (T, T) -> Boolean = { old, new -> old != new },
        render: () -> Unit,
    ) {
        settingsSources += source
        stateBindings += {
            val value = source.collectAsStateWithLifecycle().value
            val previous = remember { arrayOfNulls<Any>(1) }
            LaunchedEffect(value) {
                @Suppress("UNCHECKED_CAST")
                val old = previous[0] as T?
                previous[0] = value
                if (finishRequested && settingsSources.none { (it.value as? CgmSettingsIoState<*>)?.loading == true }) {
                    finishRequested = false
                    finish()
                }
                if (old == null || renderWhen(old, value)) {
                    if (pointerDown) deferredRenders += render else render()
                }
            }
        }
    }

    protected fun showSettingsView(view: View) {
        content.value = view
        host?.let { attach(it, view) }
        if (installed) return
        installed = true
        setContent {
            stateBindings.forEach { it() }
            val screen = content.collectAsStateWithLifecycle().value
            AndroidView(
                factory = { FrameLayout(it).also { frame -> host = frame } },
                update = { frame -> screen?.let { attach(frame, it) } },
            )
        }
    }

    private fun attach(frame: FrameLayout, view: View) {
        if (view.parent === frame) return
        (view.parent as? ViewGroup)?.removeView(view)
        frame.removeAllViews()
        frame.addView(view, FrameLayout.LayoutParams(-1, -1))
    }
}
