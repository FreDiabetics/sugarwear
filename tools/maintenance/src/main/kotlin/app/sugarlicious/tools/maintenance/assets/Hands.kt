package app.sugarlicious.tools.maintenance.assets

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal fun nativeHand(strip: BufferedImage): BufferedImage {
    require(strip.width == 50 && strip.height == 275) { "Invalid original canvas: ${strip.width}x${strip.height}" }
    return BufferedImage(450, 450, BufferedImage.TYPE_INT_ARGB).apply {
        // Direct raster copy preserves even RGB components under transparent alpha.
        setRGB(200, 0, 50, 275, strip.getRGB(0, 0, 50, 275, null, 0, 50), 0, 50)
    }
}

internal fun handTool(args: List<String>, root: Path) {
    val opts = options(args, setOf("--output"))
    val output = Path.of(opts["--output"] ?: root.resolve("watchfaces/sugarlicious-orbit/src/main/res/drawable-nodpi").toString())
    val reference = root.resolve("watchfaces/sugarlicious-orbit/reference/hands")
    val apex = root.resolve("watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi")
    Files.createDirectories(output)

    fun read(path: Path): BufferedImage {
        val image = ImageIO.read(path.toFile()) ?: error("Unsupported image $path")
        if (image.colorModel.colorSpace.type != java.awt.color.ColorSpace.TYPE_GRAY) return image
        // Pillow's LA -> RGBA conversion copies luminance bytes. ImageIO.getRGB
        // instead applies the gray color-space transfer curve, changing the art.
        require(image.raster.sampleModel.getSampleSize(0) == 8) { "Expected 8-bit grayscale hand: $path" }
        return BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val gray = image.raster.getSample(x, y, 0)
                    val alpha = if (image.colorModel.hasAlpha()) image.raster.getSample(x, y, 1) else 255
                    setRGB(x, y, (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray)
                }
            }
        }
    }

    fun save(image: BufferedImage, name: String) {
        ImageIO.write(image, "png", output.resolve(name).toFile())
    }
    for (style in 0..14) {
        for (kind in listOf("hour", "minute")) {
            val name = "orbit_${kind}_$style.png"
            save(nativeHand(read(reference.resolve(name))), name)
            if (style == 6) {
                val image = read(reference.resolve("orbit_${kind}_6_accent.png"))
                val canvas = BufferedImage(450, 450, BufferedImage.TYPE_INT_ARGB)
                require(image.width + 200 <= 450 && image.height <= 450)
                canvas.setRGB(200, 0, image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width), 0, image.width)
                save(canvas, "orbit_${kind}_6_accent.png")
            } else if (style == 14) {
                val canvas = BufferedImage(450, 450, BufferedImage.TYPE_INT_ARGB)
                val top = if (kind == "hour") 98 else 34
                for (y in top..161) for (x in 220..229) canvas.setRGB(x, y, -1)
                save(canvas, "orbit_${kind}_14_accent.png")
            }
        }
    }
    val second = BufferedImage(1800, 1800, BufferedImage.TYPE_INT_ARGB)
    PillowRaster.capsule(second, 894, 20, 906, 1052, 0xa6000000.toInt())
    PillowRaster.capsule(second, 894, 8, 906, 1040, -1)
    save(PillowRaster.resize(second, 450), "orbit_second_hand.png")
    for (style in 0..17) {
        var preview =
            BufferedImage(450, 450, BufferedImage.TYPE_INT_ARGB).apply {
                val black = IntArray(450 * 450) { 0xff000000.toInt() }
                setRGB(0, 0, 450, 450, black, 0, 450)
            }
        for ((kind, angle) in listOf("hour" to 304, "minute" to 48)) {
            var hand = if (style < 15) read(output.resolve("orbit_${kind}_$style.png")) else read(apex.resolve("${kind}_hand${listOf("","_transparent","_tblack")[style - 15]}.png"))
            if (style in listOf(6, 14)) {
                val accent = read(output.resolve("orbit_${kind}_${style}_accent.png"))
                hand = if (style == 14) PillowRaster.composite(hand, accent) else PillowRaster.composite(accent, hand)
            }
            if (hand.width != 450 || hand.height != 450) hand = PillowRaster.resize(hand, 450)
            preview = PillowRaster.composite(preview, PillowRaster.rotate(hand, -angle.toDouble(), 225.0, 225.0))
        }
        save(PillowRaster.resize(preview, 300), "orbit_hand_preview_$style.png")
    }
}
