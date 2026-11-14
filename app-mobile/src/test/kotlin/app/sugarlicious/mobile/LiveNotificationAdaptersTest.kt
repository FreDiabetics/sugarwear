package app.sugarlicious.mobile

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Metric.FixedFloat
import androidx.core.app.NotificationCompat.Metric.FixedInt
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LiveNotificationAdaptersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [37])
    fun `glucose only sources retain valid live metric without therapy label`() {
        listOf(null, "", "  ").forEach { therapy ->
            val result = AndroidLiveNotificationAdapter.apply(builder(), presentation().copy(therapyText = therapy), 37)
            assertTrue(result.metric != null)
        }
    }

    @Test
    @Config(sdk = [35])
    fun `DeviceVendor owns icon rendering and uses the OS accent background`() {
        val builder = builder()
        DeviceVendorLiveNotificationAdapter.apply(context, builder, presentation(), "Samsung")
        val extras = builder.build().extras
        listOf(
            DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_ICON,
            DeviceVendorLiveNotificationAdapter.EXTRA_CARD_ICON,
            DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_ICON,
            DeviceVendorLiveNotificationAdapter.EXTRA_AOD_REMOTE_APP_ICON,
            DeviceVendorLiveNotificationAdapter.EXTRA_CARD_ICON_BACKGROUND,
        ).forEach {
            assertFalse(extras.containsKey(it))
        }
    }

    @Test
    @Config(sdk = [35])
    fun `api 35 does not request official android live update`() {
        val builder = builder()

        val result = AndroidLiveNotificationAdapter.apply(builder, presentation(), 35)
        val notification = builder.build()

        assertFalse(result.promotionRequested)
        assertNull(result.metric)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    @Config(sdk = [36])
    fun `api 36 requests promotion and puts glucose plus trend in capsule`() {
        val builder = builder()

        val result = AndroidLiveNotificationAdapter.apply(builder, presentation(), 36)
        val notification = builder.build()

        assertTrue(result.promotionRequested)
        assertEquals("118 ↘", result.shortCriticalText)
        assertNull(result.metric)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    @Config(sdk = [37])
    fun `api 37 uses fixed integer glucose as critical metric`() {
        val builder = builder()

        val result = AndroidLiveNotificationAdapter.apply(builder, presentation(), 37)
        val metricValue = result.metric!!.value

        assertTrue(metricValue is FixedInt)
        assertEquals(118, (metricValue as FixedInt).value)
        assertEquals(0, result.criticalMetricIndex)
        assertTrue(builder.build().hasPromotableCharacteristics())
    }

    @Test
    @Config(sdk = [37])
    fun `api 37 integer metric uses the same rounding as the glucose title`() {
        val builder = builder()
        val rounded = state().copy(glucose = state().glucose!!.copy(valueMgDl = 118.8))

        val result = AndroidLiveNotificationAdapter.apply(builder, notificationPresentation(rounded, NOW), 37)

        assertEquals("119", notificationPresentation(rounded, NOW).glucoseText)
        assertEquals(119, (result.metric!!.value as FixedInt).value)
    }

    @Test
    @Config(sdk = [37])
    fun `api 37 mmol metric retains one fraction digit`() {
        val builder = builder()
        val state = state().copy(glucose = state().glucose!!.copy(valueMgDl = 162.0, displayUnit = GlucoseUnit.MMOL_L))

        val result = AndroidLiveNotificationAdapter.apply(builder, notificationPresentation(state, NOW, GlucoseUnit.MMOL_L), 37)
        val metricValue = result.metric!!.value as FixedFloat

        assertEquals(9.0f, metricValue.value, 0f)
        assertEquals(1, metricValue.minFractionDigits)
        assertEquals(1, metricValue.maxFractionDigits)
    }

    @Test
    @Config(sdk = [36])
    fun `unknown trend is not replaced by flat in capsule`() {
        val unknown = state().copy(glucose = state().glucose!!.copy(trend = Trend.UNKNOWN))
        val builder = builder()

        val result = AndroidLiveNotificationAdapter.apply(builder, notificationPresentation(unknown, NOW), 36)

        assertEquals("118", result.shortCriticalText)
    }

    @Test
    @Config(sdk = [35])
    fun `deviceVendor adapter keeps therapy details out of the drawer while preserving now bar text`() {
        val builder = builder()

        val texts = DeviceVendorLiveNotificationAdapter.apply(context, builder, presentation(), "Samsung")
        val notification = builder.build()

        assertEquals("118 ↘ −6 · Basal 0.45U/h", texts!!.drawerPrimary)
        assertEquals("IOB 1.30U · COB 22g", texts.drawerSecondary)
        assertEquals("118 ↘", texts.nowBarPrimary)
        assertEquals("−6 mg/dL · 0m", texts.nowBarSecondary)
        assertEquals("118 ↘ −6", texts.chipExpandedText)
        assertEquals(1, notification.extras.getInt(DeviceVendorLiveNotificationAdapter.EXTRA_STYLE))
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_CARD_ICON_BACKGROUND))
        assertEquals(texts.drawerPrimary, notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_PRIMARY_INFO))
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_SECONDARY_INFO))
        assertEquals(texts.nowBarPrimary, notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_PRIMARY_INFO))
        assertEquals(texts.nowBarSecondary, notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_NOWBAR_SECONDARY_INFO))
        assertEquals(texts.chipExpandedText, notification.extras.getString(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_EXPANDED_TEXT))
        assertEquals(DeviceVendorLiveNotificationAdapter.systemAccentColor(context), notification.extras.getInt(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_BACKGROUND_COLOR))
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_CHIP_EXPANDED_VIEW))
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `deviceVendor now bar derives flat arrow from plus three five minute delta`() {
        val state =
            state().copy(
                glucose = state().glucose!!.copy(trend = Trend.FORTY_FIVE_UP, deltaMgDl = 3.0),
            )

        val presentation = notificationPresentation(state, NOW)
        val texts = DeviceVendorLiveNotificationAdapter.texts(presentation)

        assertEquals("118 ↗ +3 · Basal 0.45U/h", texts.drawerPrimary)
        assertEquals("118 →", texts.nowBarPrimary)
        assertEquals("+3 mg/dL · 0m", texts.nowBarSecondary)
        assertEquals("118 ↗ +3", texts.chipExpandedText)
    }

    @Test
    @Config(sdk = [37])
    fun `non deviceVendor device receives no proprietary extras`() {
        val builder = builder()

        val texts = DeviceVendorLiveNotificationAdapter.apply(context, builder, presentation(), "Google")
        val notification = builder.build()

        assertNull(texts)
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_STYLE))
        assertFalse(notification.extras.containsKey(DeviceVendorLiveNotificationAdapter.EXTRA_PRIMARY_INFO))
    }

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentTitle("Sugarlicious")

    private fun presentation(): NotificationPresentation = notificationPresentation(state(), NOW)

    private fun state(): TherapyDisplayState =
        TherapyDisplayState(
            receivedAtEpochMs = NOW,
            glucose =
                GlucoseState(
                    valueMgDl = 118.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FORTY_FIVE_DOWN,
                    measuredAtEpochMs = NOW,
                    deltaMgDl = -6.0,
                ),
            insulin = InsulinState(totalIob = 1.3),
            carbs = CarbState(cobGrams = 22.0),
            basal = app.sugarlicious.model.BasalState(currentUnitsPerHour = 0.45),
        )

    private companion object {
        const val NOW = 2_000_000L
    }
}
