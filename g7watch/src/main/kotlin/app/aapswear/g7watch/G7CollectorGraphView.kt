package app.aapswear.g7watch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import app.aapswear.g7.CgmReading
import app.aapswear.model.GraphTimeWindow
import app.aapswear.model.RelativeGraphTimeAxis
import app.aapswear.uishared.SharedWearCgmGraphRenderer

internal fun g7CollectorGraphWindow(
    nowEpochMs: Long,
    graphHours: Int,
): GraphTimeWindow = GraphTimeWindow.live(nowEpochMs, graphHours * RelativeGraphTimeAxis.HOUR_MS)

@SuppressLint("DrawAllocation")
internal class G7CollectorGraphView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private val density = resources.displayMetrics.density
        private val directSettings by lazy { G7DirectToWatchSettingsStore(context) }
        private val appearanceStore by lazy { G7AppearanceStore(context) }
        private var readings: List<CgmReading> = emptyList()
        private var nowOverrideEpochMs: Long? = null
        private var boundGraphHours: Int = 3
        private var boundPalette: G7AppearancePalette? = null

        init {
            outlineProvider =
                object : ViewOutlineProvider() {
                    override fun getOutline(
                        view: View,
                        outline: Outline,
                    ) {
                        if (view.width > 0 && view.height > 0) {
                            outline.setRoundRect(
                                0,
                                0,
                                view.width,
                                view.height,
                                directSettings.graphStyle().cornerRadiusDp * density,
                            )
                        }
                    }
                }
            clipToOutline = true
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            invalidateOutline()
        }

        fun bind(
            readings: List<CgmReading>,
            palette: G7AppearancePalette,
            graphHours: Int,
            nowEpochMs: Long? = null,
        ) {
            // The in-app graph belongs to SugarWear and therefore follows SugarWear's appearance
            // palette. Direct-to-Watch settings remain exclusive to Vigil.
            boundPalette = palette
            boundGraphHours = graphHours.takeIf { it in G7DirectToWatchSettingsStore.HOUR_OPTIONS } ?: 3
            this.readings = readings
            this.nowOverrideEpochMs = nowEpochMs
            invalidateOutline()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = nowOverrideEpochMs ?: System.currentTimeMillis()
            val graphHours = boundGraphHours
            val palette = boundPalette ?: G7AppearanceStore(context).load()
            SharedWearCgmGraphRenderer.render(
                canvas,
                width,
                height,
                density,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, resources.displayMetrics),
                g7SharedGraphInput(
                    readings,
                    palette,
                    directSettings,
                    graphHours,
                    now,
                    styleOverride = appearanceStore.inAppGraphStyle(),
                ),
            )
        }
    }
