package app.sugarlicious.mobile

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.GlucoseState
import app.sugarlicious.model.GlucoseUnit
import app.sugarlicious.model.TherapyDisplayState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobileCanonicalCgmTest {
    @Test
    fun `legacy watch snapshot sanitizes to AndroidAPS source rather than Other`() {
        val now = System.currentTimeMillis()
        val legacy =
            TherapyDisplayState(
                source = DataSourceId.DEXCOM_G7_WATCH,
                receivedAtEpochMs = now,
                glucose =
                    GlucoseState(
                        valueMgDl = 123.0,
                        displayUnit = GlucoseUnit.MG_DL,
                        measuredAtEpochMs = now - 60_000L,
                        source = DataSourceId.DEXCOM_G7_WATCH,
                    ),
            )

        val sanitized = legacy.withoutDirectWatchCgm()

        assertEquals(DataSourceId.ANDROID_APS, sanitized.source)
        assertEquals(null, sanitized.glucose)
        assertEquals("MOBILE_PHONE_ONLY:NO_WATCH_CGM", sanitized.sourceContract)
    }
}
