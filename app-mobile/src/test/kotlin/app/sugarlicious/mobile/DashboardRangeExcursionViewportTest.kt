package app.sugarlicious.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRangeExcursionViewportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `panning into an old excursion does not reactivate current high range tint`() {
        val preferences = context.getSharedPreferences("chart_range_viewport", Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val high = Color.rgb(23, 47, 211)
        SugarliciousColorStore.save(preferences, SugarliciousColorRole.RANGE_HIGH, high)
        SugarliciousColors.apply(SugarliciousColorStore.load(preferences))

        try {
            val now = System.currentTimeMillis()
            val history =
                listOf(
                    GlucoseSample(180.0, now - 40 * 60_000L),
                    GlucoseSample(182.0, now - 35 * 60_000L),
                    GlucoseSample(185.0, now - 30 * 60_000L),
                    GlucoseSample(188.0, now - 25 * 60_000L),
                    GlucoseSample(130.0, now - 15 * 60_000L),
                    GlucoseSample(128.0, now - 10 * 60_000L),
                    GlucoseSample(126.0, now - 5 * 60_000L),
                    GlucoseSample(124.0, now),
                )
            val state =
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(124.0, GlucoseUnit.MG_DL, measuredAtEpochMs = now),
                    glucoseHistory = history,
                    target = TargetState(80.0, 160.0),
                )
            val viewport = ChartViewport(1)
            val chart =
                GlucoseDashboardChart(context, sharedViewport = viewport).apply {
                    bind(
                        state = state,
                        unit = GlucoseUnit.MG_DL,
                        showPredictions = false,
                        durationHours = 1,
                        showTargetRange = true,
                        clockEpochMs = now,
                    )
                }

            viewport.pan(140f, 420f)
            val bitmap = render(chart, 230)
            assertEquals(0, count(bitmap) { it == high })
        } finally {
            SugarliciousColors.apply(SugarliciousPalette.defaults())
        }
    }

    private fun render(
        view: View,
        height: Int,
    ): Bitmap {
        val width = 420
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun count(
        bitmap: Bitmap,
        predicate: (Int) -> Boolean,
    ): Int {
        var result = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (predicate(bitmap.getPixel(x, y))) result++
            }
        }
        return result
    }
}
