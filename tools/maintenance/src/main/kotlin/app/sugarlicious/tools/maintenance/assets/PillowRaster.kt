/*
Algorithms translated from Pillow 11.3.0 src/libImaging.
https://github.com/python-pillow/Pillow/tree/11.3.0/src/libImaging

The Python Imaging Library (PIL) is

    Copyright © 1997-2011 by Secret Labs AB
    Copyright © 1995-2011 by Fredrik Lundh and contributors

Pillow is the friendly PIL fork. It is

    Copyright © 2010 by Jeffrey A. Clark and contributors

Like PIL, Pillow is licensed under the open source MIT-CMU License:

By obtaining, using, and/or copying this software and/or its associated
documentation, you agree that you have read, understood, and will comply
with the following terms and conditions:

Permission to use, copy, modify and distribute this software and its
documentation for any purpose and without fee is hereby granted,
provided that the above copyright notice appears in all copies, and that
both that copyright notice and this permission notice appear in supporting
documentation, and that the name of Secret Labs AB or the author not be
used in advertising or publicity pertaining to distribution of the software
without specific, written prior permission.

SECRET LABS AB AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS
SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS.
IN NO EVENT SHALL SECRET LABS AB OR THE AUTHOR BE LIABLE FOR ANY SPECIAL,
INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE
OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.

*/
package app.sugarlicious.tools.maintenance.assets

import java.awt.image.BufferedImage
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

/** Integer RGBA arithmetic and sampling matching Pillow 11.3's libImaging.
 * See upstream Resample.c, Geometry.c, Convert.c, Draw.c and AlphaComposite.c.
 * Coordinate conventions, 22-bit filter weights and truncation are intentional.
 */
internal object PillowRaster {
    private val shifts = intArrayOf(16, 8, 0, 24)
    private val rgbShifts = intArrayOf(16, 8, 0)

    private fun div255(value: Int) = ((value shr 8) + value) shr 8

    private fun mul255(value: Int, alpha: Int) = div255(value * alpha + 128)

    private fun premultiply(p: Int): Int {
        val a = p ushr 24
        var out = a shl 24
        for (s in rgbShifts) out = out or (mul255((p ushr s) and 255, a) shl s)
        return out
    }

    private fun straight(p: Int): Int {
        val a = p ushr 24
        if (a == 0 || a == 255) return p
        var out = a shl 24
        for (s in rgbShifts) out = out or ((((p ushr s) and 255) * 255 / a).coerceAtMost(255) shl s)
        return out
    }

    private fun image(width: Int, height: Int, pixels: IntArray) = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, width, height, pixels, 0, width) }

    private data class Coeff(
        val start: Int,
        val weights: IntArray,
    )

    private fun sinc(x: Double): Double {
        if (x == 0.0) return 1.0
        val angle = x * PI
        return sin(angle) / angle
    }

    private fun coeffs(input: Int, output: Int): List<Coeff> {
        val scale = input.toDouble() / output
        val filterScale = max(1.0, scale)
        val support = 3 * filterScale
        return (0 until output).map { destination ->
            val center = (destination + .5) * scale
            val first = max(0, (center - support + .5).toInt())
            val last = min(input, (center + support + .5).toInt())
            val values =
                DoubleArray(last - first) { index ->
                    val x = (index + first - center + .5) / filterScale
                    if (x >= -3 && x < 3) sinc(x) * sinc(x / 3) else 0.0
                }
            val sum = values.sum()
            Coeff(
                first,
                IntArray(values.size) {
                    val w = if (sum != 0.0) values[it] / sum else values[it]
                    (w * (1 shl 22) + if (w < 0) -.5 else .5).toInt()
                },
            )
        }
    }

    fun resize(source: BufferedImage, width: Int, height: Int = width): BufferedImage {
        if (source.width == width && source.height == height) return image(width, height, source.getRGB(0, 0, width, height, null, 0, width))
        val sw = source.width
        val sh = source.height
        val pixels = source.getRGB(0, 0, sw, sh, null, 0, sw).map(::premultiply).toIntArray()
        val horizontal =
            if (sw == width) {
                pixels
            } else {
                val coefficients = coeffs(sw, width)
                val result = IntArray(width * sh)
                for (y in 0 until sh) {
                    for (x in 0 until width) {
                        val coefficient = coefficients[x]
                        var p = 0
                        for (shift in shifts) {
                            var sum = 1 shl 21
                            coefficient.weights.forEachIndexed { index, weight -> sum += ((pixels[y * sw + coefficient.start + index] ushr shift) and 255) * weight }
                            p = p or ((sum shr 22).coerceIn(0, 255) shl shift)
                        }
                        result[y * width + x] = p
                    }
                }
                result
            }
        val vertical =
            if (sh == height) {
                horizontal
            } else {
                val coefficients = coeffs(sh, height)
                val result = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val coefficient = coefficients[y]
                        var p = 0
                        for (shift in shifts) {
                            var sum = 1 shl 21
                            coefficient.weights.forEachIndexed { index, weight -> sum += ((horizontal[(coefficient.start + index) * width + x] ushr shift) and 255) * weight }
                            p = p or ((sum shr 22).coerceIn(0, 255) shl shift)
                        }
                        result[y * width + x] = p
                    }
                }
                result
            }
        return image(width, height, vertical.map(::straight).toIntArray())
    }

    private fun cubic(v1: Double, v2: Double, v3: Double, v4: Double, d: Double): Double {
        val p1 = v2
        val p2 = -v1 + v3
        val p3 = 2 * (v1 - v2) + v3 - v4
        val p4 = -v1 + v2 - v3 + v4
        return p1 + d * (p2 + d * (p3 + d * p4))
    }

    private fun horizontalCubic(input: IntArray, width: Int, height: Int, x: Int, y: Int, shift: Int, d: Double): Double {
        val base = y.coerceIn(0, height - 1) * width
        val v1 = ((input[base + x.coerceIn(0, width - 1)] ushr shift) and 255).toDouble()
        val v2 = ((input[base + (x + 1).coerceIn(0, width - 1)] ushr shift) and 255).toDouble()
        val v3 = ((input[base + (x + 2).coerceIn(0, width - 1)] ushr shift) and 255).toDouble()
        val v4 = ((input[base + (x + 3).coerceIn(0, width - 1)] ushr shift) and 255).toDouble()
        return cubic(v1, v2, v3, v4, d)
    }

    fun rotate(source: BufferedImage, angle: Double, cx: Double = source.width / 2.0, cy: Double = source.height / 2.0): BufferedImage {
        val normalized = ((angle % 360) + 360) % 360
        val radians = -Math.toRadians(normalized)

        fun rounded(x: Double) = BigDecimal.valueOf(x).setScale(15, RoundingMode.HALF_EVEN).toDouble()
        val a = rounded(cos(radians))
        val b = rounded(sin(radians))
        val d = rounded(-sin(radians))
        val e = rounded(cos(radians))
        val c = a * (-cx) + b * (-cy) + cx
        val f = d * (-cx) + e * (-cy) + cy
        val w = source.width
        val h = source.height
        val input = source.getRGB(0, 0, w, h, null, 0, w).map(::premultiply).toIntArray()
        val output = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val xin = a * (x + .5) + b * (y + .5) + c
                val yin = d * (x + .5) + e * (y + .5) + f
                if (xin < 0 || xin >= w || yin < 0 || yin >= h) continue
                val fx = floor(xin - .5).toInt()
                val fy = floor(yin - .5).toInt()
                val dx = xin - .5 - fx
                val dy = yin - .5 - fy
                val firstX = fx - 1
                val firstY = fy - 1
                var pixel = 0
                for (shift in shifts) {
                    val v1 = horizontalCubic(input, w, h, firstX, firstY, shift, dx)
                    val v2 = if (firstY + 1 in 0 until h) horizontalCubic(input, w, h, firstX, firstY + 1, shift, dx) else v1
                    val v3 = if (firstY + 2 in 0 until h) horizontalCubic(input, w, h, firstX, firstY + 2, shift, dx) else v2
                    val v4 = if (firstY + 3 in 0 until h) horizontalCubic(input, w, h, firstX, firstY + 3, shift, dx) else v3
                    pixel = pixel or (cubic(v1, v2, v3, v4, dy).coerceIn(0.0, 255.0).toInt() shl shift)
                }
                output[y * w + x] = straight(pixel)
            }
        }
        return image(w, h, output)
    }

    fun composite(destination: BufferedImage, source: BufferedImage): BufferedImage {
        require(destination.width == source.width && destination.height == source.height)
        val w = source.width
        val h = source.height
        val dst = destination.getRGB(0, 0, w, h, null, 0, w)
        val src = source.getRGB(0, 0, w, h, null, 0, w)
        val out =
            IntArray(dst.size) { index ->
                val s = src[index]
                val t = dst[index]
                val sa = s ushr 24
                val da = t ushr 24
                if (sa == 0) {
                    t
                } else {
                    val blend = da * (255 - sa)
                    val outa255 = sa * 255 + blend
                    val coefficient1 = sa * 255 * 255 * 128 / outa255
                    val coefficient2 = 255 * 128 - coefficient1
                    var p = div255(outa255 + 128) shl 24
                    for (shift in rgbShifts) {
                        val value = ((s ushr shift) and 255) * coefficient1 + ((t ushr shift) and 255) * coefficient2
                        p = p or ((div255(value + (128 shl 7)) shr 7) shl shift)
                    }
                    p
                }
            }
        return image(w, h, out)
    }

    /** Filled circular-ended rectangle, with inclusive integer bounds as ImageDraw. */
    fun capsule(canvas: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        val diameter = x1 - x0
        require(diameter >= 0 && y1 - y0 >= diameter)
        // Pillow's quarter-ellipse Bresenham grid has a two-unit step.
        val extents = IntArray(diameter + 1)
        var x = diameter
        var y = diameter % 2
        val a2 = diameter.toLong() * diameter
        val a2b2 = a2 * a2

        fun delta(xx: Int, yy: Int) = abs(a2 * yy * yy + a2 * xx * xx - a2b2)
        while (true) {
            val rowTop = (diameter - y) / 2
            val rowBottom = (diameter + y) / 2
            extents[rowTop] = max(extents[rowTop], x)
            extents[rowBottom] = max(extents[rowBottom], x)
            if (x == diameter % 2 && y == diameter) break
            var nx = x
            var ny = y + 2
            var deviation = delta(nx, ny)
            if (nx > 1) {
                val diagonal = delta(x - 2, y + 2)
                if (deviation > diagonal) {
                    nx = x - 2
                    ny = y + 2
                    deviation = diagonal
                }
                if (deviation > delta(x - 2, y)) {
                    nx = x - 2
                    ny = y
                }
            }
            x = nx
            y = ny
        }
        val radius = diameter / 2
        for (yy in y0..y1) {
            val row =
                when {
                    yy <= y0 + radius -> yy - y0
                    yy >= y1 - radius -> diameter - (y1 - yy)
                    else -> radius
                }
            val extent = extents[row]
            for (xx in x0 + (diameter - extent) / 2..x0 + (diameter + extent) / 2) canvas.setRGB(xx, yy, color)
        }
    }
}
