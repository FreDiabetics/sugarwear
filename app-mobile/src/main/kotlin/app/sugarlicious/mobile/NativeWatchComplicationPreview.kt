package app.sugarlicious.mobile

import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.withRotation
import app.sugarlicious.model.SugarliciousComplicationIds
import app.sugarlicious.model.Trend
import app.sugarlicious.model.TrendVisuals
import app.sugarlicious.uishared.SystemTypography

internal data class NativeResolvedText(
    val template: NativeSlotText,
    val value: String,
)

/** WFF centers glyphs on TextCircular; Canvas instead positions their baseline on the path. */
internal fun nativeCircularTextBaselineOffset(ascent: Float, descent: Float): Float = -(ascent + descent) / 2f

internal fun nativeTrendIconResource(trend: Trend): Int? =
    when (trend) {
        Trend.DOUBLE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_doubleup
        Trend.SINGLE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_up
        Trend.FORTY_FIVE_UP -> app.sugarlicious.uishared.R.drawable.ic_trend_fortyfiveup
        Trend.FLAT -> app.sugarlicious.uishared.R.drawable.ic_trend_flat
        Trend.FORTY_FIVE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_fortyfivedown
        Trend.SINGLE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_down
        Trend.DOUBLE_DOWN -> app.sugarlicious.uishared.R.drawable.ic_trend_doubledown
        Trend.UNKNOWN -> null
    }

internal fun nativeCircularGlucoseTrendTemplate(slot: NativeWatchSlot, template: NativeSlotTemplate, trend: Trend): NativeSlotTemplate {
    val b = slot.bounds
    val main = template.texts.firstOrNull { it.expression.contains("TEXT") } ?: return template
    val textHeight = b.height * .32f
    val iconHeight = b.height * .145f
    val iconWidth = iconHeight * (TrendVisuals.spec(trend)?.aspectRatio ?: 1f)
    return template.copy(
        texts = listOf(main.copy(bounds = NativeSlotBounds("BoundingBox", b.x + b.width * .1f, b.y + b.height * .48f - textHeight / 2, b.width * .8f, textHeight), size = b.width * .24f, emptyTitleY = null)),
        icon = NativeSlotBounds("BoundingBox", b.x + (b.width - iconWidth) / 2, b.y + b.height * .85f - iconHeight / 2, iconWidth, iconHeight),
        iconAngle = 0f,
    )
}

internal fun nativeDemoValue(demo: PhonePreview, hasTrendIcon: Boolean): String =
    demo.secondary.ifBlank { demo.primary } +
        if (hasTrendIcon) {
            ""
        } else {
            demo.trend
                ?.let {
                    " " +
                        app.sugarlicious.model.TherapyDisplayFormatter
                            .trendArrow(it)
                }.orEmpty()
        }

/** Collapse native alternative color/unit branches into one active title and one active value. */
internal fun resolveNativeTemplateText(template: NativeSlotTemplate, title: String, value: String): List<NativeResolvedText> =
    template.texts
        .mapNotNull { text ->
            var content =
                when {
                    text.expression.contains("TITLE") && text.expression.contains("TEXT") ->
                        Regex("\\[COMPLICATION\\.(TEXT|TITLE)]")
                            .findAll(text.expression)
                            .map { if (it.groupValues[1] == "TITLE") title else value }
                            .filter(String::isNotBlank)
                            .joinToString(" ")
                    text.expression.contains("TITLE") -> title
                    else -> value
                }
            if (content.isBlank()) return@mapNotNull null
            if (text.therapyUnitFormatting) {
                val parts = Regex("^([-+]?[0-9]+(?:[.,][0-9]+)?)\\s*([Ug])$").matchEntire(content)
                if (parts != null && parts.groupValues[1].length <= 9) {
                    val number = parts.groupValues[1]
                    val unit = parts.groupValues[2]
                    val formatted = if (unit == "U") java.text.DecimalFormat("#.#", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(number.replace(',', '.').toDouble()) else number
                    content = "$formatted\u2009$unit"
                }
            }
            val active = if (title.isBlank() && text.emptyTitleY != null) text.copy(bounds = text.bounds.copy(y = text.emptyTitleY)) else text
            NativeResolvedText(active, content)
        }.distinctBy { it.template.bounds to it.value }

/** Same gauge limits as the native providers, applied to the displayed demo value. */
internal fun nativeDemoProgress(id: Int?, text: String, systemProvider: String): Float? {
    val number =
        Regex("[-+]?\\d+(?:[.,]\\d+)?")
            .find(text)
            ?.value
            ?.replace(',', '.')
            ?.toFloatOrNull() ?: return null
    val base = id?.let(SugarliciousComplicationIds::baseId)
    val fraction =
        when {
            base == SugarliciousComplicationIds.IOB -> number / 10f
            base == SugarliciousComplicationIds.COB -> number / 150f
            base == SugarliciousComplicationIds.TIR -> number / 100f
            base == SugarliciousComplicationIds.SENSOR_AGE -> number / 14f
            base in setOf(SugarliciousComplicationIds.GLUCOSE, SugarliciousComplicationIds.GLUCOSE_TREND) -> (number - 40f) / 220f
            systemProvider == "WATCH_BATTERY" -> number / 100f
            systemProvider == "STEP_COUNT" -> text.filter(Char::isDigit).toFloatOrNull()?.div(10_000f) ?: return null
            else -> return null
        }
    return fraction.coerceIn(0f, 1f)
}

internal fun nativeDefaultVariant(slot: NativeWatchSlot): Int? {
    val provider = slot.provider.substringAfterLast('.')
    val base =
        when (provider) {
            "TirRangedValueComplication" -> SugarliciousComplicationIds.TIR
            "IobRangedValueComplication" -> SugarliciousComplicationIds.IOB
            "CobRangedValueComplication" -> SugarliciousComplicationIds.COB
            "GlucoseTrendRangedValueComplication" -> SugarliciousComplicationIds.GLUCOSE_TREND
            "GlucoseDeltaComplication" -> SugarliciousComplicationIds.TIME_DELTA
            "DateComplication" -> SugarliciousComplicationIds.DATE
            else -> return null
        }
    return eligibleNativeVariants(slot).firstOrNull { SugarliciousComplicationIds.baseId(it.id) == base && it.type.name == slot.providerType }?.id
        ?: eligibleNativeVariants(slot).firstOrNull { SugarliciousComplicationIds.baseId(it.id) == base }?.id
}

/** Dynamic phone-side demo, using the text, icon and track geometry of the actual WFF template. */
@Composable
internal fun NativeWatchComplicationPreview(faceIndex: Int, slots: List<NativeWatchSlot>, assignments: Map<Int, Int>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val atlas = remember(faceIndex, resources) { BitmapFactory.decodeResource(resources, if (faceIndex == 0) R.drawable.apex_slots_native else R.drawable.orbit_slots_native) }
    val erasePaint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter =
                    ColorMatrixColorFilter(
                        ColorMatrix(
                            floatArrayOf(
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                0f,
                                3.4016f,
                                11.4432f,
                                1.1552f,
                                0f,
                                -8f,
                            ),
                        ),
                    )
            }
        }
    Canvas(modifier) {
        withTransform({ scale(size.minDimension / 512f, size.minDimension / 512f, Offset.Zero) }) {
            slots.filter { it.id in assignments }.forEach { slot ->
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    val saved = canvas.saveLayer(null, null)
                    canvas.clipPath(nativeDynamicPreviewBackdrop(slot, nativeDefaultVariant(slot)).asAndroidPath())
                    canvas.drawBitmap(atlas, null, RectF(0f, 0f, 512f, 512f), erasePaint)
                    canvas.restoreToCount(saved)
                }
                drawNativeSlotDemo(slot, assignments[slot.id], context)
            }
        }
    }
}

internal fun DrawScope.drawNativeSlotDemo(slot: NativeWatchSlot, variantId: Int?, context: android.content.Context) {
    val id = variantId ?: nativeDefaultVariant(slot)
    val variant = eligibleNativeVariants(slot).firstOrNull { it.id == id }
    val type = variant?.type?.name ?: slot.providerType
    val originalTemplate = slot.templates[type] ?: slot.templates[slot.providerType] ?: return
    val demo = id?.let { previewFor(it, null) }
    val circularTrend = type == "RANGED_VALUE" && id?.let(SugarliciousComplicationIds::baseId) == SugarliciousComplicationIds.GLUCOSE_TREND && slot.bounds.kind == "BoundingOval"
    val template = if (circularTrend && demo?.trend != null) nativeCircularGlucoseTrendTemplate(slot, originalTemplate, demo.trend) else originalTemplate
    val systemText =
        when (slot.systemProvider) {
            "WATCH_BATTERY" -> "76%"
            "STEP_COUNT" -> "6.540"
            "NEXT_EVENT" -> "14:30 Termin"
            else -> "—"
        }
    drawIntoCanvas { composeCanvas ->
        val canvas = composeCanvas.nativeCanvas
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                SystemTypography.apply(this, emphasized = true)
                textAlign = Paint.Align.CENTER
            }
        template.track?.takeIf { type in setOf("RANGED_VALUE", "GOAL_PROGRESS") }?.let { b ->
            val rect = RectF(b.x, b.y, b.x + b.width, b.y + b.height)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = b.thickness
            paint.strokeCap = Paint.Cap.ROUND
            val fraction = nativeDemoProgress(id, demo?.let { it.secondary.ifBlank { it.primary } } ?: systemText, slot.systemProvider)
            val sweep = nativeArcSweep(b.startAngle, b.endAngle, template.trackClockwise)
            val remaining =
                if (template.trackGapDegrees > 0f && fraction != null && fraction > 0f) {
                    (kotlin.math.abs(sweep) * (1 - fraction) - template.trackGapDegrees).coerceAtLeast(0f) * kotlin.math.sign(sweep)
                } else {
                    sweep
                }
            paint.color = template.trackColor
            if (remaining != 0f) canvas.drawArc(rect, b.startAngle - 90f, remaining, false, paint)
            paint.color = template.fillColor
            if (fraction != null) {
                val fillSweep = kotlin.math.abs(sweep) * fraction * if (template.fillClockwise) 1f else -1f
                canvas.drawArc(rect, (template.fillStartAngle ?: b.startAngle) - 90f, fillSweep, false, paint)
            }
            paint.style = Paint.Style.FILL
        }
        val title = demo?.primary?.takeIf { demo.secondary.isNotBlank() }.orEmpty()
        val value =
            demo?.let { nativeDemoValue(it, template.icon != null && it.trend?.let(::nativeTrendIconResource) != null) } ?: systemText
        resolveNativeTemplateText(template, title, value).forEach { resolved ->
            val text = resolved.template
            val value = resolved.value
            paint.color = text.color
            paint.textSize = text.size
            paint.letterSpacing = text.letterSpacing
            val b = text.bounds
            if (b.kind == "BoundingArc") {
                val sweep = if (text.clockwise) (b.endAngle - b.startAngle + 360f) % 360f else -((b.startAngle - b.endAngle + 360f) % 360f)
                val path = android.graphics.Path().apply { addArc(RectF(b.x, b.y, b.x + b.width, b.y + b.height), b.startAngle - 90f, sweep) }
                val length = android.graphics.PathMeasure(path, false).length
                if (paint.measureText(value) > length * .94f) paint.textSize *= length * .94f / paint.measureText(value)
                val textWidth = paint.measureText(value)
                val position =
                    when (text.align) {
                        "START" -> 0f
                        "END" -> length - textWidth
                        else -> (length - textWidth) / 2f
                    }
                // Android applies Paint.Align to the whole path; use one explicit alignment only.
                paint.textAlign = Paint.Align.LEFT
                canvas.drawTextOnPath(value, path, position, nativeCircularTextBaselineOffset(paint.ascent(), paint.descent()), paint)
                paint.textAlign = Paint.Align.CENTER
            } else {
                if (paint.measureText(value) > b.width * .94f) paint.textSize *= b.width * .94f / paint.measureText(value)
                canvas.drawText(value, b.x + b.width / 2, b.y + b.height / 2 - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
        template.icon?.let { b ->
            if (type in setOf("SMALL_IMAGE", "LARGE_IMAGE")) {
                val values = listOf(132f, 128f, 125f, 130f, 138f, 142f, 139f, 144f, 149f, 145f, 141f, 138f)
                val path = android.graphics.Path()
                values.forEachIndexed { index, value ->
                    val x = b.x + b.width * index / (values.size - 1)
                    val y = b.y + b.height * (1 - (value - 70f) / 130f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                paint.color = template.iconColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                return@let
            }
            val iconRes =
                demo?.trend?.let(::nativeTrendIconResource) ?: id?.let(::catalogPreviewIcon) ?: when (slot.systemProvider) {
                    "WATCH_BATTERY" -> R.drawable.ic_device_vendor_watch_battery
                    // DeviceVendor Health steps_complication_previews.xml uses setting_list_ic_step.png.
                    "STEP_COUNT" -> R.drawable.ic_device_vendor_health_steps_complication
                    "NEXT_EVENT" -> android.R.drawable.ic_menu_my_calendar
                    else -> null
                }
            val icon = iconRes?.let { ContextCompat.getDrawable(context, it)?.mutate() }
            if (icon != null) {
                val iconBounds =
                    demo?.trend?.let(TrendVisuals::spec)?.let { spec ->
                        val width = b.height * spec.aspectRatio
                        b.copy(x = b.x + (b.width - width) / 2, width = width)
                    } ?: b
                icon.setTint(template.iconColor)
                icon.setBounds(iconBounds.x.toInt(), iconBounds.y.toInt(), (iconBounds.x + iconBounds.width).toInt(), (iconBounds.y + iconBounds.height).toInt())
                canvas.withRotation(template.iconAngle, b.x + b.width / 2, b.y + b.height / 2) {
                    icon.draw(this)
                }
            }
        }
    }
}
