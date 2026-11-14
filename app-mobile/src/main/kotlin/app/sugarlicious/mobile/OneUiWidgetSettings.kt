package app.sugarlicious.mobile

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import app.sugarlicious.uishared.ScrollSafeSeekBar
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as UiColor

internal fun oneUiButtonContentColor(background: UiColor): UiColor = if (background.luminance() > .179f) UiColor.Black else UiColor.White

internal data class OneUiWidgetSettingsColors(
    val page: UiColor = UiColor.Black,
    val card: UiColor = UiColor(0xFF1C1C1C),
    val primary: UiColor = UiColor.White,
    val secondary: UiColor = UiColor.White,
    val accent: UiColor = UiColor(0xFFADC5C9),
    val link: UiColor = UiColor.White,
    val action: UiColor = UiColor(0xFF1C1C1C),
)

internal val LocalWidgetHeaderExpansionDp = staticCompositionLocalOf { 0f }

internal val LocalOneUiWidgetSettingsColors = staticCompositionLocalOf { OneUiWidgetSettingsColors() }

internal object OneUiWidgetStyle {
    val primary: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.primary
    val secondary: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.secondary
    val accent: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.accent
    val link: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.link
    val card: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.card
    val action: UiColor @Composable get() = LocalOneUiWidgetSettingsColors.current.action
}

/** Show the OS-managed wallpaper; never request permission to read or copy its image. */
@Suppress("DEPRECATION") // Transparent system bars are still required before enforced edge-to-edge.
internal fun configureOneUiWidgetSettingsWindow(window: Window) {
    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT < 35) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
    }
    WindowCompat.getInsetsController(window, window.decorView).also {
        it.isAppearanceLightStatusBars = false
        it.isAppearanceLightNavigationBars = false
    }
}

@Composable
internal fun OneUiWidgetSettingsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val platformScheme =
        if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    val brand = app.sugarlicious.uishared.SugarliciousAccentPalette
    val accent = UiColor(brand.primary(dark))
    val scheme =
        platformScheme.copy(
            primary = accent,
            onPrimary = UiColor(brand.onPrimary),
            primaryContainer = UiColor(brand.container),
            onPrimaryContainer = UiColor(brand.secondary),
            secondary = UiColor(brand.secondary),
            onSecondary = UiColor.Black,
            secondaryContainer = UiColor(brand.container),
            onSecondaryContainer = UiColor(brand.secondary),
            tertiary = UiColor(brand.tertiary),
            onTertiary = UiColor.Black,
            inversePrimary = accent,
            surface = if (dark) UiColor(0xFF1C1C1C) else UiColor.White,
            onSurface = if (dark) UiColor.White else UiColor(0xFF252525),
            onSurfaceVariant = if (dark) UiColor.White else UiColor(0xFF666666),
        )
    val colors =
        if (dark) {
            OneUiWidgetSettingsColors(accent = scheme.primary)
        } else {
            OneUiWidgetSettingsColors(
                page = UiColor(0xFFF2F2F2),
                card = UiColor.White,
                primary = UiColor(0xFF252525),
                secondary = UiColor(0xFF666666),
                link = UiColor(0xFF252525),
                action = UiColor(0xFFE7E7E7),
                accent = scheme.primary,
            )
        }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val controlContext = remember(context, configuration, dark) { mobileSettingsControlContext(context, dark) }
    CompositionLocalProvider(LocalOneUiWidgetSettingsColors provides colors, LocalContext provides controlContext, LocalContentColor provides colors.primary) {
        MaterialTheme(colorScheme = scheme, typography = oneUiMenuTypography) {
            ProvideTextStyle(oneUiMenuTypography.bodyLarge.copy(color = UiColor.Unspecified)) { content() }
        }
    }
}

@Composable
internal fun OneUiWidgetSettingsSurface(scroll: androidx.compose.foundation.ScrollState? = null, onBack: () -> Unit = {}, lazyScroll: androidx.compose.foundation.lazy.LazyListState? = null, title: String = "Widget-Einstellungen", content: @Composable BoxScope.((Rect) -> Unit) -> Unit) {
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    val colors = LocalOneUiWidgetSettingsColors.current
    val context = LocalContext.current
    val wallpaperSupported = remember(context) { runCatching { WallpaperManager.getInstance(context).isWallpaperSupported }.getOrDefault(false) }
    val density = LocalDensity.current
    val compactHeaderHeightDp = measuredSettingsCompactTitle(title, settingsWindowWidthDp()).heightDp
    val headerClearanceDp = compactHeaderHeightDp + SETTINGS_SUBMENU_CARD_GAP_DP
    val inset = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.toPx() }
    var headerExpansionDp by remember(scroll, lazyScroll) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var pullingHeader by remember(scroll, lazyScroll) { mutableStateOf(false) }
    val expandedTitleHeightDp = measuredSettingsExpandedTitleHeightDp(title)
    val maximumExpansionDp = maxOf(160f, expandedTitleHeightDp + compactHeaderHeightDp + 16f)
    val headerDecay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
    val scrollPositionPx: () -> Int = {
        scroll?.value ?: lazyScroll?.let { if (it.firstVisibleItemIndex == 0) it.firstVisibleItemScrollOffset else 4096 } ?: 0
    }
    val pullConnection =
        remember(scroll, lazyScroll, density, maximumExpansionDp) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if ((scroll == null && lazyScroll == null) || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || scrollPositionPx() != 0 || (available.y <= 0f && headerExpansionDp == 0f && !pullingHeader)) return androidx.compose.ui.geometry.Offset.Zero
                    val previous = headerExpansionDp
                    headerExpansionDp = (previous + with(density) { available.y.toDp().value } * .55f).coerceIn(0f, maximumExpansionDp)
                    pullingHeader = pullingHeader || headerExpansionDp != previous
                    return androidx.compose.ui.geometry
                        .Offset(0f, (headerExpansionDp - previous) * density.density / .55f)
                }

                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (pullingHeader) {
                        pullingHeader = false
                        if (headerExpansionDp == 0f) return androidx.compose.ui.unit.Velocity.Zero
                        var remainingVelocity = 0f
                        androidx.compose.animation.core.AnimationState(headerExpansionDp, available.y / density.density * .55f).animateDecay(headerDecay) {
                            headerExpansionDp = value.coerceIn(0f, maximumExpansionDp)
                            if (value <= 0f) {
                                remainingVelocity = velocity * density.density / .55f
                                cancelAnimation()
                            } else if (value >= maximumExpansionDp) {
                                cancelAnimation()
                            }
                        }
                        val target = settingsSubmenuExpansionTarget(headerExpansionDp, maximumExpansionDp)
                        androidx.compose.animation.core
                            .animate(
                                headerExpansionDp,
                                target,
                                animationSpec =
                                    androidx.compose.animation.core
                                        .tween(320),
                            ) { value, _ -> headerExpansionDp = value }
                        return androidx.compose.ui.unit
                            .Velocity(0f, available.y - remainingVelocity)
                    }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
    val registry = remember(scroll, inset) { scroll?.let { SettingsHeadingRegistry(it, inset) } }
    val snapConnection = rememberSettingsSnapConnection(registry)
    CompositionLocalProvider(LocalSettingsHeadingRegistry provides registry, LocalWidgetHeaderExpansionDp provides headerExpansionDp) {
        Box(
            Modifier.fillMaxSize().nestedScroll(pullConnection).nestedScroll(snapConnection).onGloballyPositioned { registry?.viewportTop = it.positionInWindow().y }.drawWithContent {
                val aperture = previewBounds?.takeIf { it.width > 0f && it.height > 0f }
                val path =
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        aperture?.let { addRoundRect(RoundRect(it, CornerRadius(20.dp.toPx()))) }
                    }
                drawPath(path, colors.page)
                if (!wallpaperSupported && aperture != null) {
                    drawRoundRect(colors.card, aperture.topLeft, aperture.size, CornerRadius(20.dp.toPx()))
                }
                drawContent()
            },
        ) {
            content { previewBounds = it }
            if (scroll != null || lazyScroll != null) {
                val topInsetDp = with(density) { inset.toDp().value - 56f }
                val headerState by remember(scroll, lazyScroll, density, topInsetDp, headerClearanceDp, maximumExpansionDp, expandedTitleHeightDp) {
                    androidx.compose.runtime.derivedStateOf {
                        val scrollDp = with(density) { scrollPositionPx().toDp().value }
                        val firstTop = previewBounds?.let { with(density) { it.top.toDp().value } + scrollDp - topInsetDp } ?: (headerClearanceDp + headerExpansionDp)
                        settingsSubmenuHeaderState(scrollDp, headerExpansionDp, maximumExpansionDp, firstTop, compactHeaderHeightDp - 4f, expandedTitleHeightDp)
                    }
                }
                if (headerExpansionDp > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .offset {
                                androidx.compose.ui.unit
                                    .IntOffset(0, headerState.expandedTitleTopDp.dp.roundToPx())
                            }.fillMaxWidth()
                            .height(expandedTitleHeightDp.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            brandedSettingsTitleText(title),
                            color = OneUiWidgetStyle.primary,
                            style = oneUiMenuTypography.headlineLarge.copy(fontSize = 34.sp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp).graphicsLayer { alpha = headerState.expandedTitleAlpha },
                        )
                    }
                }
                OneUiCompactSettingsHeader(
                    onBack,
                    Modifier.align(Alignment.TopStart).statusBarsPadding().offset {
                        androidx.compose.ui.unit
                            .IntOffset(0, headerState.rowOffsetDp.dp.roundToPx())
                    },
                    headerState,
                    title,
                )
            }
        }
    }
}

internal data class OneUiWidgetPreviewPlacement(
    val widthDp: Float,
    val heightDp: Float,
)

internal fun commitWidgetSettings(commit: () -> Unit, publish: () -> Unit, rollback: () -> Unit) {
    try {
        commit()
        publish()
    } catch (error: Exception) {
        rollback()
        throw error
    }
}

internal fun oneUiWidgetPreviewPlacement(widthDp: Float, heightDp: Float, hostResizeRatio: Float, frameWidthDp: Float): OneUiWidgetPreviewPlacement {
    val ratio = hostResizeRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = widthDp.coerceAtLeast(1f) * ratio
    val height = heightDp.coerceAtLeast(1f) * ratio
    val fit = minOf(1f, (frameWidthDp * .72f).coerceAtLeast(1f) / width, 122f / height)
    return OneUiWidgetPreviewPlacement(width * fit, height * fit)
}

@Composable
internal fun <T> OneUiWidgetChoiceRow(label: String, value: T, choices: List<T>, display: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val colors = LocalOneUiWidgetSettingsColors.current
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(label, color = colors.primary, fontSize = 16.sp)
                Text(display(value), color = colors.link, style = oneUiMenuTypography.bodyMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = colors.card) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(display(choice), color = colors.primary) }, onClick = {
                    expanded = false
                    onSelected(choice)
                })
            }
        }
    }
}

@Composable
internal fun WidgetAppearanceModeRow(selected: app.sugarlicious.model.AppearanceMode, onSelected: (app.sugarlicious.model.AppearanceMode) -> Unit) {
    Text("Farbpalette bearbeiten", color = OneUiWidgetStyle.secondary)
    OneUiSettingsModeSelector(
        if (selected == app.sugarlicious.model.AppearanceMode.LIGHT) DashboardThemeMode.LIGHT else DashboardThemeMode.DARK,
        showSystemChoice = false,
        showIllustrations = false,
    ) { onSelected(if (it == DashboardThemeMode.LIGHT) app.sugarlicious.model.AppearanceMode.LIGHT else app.sugarlicious.model.AppearanceMode.DARK) }
}

@Composable
internal fun OneUiWidgetSeekBar(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val callback by rememberUpdatedState(onChange)
    val currentRange by rememberUpdatedState(range)
    val colors = LocalOneUiWidgetSettingsColors.current
    AndroidView(
        factory = { context ->
            OneUiSeekBar(context).apply {
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) callback(currentRange.first + progress)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            }
        },
        update = { view ->
            view.contentDescription = label
            view.max = (range.last - range.first).coerceAtLeast(1)
            val progress = (value - range.first).coerceIn(0, view.max)
            if (view.progress != progress) view.progress = progress
            view.palette(colors.accent.toArgb(), colors.card.toArgb())
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
}

@Composable
internal fun MobileSettingsSeekBar(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onFinished: () -> Unit) {
    val callback by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val currentRange by rememberUpdatedState(range)
    val colors = LocalOneUiWidgetSettingsColors.current
    AndroidView(
        factory = { context ->
            OneUiSeekBar(context, showTicks = false).apply {
                max = 1000
                val interaction =
                    SettingsSliderInteraction(
                        { progress -> callback(currentRange.start + (currentRange.endInclusive - currentRange.start) * progress / 1000f) },
                        { finished() },
                    )
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            interaction.changed(progress, fromUser)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = interaction.started()

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = interaction.stopped()
                    },
                )
            }
        },
        update = { view ->
            view.contentDescription = label
            val span = range.endInclusive - range.start
            val progress = if (span > 0f) ((value - range.start) / span * 1000).roundToInt().coerceIn(0, 1000) else 0
            if (view.progress != progress) view.progress = progress
            view.palette(colors.accent.toArgb(), colors.card.toArgb())
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
}

internal class OneUiSeekBar
    @JvmOverloads
    constructor(
        context: Context,
        private val showTicks: Boolean = true,
    ) : ScrollSafeSeekBar(context) {
        private val density = resources.displayMetrics.density
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var currentPalette: Pair<Int, Int>? = null

        fun palette(accent: Int, surface: Int) {
            if (currentPalette == (accent to surface)) return
            currentPalette = accent to surface

            fun track(alpha: Int) =
                GradientDrawable().apply {
                    setColor((accent and 0x00FFFFFF) or (alpha shl 24))
                    cornerRadius = 7f * density
                    setSize(1, (14f * density).roundToInt())
                }
            progressDrawable =
                LayerDrawable(arrayOf(track(60), ClipDrawable(track(130), Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                    setId(0, android.R.id.background)
                    setId(1, android.R.id.progress)
                    for (index in 0..1) {
                        setLayerHeight(index, (14f * density).roundToInt())
                        setLayerGravity(index, Gravity.CENTER_VERTICAL)
                    }
                }
            thumb =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(surface)
                    setStroke((2f * density).roundToInt(), accent)
                    setSize((20f * density).roundToInt(), (20f * density).roundToInt())
                }
            thumbOffset = (10f * density).roundToInt()
            splitTrack = false
            thumbTintList = null
            progressTintList = null
            progressBackgroundTintList = null
            tickMark = null
            setPadding(thumbOffset, 0, thumbOffset, 0)
            tickPaint.color = accent
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!showTicks) return
            val inset = 8f * density
            val trackWidth = width - paddingLeft - paddingRight - 2f * inset
            val currentX = paddingLeft + (width - paddingLeft - paddingRight) * progress / max.coerceAtLeast(1).toFloat()
            listOf(0f, .5f, 1f).forEach { fraction ->
                val x = paddingLeft + inset + trackWidth * fraction
                if (abs(x - currentX) > 12f * density) canvas.drawCircle(x, height / 2f, 4f * density, tickPaint)
            }
        }
    }

@Composable
internal fun WidgetSettingsToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true, label: String? = null) {
    val callback by rememberUpdatedState(onCheckedChange)
    val currentChecked by rememberUpdatedState(checked)
    AndroidView(
        factory = { context ->
            android.widget.Switch(context).apply {
                showText = false
                styleMobileSettingsSwitch(this)
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setOnCheckedChangeListener { _, value -> if (value != currentChecked) callback?.invoke(value) }
            }
        },
        update = {
            it.contentDescription = label
            it.isChecked = checked
            it.isEnabled = enabled && onCheckedChange != null
        },
        modifier = Modifier.width(64.dp).height(48.dp).padding(end = 10.dp),
    )
}

@Composable
internal fun WidgetToggleSetting(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), color = OneUiWidgetStyle.primary, fontSize = 16.sp)
        WidgetSettingsToggle(checked, onCheckedChange, enabled, label)
    }
}
