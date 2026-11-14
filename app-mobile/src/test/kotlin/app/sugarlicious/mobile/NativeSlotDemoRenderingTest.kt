package app.sugarlicious.mobile

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeSlotDemoRenderingTest {
    @Test
    fun `isolated Orbit inner pills have the same silhouette and centered native text`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        val relative = "watchfaces/sugarlicious-orbit/src/main/res/raw/watchface.xml"
        val source = listOf(File(cwd, relative), File(cwd.parentFile, relative)).first(File::isFile)
        val slots = source.inputStream().use(::readNativeWatchSlots)
        val outer = slots.single { it.id == 4 }
        val inner = nativeIsolatedPreviewSlot(2, slots.single { it.id == 5 }, outer.bounds)
        val outerBounds = nativeDynamicPreviewBackdrop(outer, null).getBounds()
        val innerBounds = nativeDynamicPreviewBackdrop(inner, null).getBounds()
        assertEquals(outerBounds.width, innerBounds.width, .01f)
        assertEquals(outerBounds.height, innerBounds.height, .01f)
        val sheet = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            androidx.compose.ui.graphics
                .Canvas(sheet.asImageBitmap()),
            Size(512f, 256f),
        ) {
            drawRect(Color(0xFF242424))
            for ((index, slot) in listOf(outer, inner).withIndex()) {
                val backdrop = nativeDynamicPreviewBackdrop(slot, null)
                val crop = backdrop.getBounds()
                val factor = 220f / maxOf(crop.width, crop.height)
                withTransform({
                    translate(index * 256f + (256f - crop.width * factor) / 2 - crop.left * factor, (256f - crop.height * factor) / 2 - crop.top * factor)
                    scale(factor, factor, androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    drawPath(backdrop, Color.Black)
                    drawNativeSlotDemo(slot, null, context)
                }
            }
        }
        File(System.getProperty("java.io.tmpdir"), "orbit-inner-pill-rendering.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `clockwise and counterclockwise glyph ink stays centered on its native circle`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        val relative = "watchfaces/sugarlicious-orbit/src/main/res/raw/watchface.xml"
        val source = listOf(File(cwd, relative), File(cwd.parentFile, relative)).first(File::isFile)
        val slots = source.inputStream().use(::readNativeWatchSlots)
        for (id in listOf(0, 2)) {
            val slot = slots.single { it.id == id }
            val template = slot.templates.getValue("RANGED_VALUE")
            val text = template.texts.first()
            val onlyText = slot.copy(templates = mapOf("RANGED_VALUE" to template.copy(track = null, icon = null)))
            val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                androidx.compose.ui.graphics
                    .Canvas(image.asImageBitmap()),
                Size(512f, 512f),
            ) {
                drawNativeSlotDemo(onlyText, null, context)
            }
            var sum = 0.0
            var pixels = 0
            for (y in 0 until 512) {
                for (x in 0 until 512) {
                    if (android.graphics.Color.alpha(image.getPixel(x, y)) > 100) {
                        sum += kotlin.math.hypot(x + .5 - 256, y + .5 - 256)
                        pixels++
                    }
                }
            }
            assertTrue("slot $id must render text", pixels > 100)
            assertEquals("slot $id glyph ink radial center", text.bounds.width / 2.0, sum / pixels, text.size * .2)
        }
    }

    @Test
    fun `every Orbit slot paints complete provider text on its native arc`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        val relative = "watchfaces/sugarlicious-orbit/src/main/res/raw/watchface.xml"
        val source = listOf(File(cwd, relative), File(cwd.parentFile, relative)).first(File::isFile)
        val slots = nativeSlotEditorOrder(2, source.inputStream().use(::readNativeWatchSlots))
        val sheet = Bitmap.createBitmap(1536, 1024, Bitmap.Config.ARGB_8888)
        val sheetCanvas = android.graphics.Canvas(sheet)
        sheetCanvas.drawColor(android.graphics.Color.BLACK)

        fun render(slot: NativeWatchSlot): Bitmap {
            val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                androidx.compose.ui.graphics
                    .Canvas(image.asImageBitmap()),
                Size(512f, 512f),
            ) {
                drawNativeSlotDemo(slot, null, context)
            }
            return image
        }
        for ((index, slot) in slots.withIndex()) {
            val image = render(slot)
            val withoutText = render(slot.copy(templates = slot.templates.mapValues { (_, template) -> template.copy(texts = emptyList()) }))
            var textPixels = 0
            for (y in 0 until 512) {
                for (x in 0 until 512) {
                    if (image.getPixel(x, y) != withoutText.getPixel(x, y) && android.graphics.Color.alpha(image.getPixel(x, y)) > 100) textPixels++
                }
            }
            assertTrue("slot ${slot.id} (${slot.displayName}) must paint visible provider text, got $textPixels pixels", textPixels > 100)
            sheetCanvas.drawBitmap(image, (index % 3 * 512).toFloat(), (index / 3 * 512).toFloat(), null)
        }
        val inner = slots.single { it.id == 5 }.templates.getValue("SHORT_TEXT")
        assertEquals("+5 2m", resolveNativeTemplateText(inner, "2m", "+5").single().value)
        val calendar = slots.single { it.id == 4 }.templates.getValue("SHORT_TEXT")
        assertEquals("14:30 Termin", resolveNativeTemplateText(calendar, "", "14:30 Termin").single().value)
        File(System.getProperty("java.io.tmpdir"), "orbit-slot-rendering.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
