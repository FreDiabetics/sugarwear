package app.sugarlicious.mobile

import app.sugarlicious.model.BasalState
import app.sugarlicious.model.CarbState
import app.sugarlicious.model.CgmPresentationPolicy
import app.sugarlicious.model.CgmQuality
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.FreshnessPolicy
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.InsulinState
import app.sugarlicious.model.ProfileState
import app.sugarlicious.model.TherapyBridgeDisplaySemantics
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.model.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPresentationTest {
    private val now = 2_000_000L

    @Test
    fun `fresh presentation formats canonical glucose trend delta and therapy values`() {
        val presentation = notificationPresentation(state(), now)

        assertEquals("118", presentation.glucoseText)
        assertEquals(118.0, presentation.glucoseNumeric!!, 0.0)
        assertEquals("↘", presentation.trendSymbol)
        assertEquals("−6", presentation.deltaText)
        assertEquals("IOB 1.30U · COB 22g", presentation.therapyText)
        assertEquals("Basal 0.45U/h", presentation.basalText)
        assertEquals("118 ↘", presentation.androidCapsuleText)
        assertEquals("118 ↘ −6", presentation.compactPrimaryText)
        assertEquals("118 ↘ −6 · Basal 0.45U/h", presentation.drawerPrimaryText)
        assertEquals("AndroidAPS", presentation.sourceText)
        assertEquals("Loop", presentation.profileText)
        assertEquals("mg/dL", presentation.unitText)
    }

    @Test
    fun `mmol presentation keeps exactly one decimal`() {
        val presentation =
            notificationPresentation(
                state().copy(glucose = state().glucose!!.copy(valueMgDl = 162.0, deltaMgDl = -5.4, displayUnit = GlucoseUnit.MMOL_L)),
                now,
                GlucoseUnit.MMOL_L,
            )

        assertEquals("9.0", presentation.glucoseText)
        assertEquals(9.0, presentation.glucoseNumeric!!, 0.0)
        assertEquals("−0.3", presentation.deltaText)
        assertEquals("9.0 ↘", presentation.androidCapsuleText)
        assertEquals("9.0 ↘ −0.3", presentation.compactPrimaryText)
        assertEquals("mmol/L", presentation.unitText)
    }

    @Test
    fun `all canonical trends map without inventing unknown arrow`() {
        val expected =
            mapOf(
                Trend.DOUBLE_UP to "⇈",
                Trend.SINGLE_UP to "↑",
                Trend.FORTY_FIVE_UP to "↗",
                Trend.FLAT to "→",
                Trend.FORTY_FIVE_DOWN to "↘",
                Trend.SINGLE_DOWN to "↓",
                Trend.DOUBLE_DOWN to "⇊",
                Trend.UNKNOWN to null,
            )

        expected.forEach { (trend, glyph) ->
            val presentation = notificationPresentation(state().copy(glucose = state().glucose!!.copy(trend = trend)), now)
            assertEquals(trend.name, glyph, presentation.trendSymbol)
            assertEquals(if (glyph == null) "118" else "118 $glyph", presentation.androidCapsuleText)
        }
    }

    @Test
    fun `missing therapy fields stay absent instead of becoming zero`() {
        val presentation =
            notificationPresentation(
                state().copy(insulin = null, carbs = CarbState(cobGrams = 22.0), basal = null),
                now,
            )

        assertEquals("COB 22g", presentation.therapyText)
        assertNull(presentation.basalText)
        assertEquals("118 ↘ −6", presentation.drawerPrimaryText)
    }

    @Test
    fun `therapy values keep canonical precision and active temp basal`() {
        val tempEnd = now + 90_000L
        val input =
            state().copy(
                therapyBridgeDisplaySemantics = TherapyBridgeDisplaySemantics(insulinDigits = 2, carbDigits = 1, basalDigits = 3),
                insulin = InsulinState(totalIob = 0.04),
                carbs = CarbState(cobGrams = 2.25),
                basal = BasalState(currentUnitsPerHour = 0.8, tempAbsoluteUnitsPerHour = 0.0, tempEndsAtEpochMs = tempEnd),
            )

        val active = notificationPresentation(input, now)
        val expired = notificationPresentation(input, tempEnd)

        assertEquals("IOB 0.04U · COB 2.3g", active.therapyText)
        assertEquals("Basal 0.000U/h", active.basalText)
        assertEquals("Basal 0.800U/h", expired.basalText)
        assertNotEquals(active.revision, expired.revision)
        assertEquals(tempEnd, nextNotificationFreshnessBoundary(input, now))
    }

    @Test
    fun `signal loss retains last glucose trend and delta while invalid states stay hidden`() {
        val signalLoss = notificationPresentation(state(now - CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS), now)
        val sensorError =
            notificationPresentation(
                state().copy(glucose = state().glucose!!.copy(quality = CgmQuality.SENSOR_ERROR)),
                now,
            )
        val noSource = notificationPresentation(state().copy(glucose = null), now)

        assertEquals("Signalverlust", signalLoss.statusText)
        assertEquals("118", signalLoss.glucoseText)
        assertEquals("↘", signalLoss.trendSymbol)
        assertEquals("−6", signalLoss.deltaText)
        assertEquals("118 ↘", signalLoss.androidCapsuleText)
        assertEquals("Sensorfehler", sensorError.statusText)
        assertNull(sensorError.compactPrimaryText)
        assertEquals("Keine Quelle", noSource.statusText)
        assertNull(noSource.glucoseNumeric)
    }

    @Test
    fun `mode selection keeps deviceVendor independent from android api support`() {
        assertEquals(NotificationMode.STANDARD_CUSTOM, notificationMode(false, 37, "samsung"))
        assertEquals(NotificationMode.SAMSUNG_LIVE, notificationMode(true, 35, "Samsung"))
        assertEquals(NotificationMode.STANDARD_CUSTOM, notificationMode(true, 35, "Google"))
        assertEquals(NotificationMode.ANDROID_LIVE, notificationMode(true, 36, "Google"))
        assertEquals(NotificationMode.ANDROID_LIVE, notificationMode(true, 37, "Google"))
    }

    @Test
    fun `visible revision changes for therapy and freshness but ignores history`() {
        val base = notificationPresentation(state(), now)
        val therapy = notificationPresentation(state().copy(insulin = InsulinState(totalIob = 2.0)), now)
        val historyOnly = notificationPresentation(state().copy(glucoseHistory = listOf()), now)
        val delayed = notificationPresentation(state(now - FreshnessPolicy.CURRENT_MAX_MS - 1L), now)

        assertNotEquals(base.revision, therapy.revision)
        assertEquals(base.revision, historyOnly.revision)
        assertNotEquals(base.revision, delayed.revision)
    }

    @Test
    fun `next boundary advances through delayed stale and signal loss`() {
        val measuredAt = now - 60_000L
        val state = state(measuredAt)

        assertEquals(measuredAt + FreshnessPolicy.CURRENT_MAX_MS + 1L, nextNotificationFreshnessBoundary(state, now))
        assertEquals(
            measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L,
            nextNotificationFreshnessBoundary(state, measuredAt + FreshnessPolicy.CURRENT_MAX_MS + 1L),
        )
        assertEquals(
            measuredAt + CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS,
            nextNotificationFreshnessBoundary(state, measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L),
        )
        assertNull(nextNotificationFreshnessBoundary(state, measuredAt + CgmPresentationPolicy.SIGNAL_LOSS_AFTER_MS))
    }

    @Test
    fun `age participates in revision and advances on the exact measurement minute`() {
        val measuredAt = now - 90_000L
        val input = state(measuredAt)

        assertEquals("1m", notificationPresentation(input, now).ageText)
        assertNotEquals(
            notificationPresentation(input, measuredAt + 119_999L).revision,
            notificationPresentation(input, measuredAt + 120_000L).revision,
        )
        assertEquals(measuredAt + 120_000L, nextNotificationAgeBoundary(input, now))
    }

    @Test
    fun `deep sleep safety boundary skips aging and targets glucose suppression`() {
        val measuredAt = now - 60_000L
        val state = state(measuredAt)

        assertEquals(measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L, nextNotificationSafetyBoundary(state, now))
        assertNull(nextNotificationSafetyBoundary(state, measuredAt + FreshnessPolicy.DELAYED_MAX_MS + 1L))
    }

    private fun state(measuredAt: Long = now): TherapyDisplayState =
        TherapyDisplayState(
            source = DataSourceId.ANDROID_APS,
            receivedAtEpochMs = measuredAt,
            glucose =
                GlucoseState(
                    valueMgDl = 118.0,
                    displayUnit = GlucoseUnit.MG_DL,
                    trend = Trend.FORTY_FIVE_DOWN,
                    measuredAtEpochMs = measuredAt,
                    deltaMgDl = -6.0,
                ),
            insulin = InsulinState(totalIob = 1.3),
            carbs = CarbState(cobGrams = 22.0),
            basal = BasalState(currentUnitsPerHour = 0.45),
            profile = ProfileState(name = "Loop"),
        )
}
