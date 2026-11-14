package app.sugarlicious.mobile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

internal fun testNativeWidgetFrame(context: Context, color: Int): RemoteViews =
    nativeWidgetBitmapFrame(
        context,
        987,
        ConfigurableWidgetKind.GLUCOSE,
        NativeWidgetFrameInfo(DpSize(200.dp, 100.dp), WidgetContentInsets.ZERO, ComponentName(context, MainActivity::class.java)),
        Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) },
    )

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeWidgetFramesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun bind(id: Int, receiver: Class<*>) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val component = ComponentName(context, receiver)
        org.robolectric.Shadows.shadowOf(manager).apply {
            addInstalledProvider(android.appwidget.AppWidgetProviderInfo().apply { provider = component })
            setAllowedToBindAppWidgets(true)
        }
        manager.bindAppWidgetIdIfAllowed(id, component)
    }

    @Test
    fun `wide glucose header has its own working tap target over the right hand graph`() {
        bind(987, GlucoseWidget4x2Receiver::class.java)
        val frame =
            nativeWidgetBitmapFrame(
                context,
                987,
                ConfigurableWidgetKind.GLUCOSE,
                NativeWidgetFrameInfo(DpSize(430.dp, 228.dp), WidgetContentInsets.ZERO, ComponentName(context, MainActivity::class.java)),
                Bitmap.createBitmap(430, 228, Bitmap.Config.ARGB_8888),
            )
        val host = frame.apply(context, null)
        host.measure(
            android.view.View.MeasureSpec
                .makeMeasureSpec(430, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec
                .makeMeasureSpec(228, android.view.View.MeasureSpec.EXACTLY),
        )
        host.layout(0, 0, 430, 228)
        val target = host.findViewById<android.view.View>(R.id.widget_scale_action)
        assertEquals(android.view.View.VISIBLE, target.visibility)
        assertTrue(target.hasOnClickListeners())
        assertTrue("tap target belongs to right graph, not the glucose value", target.left >= 215)
    }

    @Test
    fun `CGM period tap also updates installed wide therapy graphs but preserves other settings`() =
        kotlinx.coroutines.runBlocking {
            bind(987, GlucoseWidget4x2Receiver::class.java)
            bind(988, TherapyFamily4x2Receiver::class.java)
            bind(989, TherapyFamily2x2Receiver::class.java)
            bind(990, MetabolicWidgetReceiver::class.java)
            android.appwidget.AppWidgetManager.getInstance(context).updateAppWidgetOptions(
                990,
                android.os.Bundle().apply {
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 344)
                    putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 175)
                },
            )
            WidgetInstanceConfigurationStore.save(context, 987, WidgetInstanceConfiguration(graphHours = 3))
            WidgetInstanceConfigurationStore.save(context, 988, WidgetInstanceConfiguration(graphHours = 1, backgroundArgb = Color.RED))
            WidgetInstanceConfigurationStore.save(context, 989, WidgetInstanceConfiguration(graphHours = 2))
            cycleWidgetGraphConfiguration(context, 987)
            assertEquals(6, WidgetInstanceConfigurationStore.read(context, 987).graphHours)
            assertEquals(6, WidgetInstanceConfigurationStore.read(context, 988).graphHours)
            assertEquals(Color.RED, WidgetInstanceConfigurationStore.read(context, 988).backgroundArgb)
            assertEquals(2, WidgetInstanceConfigurationStore.read(context, 989).graphHours)
            assertEquals(6, WidgetInstanceConfigurationStore.read(context, 990).graphHours)
        }

    @Test
    fun `copied bitmap publications reuse the same tree without removing displayed content`() {
        val host = RemoteViews(testNativeWidgetFrame(context, Color.RED)).apply(context, null)
        val image = host.findViewById<ImageView>(R.id.widget_bitmap)
        repeat(12) { index ->
            val expected = if (index % 2 == 0) Color.GREEN else Color.BLUE
            RemoteViews(RemoteViews(testNativeWidgetFrame(context, expected))).reapply(context, host)
            assertSame(image, host.findViewById<ImageView>(R.id.widget_bitmap))
            assertEquals(expected, (image.drawable as BitmapDrawable).bitmap.getPixel(100, 50))
        }
    }

    @Test
    fun `rejected publication leaves the previous bitmap intact and a later publication recovers`() {
        val host = testNativeWidgetFrame(context, Color.RED).apply(context, null)
        val result = runCatching { publishWidgetSurface(context, 987, testNativeWidgetFrame(context, Color.BLUE)) { error("rejected") } }
        assertTrue(result.isFailure)
        assertEquals(Color.RED, (host.findViewById<ImageView>(R.id.widget_bitmap).drawable as BitmapDrawable).bitmap.getPixel(100, 50))
        publishWidgetSurface(context, 987, testNativeWidgetFrame(context, Color.GREEN)) { it.reapply(context, host) }
        assertEquals(Color.GREEN, (host.findViewById<ImageView>(R.id.widget_bitmap).drawable as BitmapDrawable).bitmap.getPixel(100, 50))
    }
}
