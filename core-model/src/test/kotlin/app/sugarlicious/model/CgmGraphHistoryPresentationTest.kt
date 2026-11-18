package app.sugarlicious.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CgmGraphHistoryPresentationTest {
    @Test fun `only verified same source sensor changes retire a sample`() {
        val active = CgmGraphSensorIdentity(DataSourceId.DEXCOM_G7_WATCH, "new", "new-session")
        assertTrue(CgmGraphHistoryPresentation.isRetired(GlucoseSample(120.0, 1, DataSourceId.DEXCOM_G7_WATCH, "old", "old-session"), active))
        assertFalse(CgmGraphHistoryPresentation.isRetired(GlucoseSample(120.0, 1, DataSourceId.DEXCOM_G7_WATCH, "new", "new-session"), active))
        assertFalse(CgmGraphHistoryPresentation.isRetired(GlucoseSample(120.0, 1, DataSourceId.ANDROID_APS, "old", "old-session"), active))
        assertFalse(CgmGraphHistoryPresentation.isRetired(GlucoseSample(120.0, 1, DataSourceId.DEXCOM_G7_WATCH), active))
    }

    @Test fun `known insertion session identifies a manufacturer sensor change`() {
        val active = CgmGraphSensorIdentity(DataSourceId.DEXCOM_G7_APP, sessionId = "new-insertion")
        assertTrue(CgmGraphHistoryPresentation.isRetired(GlucoseSample(120.0, 1, DataSourceId.DEXCOM_G7_APP, sessionId = "old-insertion"), active))
    }
}
