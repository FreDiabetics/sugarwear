package app.sugarlicious.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousColorStore
import app.sugarlicious.mobile.ui.theme.SugarliciousColors
import app.sugarlicious.model.SettingsSchemaVersions
import app.sugarlicious.storage.ensureSettingsSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

private const val GRAPH_MINUTE_MS = 60_000L

internal fun delayUntilNextGraphMinute(nowEpochMs: Long): kotlin.time.Duration {
    val remainder = Math.floorMod(nowEpochMs, GRAPH_MINUTE_MS)
    return (GRAPH_MINUTE_MS - remainder).coerceAtLeast(1L).milliseconds
}

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var resumeLiveNotificationSetup: Boolean
        get() = settingsViewModel.uiState.value.resumeLiveNotificationSetup
        set(value) = settingsViewModel.setResumeLiveNotificationSetup(value)
    private var promotionSettingsRequestedThisSession: Boolean
        get() = settingsViewModel.uiState.value.promotionSettingsRequestedThisSession
        set(value) = settingsViewModel.setPromotionSettingsRequested(value)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val uiPreferences by lazy { getSharedPreferences("dashboard_ui", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var scroll: DashboardScrollView
    private lateinit var factory: DashboardViewFactory
    private val settingsBackCircle =
        android.graphics.drawable
            .GradientDrawable()
            .apply { shape = android.graphics.drawable.GradientDrawable.OVAL }
    private val settingsTopShade =
        android.graphics.drawable
            .GradientDrawable()
            .apply { orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM }
    private var settingsShadeColor: Int? = null
    private var submenuExpansionPx = 0f
    private var submenuExpansionTitle: String? = null
    private var submenuPullBasePx: Float? = null
    private var submenuExpansionAnimator: android.animation.ValueAnimator? = null
    private var screen = DashboardScreen.OVERVIEW
    private val dashboardViewModel by viewModels<DashboardViewModel>()

    private val settingsViewModel by viewModels<MobileSettingsViewModel>()

    private val sourceSettingsViewModel by viewModels<MobileSourceSettingsViewModel>()

    private var settingsSwipeStartX = 0f
    private var settingsSwipeStartY = 0f
    private var settingsSwipeTracking = false
    private var deferCloudEndpointSyncForPermissionCheck = false
    private val healthPermissionsLauncher =
        registerForActivityResult(HealthConnectIntegration.permissionContract) { granted -> settingsViewModel.healthPermissionsResult(granted) }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            PersistentBridgeService.refresh(this)
            if (::factory.isInitialized) refresh(forceSettingsRender = screen == DashboardScreen.SETTINGS)
        }
    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                deferCloudEndpointSyncForPermissionCheck = false
                scope.launch(Dispatchers.IO) {
                    CloudEndpointTreatmentSync.sync(applicationContext)
                }
            } else {
                Toast
                    .makeText(
                        this,
                        "Lokaler Nightscout-Zugriff ist ohne Netzwerkfreigabe nicht m\u00f6glich",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    private val settingsExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(settingsViewModel::exportSettings)
        }
    private val settingsImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(settingsViewModel::importSettings)
        }

    private val uiListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            runOnUiThread {
                SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
                MobileTrendArrowAppearance.apply(uiPreferences)
                if (screen == DashboardScreen.SETTINGS && (isInteractiveAppearancePreference(key) || factory.settingsCategory == "general" && key?.startsWith("source.") == true)) {
                    // Keep the active color picker/slider and expanded section alive. Rebuilding the
                    // complete Settings hierarchy here used to close the configurator on every drag.
                    applyRuntimeColors()
                } else if (uiPreferenceRequiresDashboardRefresh(key)) {
                    refresh(forceSettingsRender = true)
                }
            }
            scope.launch(Dispatchers.IO) {
                runCatching { publishWatchConfig(applicationContext) }
                if (uiPreferenceRequiresWidgetUpdate(key)) {
                    runCatching { SugarliciousWidgets.update(applicationContext) }
                }
            }
        }

    internal fun uiPreferenceRequiresDashboardRefresh(key: String?): Boolean = key != "watchFaceIndex"

    internal fun isInteractiveAppearancePreference(key: String?): Boolean =
        key != null &&
            (
                key.startsWith("color.") ||
                    key.startsWith("notification.color.") ||
                    key.startsWith("widget.color.") ||
                    key.startsWith("cgm.dot") ||
                    key.startsWith("notification.cgm.dot")
            )

    internal fun uiPreferenceRequiresWidgetUpdate(key: String?): Boolean =
        key == "themeMode" ||
            key?.startsWith("color.") == true ||
            key?.startsWith("widget.color.") == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SugarliciousWatchFaceSelectionStore.migrate(this)
        uiPreferences.ensureSettingsSchema(SettingsSchemaVersions.APPEARANCE)
        // The target band is canonical graph content, not a user-toggleable stream.
        uiPreferences.edit { remove("cgm.targetRange") }
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        MobileTrendArrowAppearance.apply(uiPreferences)
        setContentView(R.layout.activity_main)
        app.sugarlicious.uishared.SystemTypography
            .applyDeclaredWeights(findViewById(android.R.id.content))
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (screen == DashboardScreen.SETTINGS && factory.backWithinSettings()) {
                        scroll.scrollTo(0, 0)
                        refresh(forceSettingsRender = true)
                        return
                    }
                    if (screen != DashboardScreen.OVERVIEW) {
                        navigate(DashboardScreen.OVERVIEW)
                    }
                }
            },
        )
        requestMigratedLocalNetworkAccessIfNeeded()
        if (!uiPreferences.getBoolean("targetStepLineDefaultMigratedV3", false)) {
            // Earlier releases persisted the target-value layer as off, which made a correctly
            // rendered Step-Line invisible after an in-place update. Enable the new AAPS-style
            // layer once; subsequent user changes are preserved.
            uiPreferences.edit {
                putBoolean("cgm.targetValue", true)
                putBoolean("targetStepLineDefaultMigratedV3", true)
            }
        }
        if (!uiPreferences.getBoolean("insulinActivityVisibleMigratedV4", false)) {
            // Earlier defaults persisted this stream as disabled, so the renderer and valid AAPS
            // activity samples could both be present while no activity pixels were ever drawn.
            uiPreferences.edit {
                putBoolean("cgm.activity", true)
                putBoolean("insulinActivityVisibleMigratedV4", true)
            }
        }
        content = findViewById(R.id.dashboard_content)
        scroll = findViewById(R.id.dashboard_scroll)
        screen =
            savedInstanceState?.getString("screen")?.let { runCatching { DashboardScreen.valueOf(it) }.getOrNull() }
                ?: DashboardScreen.OVERVIEW
        styleTitle()
        factory =
            DashboardViewFactory(
                this,
                DashboardCallbacks(
                    navigate = ::navigate,
                    setUnit = { settingsViewModel.setString("unit", it.name) },
                    setDataSource = { settingsViewModel.setString("dataSource", it.name) },
                    openCloudEndpointTreatments = { startActivity(Intent(this, CloudEndpointTreatmentSettingsActivity::class.java)) },
                    openDiagnostics = { startActivity(Intent(this, DiagnosticActivity::class.java)) },
                    setThemeMode = { settingsViewModel.setString("themeMode", it.name) },
                    setGlucoseTileDetailMode = { settingsViewModel.setString(DashboardUiPreferences.GLUCOSE_TILE_DETAIL_MODE_KEY, it.name) },
                    setShowCgmGraph = { settingsViewModel.setBoolean("showCgmGraph", it) },
                    setGraphHours = settingsViewModel::setGraphHours,
                    setCgmStream = settingsViewModel::setBoolean,
                    setShowMetabolicGraph = { settingsViewModel.setBoolean("showMetabolicGraph", it) },
                    setCompact = { settingsViewModel.setBoolean("compact", it) },
                    setLiveNotification = ::setLiveNotification,
                    setNotificationGraphEnabled = { settingsViewModel.setBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_ENABLED, it) },
                    setNotificationGraphHours = settingsViewModel::setNotificationGraphHours,
                    setWatchFaceIndex = {
                        settingsViewModel.setWatchFaceIndex(it)
                        updateTopBar()
                    },
                    syncNow = ::syncNow,
                    connectHealthConnect = ::connectHealthConnect,
                    syncHealthConnect = ::syncHealthConnect,
                    manageHealthConnect = ::manageHealthConnect,
                    requestNotificationAccess = ::requestNotificationAccess,
                    requestUnrestrictedBattery = ::requestUnrestrictedBattery,
                    exportSettings = ::exportSettings,
                    importSettings = ::importSettings,
                    openProjectGitHub = ::openProjectGitHub,
                    openContactEmail = ::openContactEmail,
                ),
                dashboardState = dashboardViewModel.uiState,
                settingsViewModel = settingsViewModel,
                sourceSettingsViewModel = sourceSettingsViewModel,
                onSettingsPageChanged = {
                    scroll.scrollTo(0, 0)
                    refresh(forceSettingsRender = true)
                },
            )
        savedInstanceState?.getString("settingsCategory")?.let { category ->
            if (factory.settingsCategory == null) {
                factory.restoreSettingsPage(category, savedInstanceState.getString("settingsTitle", "Einstellungen"))
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsViewModel.uiState.collectLatest { state ->
                    state.message?.let { message ->
                        settingsViewModel.clearMessage()
                        refresh(forceSettingsRender = true)
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.uiState.collectLatest {
                    refresh()
                }
            }
        }
        bindTopNavigation()
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            if (screen == DashboardScreen.SETTINGS || screen == DashboardScreen.WATCH) {
                updateSettingsScrollHeader(y)
            }
        }
        PersistentBridgeService.start(this)
        scope.launch(Dispatchers.IO) {
            applicationContext.recordMobileDiagnostic("APP", "APP-START-100", "Mobile overview started")
        }
        requestNotificationPermissionIfNeeded()
        refresh(forceSettingsRender = true)
    }

    private fun requestMigratedLocalNetworkAccessIfNeeded() {
        val migrationKey = "android17LocalNetworkPermissionPromptedV1"
        val configuration = CloudEndpointConfigurationStore.read(this)
        val granted =
            checkSelfPermission(LocalNetworkAccessPolicy.PERMISSION) == PackageManager.PERMISSION_GRANTED
        deferCloudEndpointSyncForPermissionCheck =
            LocalNetworkAccessPolicy.shouldDeferSyncWhileChecking(
                sdkInt = Build.VERSION.SDK_INT,
                enabled = configuration.enabled,
                permissionGranted = granted,
            )
        if (!deferCloudEndpointSyncForPermissionCheck) return
        scope.launch {
            val needsPermission =
                withContext(Dispatchers.IO) {
                    LocalNetworkAccessPolicy.needsPermissionAfterResolution(
                        sdkInt = Build.VERSION.SDK_INT,
                        enabled = configuration.enabled,
                        baseUrl = configuration.baseUrl,
                        permissionGranted = granted,
                    )
                }
            if (needsPermission) {
                if (!uiPreferences.getBoolean(migrationKey, false)) {
                    uiPreferences.edit { putBoolean(migrationKey, true) }
                    localNetworkPermissionLauncher.launch(LocalNetworkAccessPolicy.PERMISSION)
                }
            } else {
                deferCloudEndpointSyncForPermissionCheck = false
                scope.launch(Dispatchers.IO) { runCatching { CloudEndpointTreatmentSync.syncIfDue(applicationContext) } }
            }
        }
    }

    private fun connectHealthConnect() {
        when (HealthConnectIntegration.availability(this)) {
            HealthConnectClient.SDK_AVAILABLE -> healthPermissionsLauncher.launch(HealthConnectIntegration.permissions)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                openExternal(
                    Intent(Intent.ACTION_VIEW, "market://details?id=${HealthConnectIntegration.PROVIDER_PACKAGE}".toUri()),
                )
            else -> Toast.makeText(this, "Health Connect ist auf diesem Ger\u00e4t nicht verf\u00fcgbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncHealthConnect() = settingsViewModel.syncHealthConnect()

    private fun manageHealthConnect() {
        if (HealthConnectIntegration.availability(this) == HealthConnectClient.SDK_AVAILABLE) {
            openExternal(HealthConnectClient.getHealthConnectManageDataIntent(this, HealthConnectIntegration.PROVIDER_PACKAGE))
        } else {
            connectHealthConnect()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        var swipeTarget: DashboardScreen? = null

        if (screen == DashboardScreen.SETTINGS) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // A settings slider drag is not a Back gesture. Reserve only the edge.
                    val safeStartArea = 24.dp.toFloat()
                    settingsSwipeTracking = event.pointerCount == 1 && event.rawX <= safeStartArea
                    settingsSwipeStartX = event.rawX
                    settingsSwipeStartY = event.rawY
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    settingsSwipeTracking = false
                }

                MotionEvent.ACTION_UP -> {
                    if (settingsSwipeTracking) {
                        swipeTarget =
                            menuSwipeTarget(
                                screen = screen,
                                deltaX = event.rawX - settingsSwipeStartX,
                                deltaY = event.rawY - settingsSwipeStartY,
                                minimumDistancePx = 72.dp.toFloat(),
                            )
                    }
                    settingsSwipeTracking = false
                }

                MotionEvent.ACTION_CANCEL -> {
                    settingsSwipeTracking = false
                }
            }
        } else {
            settingsSwipeTracking = false
        }

        val handled = super.dispatchTouchEvent(event)
        swipeTarget?.let { onBackPressedDispatcher.onBackPressed() }
        return handled
    }

    override fun onStart() {
        super.onStart()
        uiPreferences.registerOnSharedPreferenceChangeListener(uiListener)
        scope.launch(Dispatchers.IO) { runCatching { requestWatchRuntimeStatus(applicationContext) } }
        if (!deferCloudEndpointSyncForPermissionCheck) {
            scope.launch(Dispatchers.IO) { runCatching { CloudEndpointTreatmentSync.syncIfDue(applicationContext) } }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::factory.isInitialized) refresh(forceSettingsRender = screen == DashboardScreen.SETTINGS)
        if (resumeLiveNotificationSetup) {
            resumeLiveNotificationSetup = false
            if (canScheduleExactFreshnessAlarm()) {
                activateLiveNotification()
            } else {
                settingsViewModel.setBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
                PersistentBridgeService.refresh(this)
            }
        }
        requestPromotionAccessIfNeeded()
    }

    override fun onStop() {
        if (::factory.isInitialized) factory.clearDataSourceExpansion()
        uiPreferences.unregisterOnSharedPreferenceChangeListener(uiListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen.name)
        outState.putString("settingsCategory", factory.settingsCategory)
        outState.putString("settingsTitle", factory.settingsTitle)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        scope.cancel()
        submenuExpansionAnimator?.cancel()
        super.onDestroy()
    }

    private fun refresh(forceSettingsRender: Boolean = false) {
        if (!::content.isInitialized || !::factory.isInitialized) return
        SugarliciousColors.apply(SugarliciousColorStore.load(uiPreferences))
        MobileTrendArrowAppearance.apply(uiPreferences)
        applyRuntimeColors()
        val uiState = DashboardUiPreferences.read(uiPreferences)
        if (screen != DashboardScreen.SETTINGS || forceSettingsRender) {
            factory.render(content, screen, dashboardViewModel.uiState.value.therapy, uiState)
        }
        renderFixedWatchHeader()
        updateTopBar()
    }

    @Suppress("DEPRECATION")
    private fun applyRuntimeColors() {
        val backgroundColor =
            if (screen == DashboardScreen.SETTINGS || screen == DashboardScreen.WATCH) {
                MobileSettingsStyle.page(SugarliciousColors.palette.isLight)
            } else {
                SugarliciousColors.argb(SugarliciousColorRole.BACKGROUND)
            }
        val text =
            SugarliciousColors.argb(
                SugarliciousColorRole.TEXT_PRIMARY,
            )

        findViewById<View>(R.id.root)
            .setBackgroundColor(backgroundColor)
        findViewById<View>(R.id.scroll_fade).background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, backgroundColor),
            )
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        val light = SugarliciousColors.palette.isLight
        if (Build.VERSION.SDK_INT >= 30) {
            val mask =
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            val flags =
                (if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0) or
                    (if (light) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }

        findViewById<TextView>(R.id.app_title)
            .setTextColor(text)

        findViewById<ImageView>(R.id.brand_logo).imageTintList = ColorStateList.valueOf(SugarliciousColors.argb(SugarliciousColorRole.BRAND_GREEN))
        findViewById<ImageView>(R.id.top_back).imageTintList = ColorStateList.valueOf(text)
        findViewById<ImageView>(R.id.top_settings).imageTintList = ColorStateList.valueOf(text)
        updateTopBar()
    }

    private fun bindTopNavigation() {
        findViewById<View>(R.id.top_settings).setOnClickListener { navigate(DashboardScreen.SETTINGS) }
        findViewById<View>(R.id.top_back).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.watch_open).setOnClickListener {
            val launch = listOf("com.samsung.android.app.watchmanager", "com.google.android.apps.wear.companion").firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            if (launch != null) {
                runCatching { startActivity(launch) }
            } else {
                android.widget.Toast
                    .makeText(this, "Bitte den Watchface-Editor auf der Uhr \u00f6ffnen", android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
        findViewById<View>(R.id.watch_send).setOnClickListener {
            scope.launch {
                val face = SugarliciousWatchFaceSelectionStore.read(applicationContext, 0)
                val slots = WatchFacePresetStore.readSlots(applicationContext, face)
                val ids = slots.values.distinct().ifEmpty { WatchFacePresetStore.read(applicationContext, face) }
                runCatching { syncComplicationPreset(applicationContext, ids) }
                    .onSuccess {
                        android.widget.Toast
                            .makeText(this@MainActivity, "An Watch gesendet \u00b7 Provider-Zuweisung in der Watch App", android.widget.Toast.LENGTH_LONG)
                            .show()
                    }.onFailure {
                        android.widget.Toast
                            .makeText(this@MainActivity, "Watch nicht erreichbar", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
            }
        }
    }

    private fun navigate(target: DashboardScreen) {
        if (screen == target) return
        screen = target
        scroll.scrollTo(0, 0)
        refresh(forceSettingsRender = true)
        scope.launch(Dispatchers.IO) {
            applicationContext.recordMobileDiagnostic(
                "NAVIGATION",
                "APP-NAV-100",
                "Dashboard screen opened",
                metadata = mapOf("screen" to target.name),
            )
        }
    }

    private fun updateTopBar() {
        listOf(R.id.watch_open, R.id.watch_send).forEach { id ->
            findViewById<android.widget.ImageButton>(id).apply {
                val vigil = SugarliciousWatchFaceSelectionStore.read(this@MainActivity, uiPreferences.getInt("watchFaceIndex", 0)) == DIRECT_TO_WATCH_FACE_INDEX
                visibility = if (screen == DashboardScreen.WATCH && !(id == R.id.watch_send && vigil)) View.VISIBLE else View.GONE
                imageTintList =
                    ColorStateList.valueOf(
                        if (SugarliciousColors.palette.isLight) 0xFF6D6D72.toInt() else 0xFFB5B5BA.toInt(),
                    )
            }
        }
        val bar = findViewById<View>(R.id.top_app_bar)
        bar.translationY = 0f
        if (screen == DashboardScreen.WATCH) {
            bar.layoutParams =
                bar.layoutParams.apply {
                    height =
                        maxOf(
                            56.dp,
                            android.util.TypedValue
                                .applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 28f, resources.displayMetrics)
                                .toInt(),
                        )
                }
        }
        val back = findViewById<View>(R.id.top_back)
        val brand = findViewById<View>(R.id.brand_logo)
        val title = findViewById<TextView>(R.id.app_title)
        if (screen == DashboardScreen.SETTINGS && !factory.settingsIsRoot) {
            bar.layoutParams = bar.layoutParams.apply { height = submenuCompactHeightPx() }
            bar.minimumHeight = 56.dp
            title.maxLines = 2
        } else if (screen == DashboardScreen.SETTINGS) {
            bar.layoutParams = bar.layoutParams.apply { height = 56.dp }
        }
        val settings = findViewById<View>(R.id.top_settings)
        (bar.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            bottomMargin = if (screen == DashboardScreen.SETTINGS) -height else 0
            bar.layoutParams = this
        }
        bar.translationZ = if (screen != DashboardScreen.OVERVIEW) 8.dp.toFloat() else 0f
        when (screen) {
            DashboardScreen.OVERVIEW -> {
                bar.visibility = View.GONE
            }
            DashboardScreen.SETTINGS, DashboardScreen.WATCH -> {
                bar.visibility = View.VISIBLE
                back.visibility = if (screen == DashboardScreen.SETTINGS && factory.settingsIsRoot) View.GONE else View.VISIBLE
                brand.visibility = View.GONE
                settings.visibility = View.GONE
                title.text =
                    if (screen == DashboardScreen.WATCH) {
                        "Watch"
                    } else if (!factory.settingsIsRoot) {
                        factory.settingsTitle
                    } else {
                        ""
                    }
                title.text = brandedSettingsTitle(title.text.toString())
                title.textSize = 20f
                title.typeface = android.graphics.Typeface.DEFAULT
                title.setTextColor(MobileSettingsStyle.text(SugarliciousColors.palette.isLight))
                findViewById<ImageView>(R.id.top_back).apply {
                    layoutParams =
                        layoutParams.apply {
                            width = 48.dp
                            height = 48.dp
                            (this as LinearLayout.LayoutParams).also {
                                it.gravity = android.view.Gravity.BOTTOM
                                it.bottomMargin = 4.dp
                            }
                        }
                    setPadding(13.dp, 13.dp, 13.dp, 13.dp)
                    setImageResource(R.drawable.ic_oneui_back)
                    imageTintList = ColorStateList.valueOf(MobileSettingsStyle.text(SugarliciousColors.palette.isLight))
                }
            }
        }

        scroll.isUserScrollEnabled = screen == DashboardScreen.SETTINGS
        scroll.sectionSnapEnabled = screen == DashboardScreen.SETTINGS && factory.settingsIsRoot
        findViewById<View>(R.id.scroll_fade).visibility =
            if (screen == DashboardScreen.OVERVIEW) View.GONE else View.VISIBLE
        content.setPadding(
            content.paddingLeft,
            if (screen == DashboardScreen.SETTINGS && !factory.settingsIsRoot) {
                submenuCompactHeightPx() + SETTINGS_SUBMENU_CARD_GAP_DP.toInt().dp
            } else if (screen != DashboardScreen.OVERVIEW) {
                0
            } else {
                2.dp
            },
            content.paddingRight,
            when (screen) {
                DashboardScreen.SETTINGS -> 24.dp
                DashboardScreen.WATCH -> 0
                DashboardScreen.OVERVIEW -> 0
            },
        )
        configureSubmenuExpansion()
        if (screen != DashboardScreen.OVERVIEW) content.post { updateSettingsScrollHeader(scroll.scrollY) }
    }

    private fun submenuCompactHeightPx(): Int {
        val paint = android.text.TextPaint().apply { textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics) }
        val text = factory.settingsTitle
        val layout =
            android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (resources.displayMetrics.widthPixels - 88.dp).coerceAtLeast(1))
                .setMaxLines(3)
                .build()
        return maxOf(56.dp, layout.height + 16.dp)
    }

    private val settingsContent: LinearLayout get() = factory.settingsBody ?: content

    private fun submenuExpansionView(): android.widget.FrameLayout? = settingsContent.findViewWithTag("settings_submenu_expansion")

    private fun submenuExpandedTitleView(): TextView? = submenuExpansionView()?.getChildAt(0) as? TextView

    private fun submenuMaximumExpansionPx(): Float = maxOf(220.dp.toFloat(), submenuExpandedTitleHeightPx() + submenuCompactHeightPx() + 32.dp.toFloat())

    private fun submenuExpandedTitleHeightPx(): Int {
        val heading = submenuExpandedTitleView() ?: return 0
        heading.layout?.takeIf { heading.width > 0 }?.let { return it.height }
        val text = heading.text
        val width = (content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - content.paddingLeft - content.paddingRight - 32.dp
        val layout =
            android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, heading.paint, width.coerceAtLeast(1))
                .build()
        return layout.height
    }

    private fun configureSubmenuExpansion() {
        val enabled = screen == DashboardScreen.SETTINGS && !factory.settingsIsRoot
        content.clipChildren = !enabled
        content.clipToPadding = !enabled
        scroll.topPullEnabled = enabled
        scroll.topPullExpanded = enabled && submenuExpansionPx > 0f
        scroll.topPullExpansionPx = submenuExpansionPx
        if (!enabled) {
            submenuExpansionAnimator?.cancel()
            submenuExpansionPx = 0f
            submenuExpansionTitle = null
            submenuPullBasePx = null
            scroll.onTopPull = null
            scroll.onTopPullFinished = null
            return
        }
        if (submenuExpansionTitle != factory.settingsTitle) {
            submenuExpansionAnimator?.cancel()
            submenuExpansionTitle = factory.settingsTitle
            submenuExpansionPx = 0f
            submenuPullBasePx = null
        }
        scroll.topPullExpanded = submenuExpansionPx > 0f
        if (submenuExpansionView() == null) {
            settingsContent.addView(
                android.widget.FrameLayout(this).apply {
                    tag = "settings_submenu_expansion"
                    clipChildren = false
                    clipToPadding = false
                    addView(
                        TextView(this@MainActivity).apply {
                            alpha = 0f
                            text = brandedSettingsTitle(factory.settingsTitle)
                            textSize = 34f
                            typeface = android.graphics.Typeface.DEFAULT
                            gravity = android.view.Gravity.CENTER
                            setTextColor(MobileSettingsStyle.text(SugarliciousColors.palette.isLight))
                            setPadding(16.dp, 0, 16.dp, 0)
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                },
                0,
                LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, submenuExpansionPx.toInt()),
            )
        }
        submenuExpandedTitleView()?.let { title -> title.layoutParams = title.layoutParams.apply { height = submenuExpandedTitleHeightPx() } }
        scroll.onTopPull = { distance ->
            if (submenuPullBasePx == null) {
                submenuExpansionAnimator?.cancel()
                submenuPullBasePx = submenuExpansionPx
            }
            setSubmenuExpansion((requireNotNull(submenuPullBasePx) + distance).coerceIn(0f, submenuMaximumExpansionPx()))
        }
        scroll.onTopPullFinished = {
            submenuPullBasePx = null
            val maximum = submenuMaximumExpansionPx()
            val density = resources.displayMetrics.density
            val target = settingsSubmenuExpansionTarget(submenuExpansionPx / density, maximum / density) * density
            submenuExpansionAnimator =
                android.animation.ValueAnimator.ofFloat(submenuExpansionPx, target).apply {
                    duration = 320L
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    addUpdateListener { setSubmenuExpansion(it.animatedValue as Float) }
                    start()
                }
        }
    }

    private fun setSubmenuExpansion(heightPx: Float) {
        submenuExpansionPx = heightPx
        scroll.topPullExpanded = heightPx > 0f
        scroll.topPullExpansionPx = heightPx
        submenuExpansionView()?.let { heading ->
            if (heading.layoutParams.height != heightPx.toInt()) {
                heading.layoutParams = heading.layoutParams.apply { height = heightPx.toInt() }
            }
        }
        updateSettingsScrollHeader(scroll.scrollY)
    }

    private fun updateSettingsScrollHeader(scrollY: Int) {
        if (screen == DashboardScreen.OVERVIEW) return
        if (screen == DashboardScreen.SETTINGS && !factory.settingsIsRoot) {
            val density = resources.displayMetrics.density
            val spacer = submenuExpansionView()
            val heading = submenuExpandedTitleView()
            val firstCard = (0 until settingsContent.childCount).map { settingsContent.getChildAt(it) }.firstOrNull { it !== spacer && it.isVisible }
            val back = findViewById<View>(R.id.top_back)
            val bar = findViewById<View>(R.id.top_app_bar)
            val state =
                settingsSubmenuHeaderState(
                    scrollY / density,
                    submenuExpansionPx / density,
                    submenuMaximumExpansionPx() / density,
                    ((firstCard?.top ?: 0) + content.paddingTop) / density,
                    (back.bottom.takeIf { it > 0 } ?: (bar.height / 2 + 24.dp)) / density,
                    submenuExpandedTitleHeightPx() / density,
                )
            findViewById<TextView>(R.id.app_title).apply {
                alpha = state.compactTitleAlpha
                translationY = state.compactTitleOffsetDp * density
                scaleX = 1f
                scaleY = 1f
            }
            heading?.apply {
                alpha = state.expandedTitleAlpha
                translationY = state.expandedTitleTopDp * density + scrollY - content.paddingTop
            }
            bar.translationY = state.rowOffsetDp * density
            settingsBackCircle.setColor(MobileSettingsStyle.card(SugarliciousColors.palette.isLight))
            settingsBackCircle.alpha = (state.backCircleAlpha * 255).toInt()
            back.background = settingsBackCircle
            bar.setBackgroundColor(Color.TRANSPARENT)
            return
        }
        findViewById<TextView>(R.id.app_title).apply {
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
        val density = resources.displayMetrics.density
        val heading = content.findViewWithTag<View>("settings_scroll_title") ?: return
        val titleView = heading as? TextView
        val compactTextPaint =
            android.text.TextPaint(titleView?.paint).apply {
                textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
            }
        val compactWidth = settingsCompactTitleLayout(heading.width / density, 0f).widthDp
        val compactText = titleView?.text ?: ""
        val compactTextHeight =
            android.text.StaticLayout.Builder
                .obtain(compactText, 0, compactText.length, compactTextPaint, (compactWidth * density).toInt().coerceAtLeast(1))
                .setMaxLines(2)
                .build()
                .height / density
        val compact = settingsCompactTitleLayout(heading.width / density, compactTextHeight)
        val bar = findViewById<View>(R.id.top_app_bar)
        (bar.layoutParams as android.widget.LinearLayout.LayoutParams).also {
            val heightPx = (compact.heightDp * density).toInt()
            if (it.height != heightPx || it.bottomMargin != -heightPx) {
                it.height = heightPx
                it.bottomMargin = -heightPx
                bar.layoutParams = it
            }
        }
        val firstCard = (0 until settingsContent.childCount).map { settingsContent.getChildAt(it) }.firstOrNull { it !== heading && it.isVisible }
        val state = settingsScrollHeaderState(scrollY / density, heading.height / density, ((firstCard?.top ?: heading.bottom) + content.paddingTop) / density, compact.heightDp)
        heading.alpha = state.titleAlpha
        heading.translationY = state.titleOffsetDp * density
        (heading as? TextView)?.apply {
            // Keep the scroll child's measured geometry unchanged during a gesture.
            // Text size/padding mutations request layout and let ScrollView re-anchor focus.
            pivotX = 0f
            pivotY = height / 2f
            scaleX = state.titleScale
            scaleY = state.titleScale
            val textWidth = layout?.let { lines -> (0 until lines.lineCount).maxOfOrNull { lines.getLineWidth(it) } } ?: paint.measureText(text.toString())
            val originalLeft = ((width - textWidth) / 2f).coerceAtLeast(0f)
            val centeredLeft = (width - textWidth * state.titleScale) / 2f
            val targetLeft = centeredLeft * (1f - state.collapseFraction) + 52.dp * state.collapseFraction
            translationX = targetLeft - originalLeft * state.titleScale
        }
        settingsBackCircle.setColor(SugarliciousColors.argb(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.SURFACE))
        settingsBackCircle.alpha = (state.backCircleAlpha * 255).toInt()
        findViewById<View>(R.id.top_back).background = settingsBackCircle
        val shadeColor = SugarliciousColors.argb(app.sugarlicious.mobile.ui.theme.SugarliciousColorRole.BACKGROUND)
        if (settingsShadeColor != shadeColor) {
            settingsTopShade.colors = intArrayOf(shadeColor, android.graphics.Color.TRANSPARENT)
            settingsShadeColor = shadeColor
        }
        settingsTopShade.alpha = 0 // The title scrolls out naturally; only the back button gets an opaque surface.
        findViewById<View>(R.id.top_app_bar).background = settingsTopShade
    }

    private fun renderFixedWatchHeader() {
        findViewById<View>(R.id.watch_fixed_header).visibility = View.GONE
    }

    private fun styleTitle() {
        val value = SpannableString(getString(R.string.app_name))
        value.setSpan(
            ForegroundColorSpan(SugarliciousColors.argb(SugarliciousColorRole.PRIMARY)),
            5,
            value.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        findViewById<TextView>(R.id.app_title).text = value
    }

    private fun syncNow() {
        settingsViewModel.syncNow(
            dashboardViewModel.uiState.value
                .takeIf { it.observationError == null }
                ?.therapy,
        )
    }

    private fun setLiveNotification(enabled: Boolean) {
        if (!enabled) {
            settingsViewModel.setBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
            PersistentBridgeService.refresh(this)
            return
        }
        if (!canScheduleExactFreshnessAlarm()) {
            settingsViewModel.setBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)
            PersistentBridgeService.refresh(this)
            resumeLiveNotificationSetup = true
            openExternal(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData("package:$packageName".toUri()),
            )
            return
        }
        activateLiveNotification()
    }

    private fun canScheduleExactFreshnessAlarm(): Boolean =
        Build.VERSION.SDK_INT < 31 || getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()

    private fun activateLiveNotification() {
        settingsViewModel.setBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
        PersistentBridgeService.refresh(this)
        val deviceVendor = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (Build.VERSION.SDK_INT < 36 && !deviceVendor) {
            Toast.makeText(this, "Live-Status ben\u00f6tigt Android 16; normale Benachrichtigung bleibt aktiv", Toast.LENGTH_LONG).show()
            return
        }
        requestPromotionAccessIfNeeded()
    }

    private fun requestPromotionAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < 36 || promotionSettingsRequestedThisSession) return
        if (!uiPreferences.getBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, false)) return
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.canPostPromotedNotifications()) {
            promotionSettingsRequestedThisSession = true
            openExternal(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        openExternal(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    // Continuous glucose delivery and safety alerts are the app's core function;
    // Doze can otherwise suspend the local collector/data path.
    @SuppressLint("BatteryLife")
    private fun requestUnrestrictedBattery() {
        if (AppRuntimeAccess.isIgnoringBatteryOptimizations(this)) {
            Toast.makeText(this, "Dauerbetrieb ist bereits uneingeschr\u00e4nkt", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:$packageName".toUri()),
            )
        }.onFailure {
            openExternal(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun exportSettings() {
        settingsExportLauncher.launch("sugarlicious-settings-${LocalDate.now()}.json")
    }

    private fun importSettings() {
        settingsImportLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
    }

    private fun openContactEmail() {
        val intent =
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_email), null))
                .putExtra(Intent.EXTRA_SUBJECT, "Sugarlicious")
        openExternal(intent)
    }

    private fun openProjectGitHub() {
        openExternal(
            Intent(
                Intent.ACTION_VIEW,
                "https://github.com/FreDiabetics/aaps_wearable-suite".toUri(),
            ),
        )
    }

    private fun openExternal(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Keine passende App installiert", Toast.LENGTH_SHORT).show()
        }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
