package app.sugarlicious.tools.screenshot

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt

data class ScreenshotMetrics(
    val width: Int,
    val height: Int,
    val meanAbsoluteError: Double,
    val rootMeanSquareError: Double,
    val mismatchedPixelsPercent: Double,
    val maxChannelDelta: Int,
)

class ScreenshotComparator {
    fun compare(
        reference: File,
        actual: File,
        diff: File? = null,
        threshold: Int = 0,
    ): ScreenshotMetrics {
        require(threshold in 0..255) { "Threshold must be between 0 and 255" }
        val expected = read(reference)
        val observed = read(actual)
        require(expected.width == observed.width && expected.height == observed.height) {
            "Image dimensions differ: ${expected.width}x${expected.height} versus ${observed.width}x${observed.height}"
        }

        val diffImage = BufferedImage(expected.width, expected.height, BufferedImage.TYPE_INT_ARGB)
        var absoluteError = 0L
        var squaredError = 0L
        var mismatchedPixels = 0L
        var maximumDelta = 0

        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val expectedColor = Color(expected.getRGB(x, y), true)
                val observedColor = Color(observed.getRGB(x, y), true)
                val deltas =
                    intArrayOf(
                        kotlin.math.abs(expectedColor.red - observedColor.red),
                        kotlin.math.abs(expectedColor.green - observedColor.green),
                        kotlin.math.abs(expectedColor.blue - observedColor.blue),
                        kotlin.math.abs(expectedColor.alpha - observedColor.alpha),
                    )
                val pixelMaximum = deltas.max()
                maximumDelta = maxOf(maximumDelta, pixelMaximum)
                deltas.forEach {
                    absoluteError += it
                    squaredError += it.toLong() * it
                }
                val mismatch = pixelMaximum > threshold
                if (mismatch) mismatchedPixels++
                diffImage.setRGB(x, y, if (mismatch) 0xFFFF0000.toInt() else 0x00000000)
            }
        }

        diff?.let {
            it.parentFile?.mkdirs()
            check(ImageIO.write(diffImage, "png", it)) { "No PNG writer available" }
        }
        val channelCount = expected.width.toLong() * expected.height * 4
        val pixelCount = expected.width.toLong() * expected.height
        return ScreenshotMetrics(
            width = expected.width,
            height = expected.height,
            meanAbsoluteError = absoluteError.toDouble() / channelCount,
            rootMeanSquareError = sqrt(squaredError.toDouble() / channelCount),
            mismatchedPixelsPercent = mismatchedPixels.toDouble() * 100.0 / pixelCount,
            maxChannelDelta = maximumDelta,
        )
    }

    private fun read(file: File): BufferedImage = requireNotNull(ImageIO.read(file)) { "Not a readable image: ${file.absolutePath}" }
}
