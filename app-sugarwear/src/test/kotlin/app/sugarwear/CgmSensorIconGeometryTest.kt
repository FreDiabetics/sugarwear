package app.sugarwear

import android.graphics.RectF
import androidx.core.graphics.PathParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CgmSensorIconGeometryTest {
    @Test fun `status vectors render at forty dp of visible height`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val failures = mutableListOf<String>()
        listOf(
            R.drawable.ic_alarm_signalverlust,
            R.drawable.ic_alarm_bluetooth,
            R.drawable.ic_sensor_paused,
            R.drawable.ic_alarm_sensorfehler,
        ).forEach { id ->
            val drawable = context.getDrawable(id)!!
            // Ten pixels per dp makes transparent margins and clipping measurable.
            val height = 400
            val width = (height * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(android.graphics.Canvas(bitmap))
                val visibleRows =
                    (0 until height).filter { y ->
                        (0 until width).any { x -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 16 }
                    }
                val visibleHeight = visibleRows.last() - visibleRows.first() + 1
                if (kotlin.math.abs(height - visibleHeight) > 5) failures += "Visible height for ${context.resources.getResourceEntryName(id)}: $visibleHeight"
            } finally {
                bitmap.recycle()
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `technical icons keep exactly 175 percent visible scale without SVG outer padding`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        fun bounds(bitmap: android.graphics.Bitmap): RectF {
            val bounds = RectF()
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 16) bounds.union(x.toFloat(), y.toFloat(), x + 1f, y + 1f)
                }
            }
            return bounds
        }
        for (id in listOf(R.drawable.ic_status_bluetooth, R.drawable.ic_status_paused, R.drawable.ic_status_sensor_error, R.drawable.ic_status_signal_loss)) {
            val original = context.getDrawable(id)!!
            val baseline = android.graphics.Bitmap.createBitmap(660, 660, android.graphics.Bitmap.Config.ARGB_8888)
            original.setBounds(0, 0, 660, 660)
            original.draw(android.graphics.Canvas(baseline))
            val layout = cgmStatusIconLayout(id)!!
            val view =
                android.widget.ImageView(context).apply {
                    setImageResource(id)
                    scaleType = android.widget.ImageView.ScaleType.MATRIX
                    imageMatrix = layout.matrix(10f, drawable.intrinsicWidth)
                }
            val width = layout.width(10f)
            val height = layout.height(10f)
            view.measure(
                android.view.View.MeasureSpec
                    .makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec
                    .makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
            val enlarged = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(enlarged))
            val before = bounds(baseline)
            val after = bounds(enlarged)
            org.junit.Assert.assertEquals(before.width() * 1.75f, after.width(), 3f)
            org.junit.Assert.assertEquals(before.height() * 1.75f, after.height(), 3f)
            assertTrue(after.left >= 0 && after.top >= 0 && after.right <= width && after.bottom <= height)
            baseline.recycle()
            enlarged.recycle()
        }
    }

    @Test fun `original status paths fit their viewport without distortion or clipping`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val failures = mutableListOf<String>()
        listOf(
            R.drawable.ic_alarm_signalverlust,
            R.drawable.ic_alarm_bluetooth,
            R.drawable.ic_sensor_paused,
            R.drawable.ic_alarm_sensorfehler,
        ).forEach { id ->
            val bounds = RectF()
            var viewportWidth = 0f
            var viewportHeight = 0f
            var translateX = 0f
            var translateY = 0f
            context.resources.getXml(id).use { xml ->
                val ns = "http://schemas.android.com/apk/res/android"
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        when (xml.name) {
                            "vector" -> {
                                viewportWidth = xml.getAttributeValue(ns, "viewportWidth").toFloat()
                                viewportHeight = xml.getAttributeValue(ns, "viewportHeight").toFloat()
                            }
                            "group" -> {
                                translateX = xml.getAttributeValue(ns, "translateX")?.toFloat() ?: 0f
                                translateY = xml.getAttributeValue(ns, "translateY")?.toFloat() ?: 0f
                            }
                            "path" -> {
                                val pathBounds = RectF()
                                val points = PathParser.createPathFromPathData(xml.getAttributeValue(ns, "pathData")).approximate(.001f)
                                val xs = points.indices.filter { it % 3 == 1 }.map { points[it] }
                                val ys = points.indices.filter { it % 3 == 2 }.map { points[it] }
                                pathBounds.set(xs.min(), ys.min(), xs.max(), ys.max())
                                pathBounds.offset(translateX, translateY)
                                bounds.union(pathBounds)
                            }
                        }
                    }
                    xml.next()
                }
            }
            val name = context.resources.getResourceEntryName(id)
            println("$name bounds=$bounds viewport=$viewportWidth x $viewportHeight")
            if (bounds.left < -0.01f || bounds.top < -0.01f || bounds.right > viewportWidth + .01f || bounds.bottom > viewportHeight + .01f) {
                failures += "$name clipped: $bounds in $viewportWidth x $viewportHeight"
            }
            val drawable = context.getDrawable(id)!!
            val intrinsicRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
            assertTrue("$name proportions", kotlin.math.abs(intrinsicRatio - viewportWidth / viewportHeight) < .02f)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
