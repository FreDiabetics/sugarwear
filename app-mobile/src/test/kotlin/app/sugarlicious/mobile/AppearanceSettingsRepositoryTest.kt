package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.mobile.ui.theme.SugarliciousPalette
import app.sugarlicious.model.AppearanceMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppearanceSettingsRepositoryTest {
    @Test fun `adapter preserves existing keys profiles and exact platform fanout`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val effects = FakeEffects()
            val repository = AndroidAppearanceSettingsRepository(context, effects, StandardTestDispatcher(testScheduler))
            repository.execute(AppearanceMode.DARK, AppearanceWrite.FloatValue(AppearanceFloat.CGM_RADIUS, 4.2f))
            repository.execute(AppearanceMode.LIGHT, AppearanceWrite.FloatValue(AppearanceFloat.CGM_RADIUS, 2.8f))
            repository.execute(AppearanceMode.DARK, AppearanceWrite.Color(AppearanceColorTarget.WIDGET, null, WidgetColorRole.GRAPH_BACKGROUND, 0xff123456.toInt()))
            val layout = NotificationLayoutSettings(glucoseScalePercent = 135, trendScalePercent = 125, glucoseXPercent = -12)
            repository.execute(AppearanceMode.DARK, AppearanceWrite.Layout(NotificationGraphProfile.EXPANDED, layout))
            val dots = NotificationGraphDotStyle(3.5f, false, .5f, false, .5f, 1.25f)
            repository.execute(AppearanceMode.DARK, AppearanceWrite.Dots(NotificationGraphProfile.EXPANDED, dots))
            repository.execute(AppearanceMode.DARK, AppearanceWrite.Color(AppearanceColorTarget.NOTIFICATION, SugarliciousColorRole.CGM_DOT_LOW, null, 0xffabcdef.toInt()))
            repository.execute(AppearanceMode.DARK, AppearanceWrite.FloatValue(AppearanceFloat.NOTIFICATION_SCALE, 42f))
            val dark = repository.load(AppearanceMode.DARK)
            val light = repository.load(AppearanceMode.LIGHT)
            val widgetState = AndroidAppearanceSettingsRepository(context, effects, StandardTestDispatcher(testScheduler), AppearanceColorTarget.WIDGET).load(AppearanceMode.DARK)
            val notificationRepository = AndroidAppearanceSettingsRepository(context, effects, StandardTestDispatcher(testScheduler), AppearanceColorTarget.NOTIFICATION)
            val notifications = notificationRepository.load(AppearanceMode.DARK)
            val lightNotifications = notificationRepository.load(AppearanceMode.LIGHT)
            assertEquals(4.2f, dark.floats.getValue(AppearanceFloat.CGM_RADIUS))
            assertEquals(2.8f, light.floats.getValue(AppearanceFloat.CGM_RADIUS))
            assertEquals(0xff123456.toInt(), widgetState.widgetPalette.argb(WidgetColorRole.GRAPH_BACKGROUND))
            assertEquals(layout, notifications.notificationLayouts.getValue(NotificationGraphProfile.EXPANDED))
            assertEquals(dots, notifications.notificationDots.getValue(NotificationGraphProfile.EXPANDED))
            assertEquals(0xffabcdef.toInt(), notifications.notificationColors.getValue(SugarliciousColorRole.CGM_DOT_LOW))
            assertNotEquals(notifications.notificationColors.getValue(SugarliciousColorRole.CGM_DOT_LOW), lightNotifications.notificationColors.getValue(SugarliciousColorRole.CGM_DOT_LOW))
            assertEquals(1, effects.widgetUpdates)
            assertEquals(2, effects.notificationUpdates)
            val p = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            assertEquals(4.2f, p.getFloat(graphAppearanceKey(AppearanceMode.DARK, "dotRadiusDp"), -1f))
            assertEquals(layout, NotificationLayoutSettingsStore.read(p, NotificationGraphProfile.EXPANDED))
        }

    @Test fun `loading mobile appearance does not initialize unrelated widget or notification profiles`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val p = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            p.edit().clear().commit()
            AndroidAppearanceSettingsRepository(context, FakeEffects(), StandardTestDispatcher(testScheduler)).load(AppearanceMode.DARK)
            assertFalse(p.contains("widget.color.very_high_text.v2"))
            assertFalse(p.getBoolean(PersistentBridgeService.PREFERENCE_NOTIFICATION_DOT_PROFILES_MIGRATED, false))
        }

    private class FakeEffects : AppearanceSettingsEffects {
        var widgetUpdates = 0
        var notificationUpdates = 0

        override suspend fun refreshWidgets() {
            widgetUpdates++
        }

        override suspend fun refreshNotifications() {
            notificationUpdates++
        }

        override suspend fun applyPalette(palette: SugarliciousPalette) = Unit

        override suspend fun publishWatchColors() = Unit
    }
}
