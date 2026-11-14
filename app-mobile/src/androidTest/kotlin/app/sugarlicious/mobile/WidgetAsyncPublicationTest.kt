package app.sugarlicious.mobile

import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Parcel
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real async apply, copies and parcel transport, rather than Robolectric's simplified host. */
@RunWith(AndroidJUnit4::class)
class WidgetAsyncPublicationTest {
    private fun transported(views: RemoteViews): RemoteViews {
        val parcel = Parcel.obtain()
        return try {
            RemoteViews(views).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            RemoteViews(RemoteViews.CREATOR.createFromParcel(parcel))
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun asyncReplacementAndCancelledBurstsKeepTheDisplayedBitmapIntact() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val queued = LinkedBlockingQueue<Runnable>()
        lateinit var host: AppWidgetHostView

        fun publish(color: Int) {
            val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            val info = NativeWidgetFrameInfo(DpSize(200.dp, 100.dp), WidgetContentInsets.ZERO, ComponentName(context, MainActivity::class.java))
            publishWidgetSurface(context, -7901, nativeWidgetBitmapFrame(context, -7901, ConfigurableWidgetKind.GLUCOSE, info, bitmap)) {
                host.updateAppWidget(transported(it))
            }
        }
        instrumentation.runOnMainSync {
            host = AppWidgetHostView(context)
            publish(Color.RED)
            host.setExecutor { queued.add(it) }
        }
        repeat(12) { revision ->
            lateinit var image: ImageView
            lateinit var oldBitmap: Bitmap
            var oldColor = 0
            val expected = if (revision % 2 == 0) Color.BLUE else Color.GREEN
            instrumentation.runOnMainSync {
                image = host.findViewById(R.id.widget_bitmap)
                oldBitmap = (image.drawable as BitmapDrawable).bitmap
                oldColor = oldBitmap.getPixel(100, 50)
                publish(Color.YELLOW)
                publish(expected)
                assertEquals(oldColor, (image.drawable as BitmapDrawable).bitmap.getPixel(100, 50))
            }
            repeat(2) { checkNotNull(queued.poll(5, TimeUnit.SECONDS)).run() }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSame(image, host.findViewById<ImageView>(R.id.widget_bitmap))
                assertEquals(expected, (image.drawable as BitmapDrawable).bitmap.getPixel(100, 50))
                assertEquals(oldColor, oldBitmap.getPixel(100, 50))
            }
        }
    }

    @Test
    fun installedWidgetsRetainRenderableBitmapsAcrossCopiesAndAsyncUpdates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val installed =
            manager.installedProviders.filter { it.provider.packageName == context.packageName }.flatMap { provider ->
                manager.getAppWidgetIds(provider.provider).map { it to configurableWidgetKind(provider.provider.className) }
            }
        assertTrue("device test requires a placed Sugarlicious widget", installed.isNotEmpty())
        installed.forEach { (id, kind) ->
            val content = runBlocking { WidgetRenderTransactions.run(id) { SugarliciousWidgets.prepareInstance(context, id, kind) } }
            val queued = LinkedBlockingQueue<Runnable>()
            lateinit var host: AppWidgetHostView
            lateinit var image: ImageView
            instrumentation.runOnMainSync {
                host = AppWidgetHostView(context)
                host.updateAppWidget(transported(content))
                image =
                    host.findViewById<ImageView>(R.id.widget_bitmap).let {
                        if (it.visibility == android.view.View.VISIBLE) it else host.findViewById(R.id.widget_bitmap_fill)
                    }
                host.setExecutor { queued.add(it) }
                host.updateAppWidget(transported(content))
                assertTrue(image.drawable != null)
            }
            checkNotNull(queued.poll(5, TimeUnit.SECONDS)).run()
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSame(image, host.findViewById<ImageView>(image.id))
                host.measure(MeasureSpec.makeMeasureSpec(640, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(360, MeasureSpec.EXACTLY))
                host.layout(0, 0, 640, 360)
                val pixels = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                host.draw(android.graphics.Canvas(pixels))
                assertTrue("empty drawn payload for widget $id", (0 until 640 step 16).any { x -> (0 until 360 step 16).any { y -> Color.alpha(pixels.getPixel(x, y)) > 0 } })
                pixels.recycle()
            }
            runBlocking { SugarliciousWidgets.renderInstance(context, id, kind) }
        }
    }
}
