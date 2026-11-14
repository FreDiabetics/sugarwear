package app.sugarlicious.mobile

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.CgmThresholds
import app.sugarlicious.model.GlucoseSample
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseTrendSizing
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.TargetSample
import app.sugarlicious.model.TargetState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import app.sugarlicious.storage.TherapyStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowBuild
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Suppress("DEPRECATION")
class PersistentBridgeServiceTest {
    @Before
    fun allowExactFreshnessAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    @Config(sdk = [35])
    fun `system palette changes refresh the existing notification`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking { TherapyStateStore(context).save(TherapyDisplayState(receivedAtEpochMs = System.currentTimeMillis())) }
        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        val main = shadowOf(android.os.Looper.getMainLooper())
        main.idle()
        try {
            val service = controller.get()
            val manager = service.getSystemService(NotificationManager::class.java)
            manager.cancel(PersistentBridgeService.NOTIFICATION_ID)
            assertNull(shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID))
            service.onConfigurationChanged(android.content.res.Configuration(service.resources.configuration))
            assertNotNull(shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID))
        } finally {
            controller.destroy()
            main.idle()
        }
    }

    @Test
    fun `repeated ordinary start suppresses identical notification but explicit refresh posts`() {
        assertFalse(shouldPostNotificationOnStart(true, null, "same", "same"))
        assertTrue(shouldPostNotificationOnStart(true, null, "new", "old"))
        assertTrue(shouldPostNotificationOnStart(true, PersistentBridgeService.ACTION_REFRESH, "same", "same"))
        assertFalse(shouldPostNotificationOnStart(true, PersistentBridgeService.ACTION_FRESHNESS_BOUNDARY, "same", "same"))
    }

    @Test
    @Config(sdk = [36])
    fun `screen wake reconciles freshness after elapsed sleep time`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()
        val measuredAt = System.currentTimeMillis()
        runBlocking {
            TherapyStateStore(context).save(
                TherapyDisplayState(
                    receivedAtEpochMs = measuredAt,
                    glucose = GlucoseState(118.0, GlucoseUnit.MG_DL, Trend.FLAT, measuredAt),
                ),
            )
        }
        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val manager = controller.get().getSystemService(NotificationManager::class.java)
        assertEquals("118 →", shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TITLE))

        val elapsedState =
            TherapyDisplayState(
                receivedAtEpochMs = measuredAt,
                glucose = GlucoseState(118.0, GlucoseUnit.MG_DL, Trend.FLAT, measuredAt - 20 * 60_000L),
            )
        controller
            .get()
            .javaClass
            .getDeclaredField("latestState")
            .apply { isAccessible = true }
            .set(
                controller.get(),
                elapsedState,
            )
        val wakeReceiver =
            controller
                .get()
                .javaClass
                .getDeclaredField("freshnessReconcileReceiver")
                .apply { isAccessible = true }
                .get(controller.get()) as android.content.BroadcastReceiver
        wakeReceiver.onReceive(context, Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val postedRevision =
            controller
                .get()
                .javaClass
                .getDeclaredField("notifiedPresentationRevision")
                .apply { isAccessible = true }
                .get(controller.get())
        assertEquals(notificationRevision(elapsedState, System.currentTimeMillis(), GlucoseUnit.MG_DL), postedRevision)
        controller.destroy()
    }

    @Test
    fun `notification glucose color follows configured range thresholds`() {
        val thresholds = CgmThresholds(veryHighMgDl = 250.0, highMgDl = 160.0, lowMgDl = 70.0, veryLowMgDl = 54.0)

        assertEquals(SugarliciousColorRole.GLUCOSE_VERY_LOW, notificationGlucoseColorRole(53.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_LOW, notificationGlucoseColorRole(62.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_LOW, notificationGlucoseColorRole(70.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_IN_RANGE, notificationGlucoseColorRole(71.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_IN_RANGE, notificationGlucoseColorRole(159.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_HIGH, notificationGlucoseColorRole(160.0, thresholds))
        assertEquals(SugarliciousColorRole.GLUCOSE_VERY_HIGH, notificationGlucoseColorRole(250.0, thresholds))
    }

    @Test
    fun `notification revision changes for visible therapy data at the same glucose time`() {
        val measuredAt = 1_000L
        val initial =
            TherapyDisplayState(
                receivedAtEpochMs = 2_000L,
                glucose = GlucoseState(valueMgDl = 120.0, displayUnit = GlucoseUnit.MG_DL, measuredAtEpochMs = measuredAt),
                insulin = app.sugarlicious.model.InsulinState(totalIob = 1.0),
            )

        val first = notificationRevision(initial, measuredAt, GlucoseUnit.MG_DL)
        assertEquals(first, notificationRevision(initial.copy(receivedAtEpochMs = 9_000L), measuredAt, GlucoseUnit.MG_DL))
        assertNotEquals(first, notificationRevision(initial.copy(insulin = app.sugarlicious.model.InsulinState(totalIob = 2.0)), measuredAt, GlucoseUnit.MG_DL))
    }

    @Test
    fun `external surfaces refresh on the next aligned minute boundary`() {
        assertEquals(60_000L, delayUntilNextExternalSurfaceMinute(120_000L))
        assertEquals(45_000L, delayUntilNextExternalSurfaceMinute(135_000L))
        assertEquals(1L, delayUntilNextExternalSurfaceMinute(179_999L))
    }

    @Test
    fun `notification value block keeps metadata below the value and flat arrow`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val layouts =
            listOf(
                Triple(R.layout.notification_sugarlicious_collapsed, 29f, 11f),
                Triple(R.layout.notification_sugarlicious_expanded, 36f, 13f),
            )

        layouts.forEach { (layoutId, valueSp, metaSp) ->
            val root = LayoutInflater.from(context).inflate(layoutId, null)
            val info = root.findViewById<ViewGroup>(R.id.notification_info_block)
            val primary = root.findViewById<ViewGroup>(R.id.notification_primary_row)
            val value = root.findViewById<TextView>(R.id.notification_value)
            val trend = root.findViewById<ImageView>(R.id.notification_trend)
            val meta = root.findViewById<TextView>(R.id.notification_meta)
            val density = context.resources.displayMetrics.density
            val valuePx =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    valueSp,
                    context.resources.displayMetrics,
                )
            val metaPx =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    metaSp,
                    context.resources.displayMetrics,
                )
            val digitBounds = Rect()
            value.text = "123"
            meta.text = "+99 mg/dL · 999m"
            trend.visibility = View.VISIBLE
            root.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (400f * density).toInt(),
                    View.MeasureSpec.EXACTLY,
                ),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            value.paint.getTextBounds("123", 0, 3, digitBounds)

            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, primary.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, meta.layoutParams.width)
            assertEquals(valuePx, value.textSize, 0.5f)
            assertEquals(metaPx, meta.textSize, 0.5f)
            assertTrue(info.measuredWidth >= primary.measuredWidth)
            assertEquals(info.measuredWidth, meta.measuredWidth)
            assertTrue(meta.paint.measureText(meta.text.toString()) <= meta.measuredWidth)
            assertTrue(trend.layoutParams.width >= trend.layoutParams.height * 2)
            assertTrue(
                abs(value.textSize * GlucoseTrendSizing.REFERENCE_ARROW_TO_GLUCOSE_HEIGHT - trend.layoutParams.height) <= 3f * density,
            )
        }
    }

    @Test
    fun `expanded notification restores centered glucose details above a full width graph`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val headerId = context.resources.getIdentifier("notification_header", "id", context.packageName)
        val headerBackgroundId = context.resources.getIdentifier("notification_header_background", "id", context.packageName)
        val headerContentId = context.resources.getIdentifier("notification_header_content", "id", context.packageName)
        val therapyId = context.resources.getIdentifier("notification_therapy", "id", context.packageName)

        assertEquals(0, headerId)
        assertEquals(0, headerBackgroundId)
        assertEquals(0, headerContentId)
        assertEquals(0, therapyId)
        val root = LayoutInflater.from(context).inflate(R.layout.notification_sugarlicious_expanded, null) as ViewGroup
        val info = root.findViewById<android.widget.LinearLayout>(R.id.notification_info_block)
        val meta = root.findViewById<TextView>(R.id.notification_meta)
        val graph = root.findViewById<ImageView>(R.id.notification_graph)

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, info.layoutParams.width)
        assertTrue(info.gravity and android.view.Gravity.CENTER_HORIZONTAL != 0)
        assertEquals(android.view.Gravity.CENTER, meta.gravity)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, graph.layoutParams.width)
        assertEquals(0, root.paddingLeft)
        assertEquals(0, root.paddingRight)
    }

    @Test
    @Config(sdk = [35])
    fun `normal notification is ongoing private and sticky by default`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking { TherapyStateStore(context).save(TherapyDisplayState(receivedAtEpochMs = 0L)) }
        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        val service = controller.get()
        val manager = service.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID)

        assertNotNull(notification)
        assertEquals("—", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_ICON))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertNotNull(notification.contentView)
        assertNotNull(notification.bigContentView)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(PersistentBridgeService.CHANNEL_ID).importance)
        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 2))
        controller.destroy()
    }

    @Test
    @Config(sdk = [36])
    fun `android live mode requests promotion and removes custom graph views`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()
        context
            .getSharedPreferences("diagnostics", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("sourceVersion", "4.0.0-dev")
            .putInt("reachableWatches", 1)
            .commit()
        val now = System.currentTimeMillis()
        val therapyState =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose =
                    GlucoseState(
                        valueMgDl = 123.0,
                        displayUnit = GlucoseUnit.MG_DL,
                        trend = Trend.FLAT,
                        measuredAtEpochMs = now,
                        deltaMgDl = 5.0,
                    ),
                glucoseHistory =
                    listOf(
                        GlucoseSample(115.0, now - 10 * 60_000L),
                        GlucoseSample(120.0, now - 5 * 60_000L),
                    ),
            )
        runBlocking { TherapyStateStore(context).save(therapyState) }

        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val manager = controller.get().getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(PersistentBridgeService.NOTIFICATION_ID)
        assertEquals("123 → +5", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("123 →", notification.shortCriticalText)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertNull(notification.contentView)
        assertNull(notification.bigContentView)
        assertEquals(1, notification.actions.size)
        controller.destroy()
    }

    @Test
    @Config(sdk = [35])
    fun `deviceVendor live mode combines proprietary capsule with fallback graph views`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ShadowBuild
            .setManufacturer("samsung")
        context
            .getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(PersistentBridgeService.PREFERENCE_LIVE_NOTIFICATION, true)
            .commit()
        val now = System.currentTimeMillis()
        runBlocking {
            TherapyStateStore(context).save(
                TherapyDisplayState(
                    receivedAtEpochMs = now,
                    glucose = GlucoseState(118.0, GlucoseUnit.MG_DL, Trend.FORTY_FIVE_DOWN, now, deltaMgDl = -6.0),
                    insulin = InsulinState(totalIob = 1.3),
                    carbs = CarbState(cobGrams = 22.0),
                ),
            )
        }

        val controller = Robolectric.buildService(PersistentBridgeService::class.java).create().startCommand(0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        var notification =
            shadowOf(controller.get().getSystemService(NotificationManager::class.java))
                .getNotification(PersistentBridgeService.NOTIFICATION_ID)

        assertEquals(1, notification.extras.getInt(DeviceVendorLiveNotificationAdapter.EXTRA_STYLE))
        assertEquals("118 ↘", notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_PRIMARY_INFO))
        assertEquals(
            DeviceVendorLiveNotificationAdapter.systemAccentColor(context),
            notification.extras.getInt(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_BACKGROUND_COLOR),
        )
        assertEquals(
            R.drawable.ic_notification_outlined,
            notification.smallIcon.resId,
        )
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_ICON))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals(R.layout.notification_sugarlicious_collapsed, notification.contentView.layoutId)
        assertEquals(R.layout.notification_sugarlicious_expanded, notification.bigContentView.layoutId)
        assertEquals(
            R.layout.notification_sugarlicious_expanded,
            notification.extras
                .getParcelable(DeviceVendorLiveNotificationAdapter.EXTRA_CUSTOM_REMOTE_VIEW, android.widget.RemoteViews::class.java)!!
                .layoutId,
        )
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_REMOTE_VIEW))
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_SECONDARY_INFO))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(
            "−6 mg/dL · 0m",
            notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_SECONDARY_INFO),
        )

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        notification =
            shadowOf(controller.get().getSystemService(NotificationManager::class.java))
                .getNotification(PersistentBridgeService.NOTIFICATION_ID)
        assertEquals(
            R.layout.notification_sugarlicious_expanded,
            notification.extras
                .getParcelable(DeviceVendorLiveNotificationAdapter.EXTRA_CUSTOM_REMOTE_VIEW, android.widget.RemoteViews::class.java)!!
                .layoutId,
        )
        assertEquals("118 ↘", notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_PRIMARY_INFO))
        controller.destroy()
    }

    @Test
    @Config(sdk = [35])
    fun `notification graph ignores target value and target history`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)
        preferences
            .edit()
            .clear()
            .putString("themeMode", "DARK")
            .commit()
        val now = System.currentTimeMillis()
        val base =
            TherapyDisplayState(
                receivedAtEpochMs = now,
                glucose =
                    GlucoseState(
                        valueMgDl = 123.0,
                        displayUnit = GlucoseUnit.MG_DL,
                        measuredAtEpochMs = now - 60_000L,
                    ),
                glucoseHistory =
                    listOf(
                        GlucoseSample(116.0, now - 10 * 60_000L),
                        GlucoseSample(120.0, now - 5 * 60_000L),
                    ),
                target = TargetState(lowMgDl = 80.0, highMgDl = 160.0, valueMgDl = 100.0),
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 100.0,
                            startedAtEpochMs = now - 60L * 60_000L,
                            endsAtEpochMs = now,
                        ),
                    ),
            )
        val changedTargetHistory =
            base.copy(
                target = base.target?.copy(valueMgDl = 130.0, temporary = true),
                targetHistory =
                    listOf(
                        TargetSample(
                            valueMgDl = 130.0,
                            startedAtEpochMs = now - 30L * 60_000L,
                            endsAtEpochMs = now + 30L * 60_000L,
                            temporary = true,
                        ),
                    ),
            )

        val collapsedBase = NotificationGraphRenderer.renderCollapsed(context, base, preferences)
        val collapsedChanged = NotificationGraphRenderer.renderCollapsed(context, changedTargetHistory, preferences)
        val expandedBase = NotificationGraphRenderer.renderExpanded(context, base, preferences)
        val expandedChanged = NotificationGraphRenderer.renderExpanded(context, changedTargetHistory, preferences)

        fun assertTargetIndependentColumnsEqual(
            baseGraph: android.graphics.Bitmap,
            changedGraph: android.graphics.Bitmap,
        ) {
            assertEquals(baseGraph.width, changedGraph.width)
            assertEquals(baseGraph.height, changedGraph.height)
            // All CGM points in this fixture are clustered in the final ~10 minutes of a 3 h
            // window. Sampling three earlier columns therefore tests target-band/target-history
            // rendering without making the assertion sensitive to millisecond-level x drift of
            // anti-aliased glucose dots between two independent render calls.
            listOf(baseGraph.width / 4, baseGraph.width / 2, baseGraph.width * 3 / 4).forEach { x ->
                for (y in 0 until baseGraph.height) {
                    assertEquals(
                        "target-independent graph differs at x=$x y=$y",
                        baseGraph.getPixel(x, y),
                        changedGraph.getPixel(x, y),
                    )
                }
            }
        }

        assertTargetIndependentColumnsEqual(collapsedBase, collapsedChanged)
        assertTargetIndependentColumnsEqual(expandedBase, expandedChanged)
    }

    @Test
    @Config(sdk = [35])
    fun `notification graph accepts only one two or three hours`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = context.getSharedPreferences("dashboard_ui", android.content.Context.MODE_PRIVATE)

        preferences
            .edit()
            .clear()
            .putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 1)
            .commit()
        assertEquals(1, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 2).commit()
        assertEquals(2, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 3).commit()
        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))

        preferences.edit().putInt(PersistentBridgeService.PREFERENCE_NOTIFICATION_GRAPH_HOURS, 6).commit()
        assertEquals(3, NotificationGraphRenderer.notificationGraphHours(preferences))
    }

    @Test
    @Config(sdk = [35])
    fun `boot receiver requests persistent service restart`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val shadowContext = shadowOf(context)
        shadowContext.clearStartedServices()

        PersistentBridgeBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val started = shadowContext.nextStartedService
        assertEquals(PersistentBridgeService::class.java.name, started.component?.className)
    }
}
