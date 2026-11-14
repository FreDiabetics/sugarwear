package app.sugarlicious.tools.screenshot

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScreenshotComparatorTest {
    @Test
    fun `identical screenshots have no error`() {
        val reference = image(2, 2, Color.BLACK)
        val actual = image(2, 2, Color.BLACK)
        val metrics = ScreenshotComparator().compare(reference, actual)
        assertEquals(0.0, metrics.meanAbsoluteError)
        assertEquals(0.0, metrics.rootMeanSquareError)
        assertEquals(0.0, metrics.mismatchedPixelsPercent)
        assertEquals(0, metrics.maxChannelDelta)
    }

    @Test
    fun `difference image and metrics identify changed pixels`() {
        val reference = image(2, 1, Color.WHITE)
        val actualImage =
            BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, Color.WHITE.rgb)
                setRGB(1, 0, Color.RED.rgb)
            }
        val actual = write(actualImage)
        val diff = File.createTempFile("diff", ".png").also { it.delete() }
        val metrics = ScreenshotComparator().compare(reference, actual, diff)
        assertEquals(50.0, metrics.mismatchedPixelsPercent)
        assertEquals(255, metrics.maxChannelDelta)
        assertTrue(metrics.meanAbsoluteError > 0.0)
        assertTrue(diff.isFile)
    }

    @Test
    fun `different dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ScreenshotComparator().compare(image(2, 2, Color.BLACK), image(3, 2, Color.BLACK))
        }
    }

    private fun image(
        width: Int,
        height: Int,
        color: Color,
    ): File =
        write(
            BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                for (y in 0 until height) for (x in 0 until width) setRGB(x, y, color.rgb)
            },
        )

    private fun write(image: BufferedImage): File = File.createTempFile("screenshot", ".png").also { ImageIO.write(image, "png", it) }
}
