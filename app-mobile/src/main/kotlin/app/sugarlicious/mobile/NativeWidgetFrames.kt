package app.sugarlicious.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.core.net.toUri
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

internal const val ACTION_CYCLE_WIDGET_GRAPH = "app.aapswear.action.CYCLE_WIDGET_GRAPH"
private val graphPeriodChanges = Mutex()

internal suspend fun cycleWidgetGraphConfiguration(context: Context, id: Int): List<Int> =
    graphPeriodChanges.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getAppWidgetInfo(id)?.provider ?: return@withLock emptyList()
        val kind = configurableWidgetKind(context, id, provider.className)
        val hours =
            WidgetRenderTransactions.run(id) {
                val current = WidgetInstanceConfigurationStore.readIsolated(context, id, kind)
                val next = nextWidgetGraphHours(current.graphHours)
                WidgetInstanceConfigurationStore.save(context, id, current.copy(graphHours = next))
                next
            }
        // Only the timeframe is shared; palettes, sizing and all other per-instance settings stay local.
        val linked =
            manager.installedProviders
                .filter { it.provider.packageName == context.packageName }
                .flatMap { candidate ->
                    manager.getAppWidgetIds(candidate.provider).filter { target ->
                        val options = manager.getAppWidgetOptions(target)
                        configurableWidgetKind(context, target, candidate.provider.className) == ConfigurableWidgetKind.METABOLIC &&
                            (candidate.provider.className.endsWith("TherapyFamily4x2Receiver") || options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 300)
                    }
                }.filter { it != id }
                .distinct()
        linked.forEach { target ->
            WidgetRenderTransactions.run(target) {
                val current = WidgetInstanceConfigurationStore.readIsolated(context, target, ConfigurableWidgetKind.METABOLIC)
                WidgetInstanceConfigurationStore.save(context, target, current.copy(graphHours = hours))
            }
        }
        listOf(id) + linked
    }

internal data class NativeWidgetFrameInfo(
    val size: DpSize,
    val insets: WidgetContentInsets,
    val launch: ComponentName,
    val fillBounds: Boolean = false,
)

internal val LocalNativeWidgetFrames = staticCompositionLocalOf<NativeWidgetFrames?> { null }
internal val LocalNativeWidgetFrameInfo = staticCompositionLocalOf<NativeWidgetFrameInfo?> { null }

/** Existing Canvas renderers remain authoritative. Only the published RemoteViews tree is native
 * and fixed: no nested Glance roots, no remove/add operations and no generated action targets.
 */
internal class NativeWidgetFrames {
    private val frames = ConcurrentHashMap<DpSize, Pair<NativeWidgetFrameInfo, Bitmap>>()

    fun capture(info: NativeWidgetFrameInfo, bitmap: Bitmap) {
        frames[info.size] = info to bitmap
    }

    fun remoteViews(context: Context, id: Int, kind: ConfigurableWidgetKind): RemoteViews {
        check(frames.isNotEmpty()) { "Widget $id did not produce a completed bitmap" }
        val variants =
            frames.values.sortedBy { it.first.size.width.value * it.first.size.height.value }.associate { (info, bitmap) ->
                SizeF(info.size.width.value, info.size.height.value) to nativeWidgetBitmapFrame(context, id, kind, info, bitmap)
            }
        return when {
            variants.size == 1 -> variants.values.first()
            Build.VERSION.SDK_INT >= 31 -> RemoteViews(variants)
            else -> RemoteViews(variants.entries.maxBy { it.key.width }.value, variants.entries.minBy { it.key.width }.value)
        }
    }
}

@Composable
internal fun WidgetBitmapImage(bitmap: Bitmap, description: String, modifier: GlanceModifier, contentScale: ContentScale) {
    val frames = LocalNativeWidgetFrames.current
    val info = LocalNativeWidgetFrameInfo.current
    SideEffect { if (frames != null && info != null) frames.capture(info.copy(fillBounds = contentScale == ContentScale.FillBounds), bitmap) }
    Image(ImageProvider(bitmap), description, modifier, contentScale = contentScale)
}

internal fun nativeWidgetBitmapFrame(
    context: Context,
    id: Int,
    kind: ConfigurableWidgetKind,
    info: NativeWidgetFrameInfo,
    bitmap: Bitmap,
): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_bitmap_frame).apply {
        val density = context.resources.displayMetrics.density
        val horizontal = (info.insets.horizontalDp * density).toInt()
        val vertical = (info.insets.verticalDp * density).toInt()
        setViewPadding(android.R.id.background, horizontal, vertical, horizontal, vertical)
        val imageId = if (info.fillBounds) R.id.widget_bitmap_fill else R.id.widget_bitmap
        setViewVisibility(R.id.widget_bitmap, if (info.fillBounds) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_bitmap_fill, if (info.fillBounds) View.VISIBLE else View.GONE)
        setImageViewBitmap(imageId, bitmap)
        setContentDescription(imageId, "Sugarlicious ${kind.name}")
        setOnClickPendingIntent(
            android.R.id.background,
            PendingIntent.getActivity(
                context,
                id,
                Intent.makeMainActivity(info.launch),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val splitGraph = kind == ConfigurableWidgetKind.GLUCOSE && widgetFamilyLayout(info.size.width.value, info.size.height.value) == WidgetFamilyLayout.WIDE_4X2
        val graph = kind == ConfigurableWidgetKind.GRAPH || splitGraph
        setViewVisibility(R.id.widget_scale_action, if (graph) View.VISIBLE else View.GONE)
        if (graph) {
            if (Build.VERSION.SDK_INT >= 31) {
                setViewLayoutMargin(R.id.widget_scale_action, RemoteViews.MARGIN_START, if (splitGraph) info.size.width.value * .535f else 0f, android.util.TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutMargin(R.id.widget_scale_action, RemoteViews.MARGIN_TOP, if (splitGraph) info.size.height.value * .12f else 0f, android.util.TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutWidth(R.id.widget_scale_action, if (splitGraph) minOf(140f, info.size.width.value * .43f) else 140f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            }
            val provider = checkNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id)).provider
            setContentDescription(R.id.widget_scale_action, "Graphskalierung ändern")
            setOnClickPendingIntent(
                R.id.widget_scale_action,
                PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(ACTION_CYCLE_WIDGET_GRAPH)
                        .setComponent(provider)
                        .setData("sugarlicious://widget/$id/scale".toUri())
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }
