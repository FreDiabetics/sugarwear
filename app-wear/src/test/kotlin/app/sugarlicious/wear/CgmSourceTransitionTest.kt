package app.sugarlicious.wear

import app.sugarlicious.model.CgmSourceState
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.protocol.WatchDataSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmSourceTransitionTest {
    @Test fun `repeated configuration sync does not retrigger collector`() {
        assertFalse(
            shouldApplyCgmCollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.DEXCOM_G7_WATCH,
            ),
        )
        assertFalse(
            shouldApplyCgmCollectorSourceTransition(
                WatchDataSource.PHONE,
                WatchDataSource.PHONE,
            ),
        )
    }

    @Test fun `changing selected source triggers collector source control once`() {
        assertTrue(
            shouldApplyCgmCollectorSourceTransition(
                WatchDataSource.PHONE,
                WatchDataSource.DEXCOM_G7_WATCH,
            ),
        )
        assertTrue(
            shouldApplyCgmCollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.PHONE,
            ),
        )
        assertTrue(
            shouldApplyCgmCollectorSourceTransition(
                WatchDataSource.DEXCOM_G7_WATCH,
                WatchDataSource.AUTOMATIC,
            ),
        )
    }

    @Test fun `alerts follow canonical Watch Direct in automatic mode`() {
        assertTrue(shouldEnableCgmAlerts(WatchDataSource.AUTOMATIC, DataSourceId.DEXCOM_G7_WATCH, CgmSourceState.WATCH_DIRECT))
        assertFalse(shouldEnableCgmAlerts(WatchDataSource.AUTOMATIC, DataSourceId.ANDROID_APS, CgmSourceState.MOBILE_PRIMARY))
        assertTrue(shouldEnableCgmAlerts(WatchDataSource.AUTOMATIC, DataSourceId.ANDROID_APS, CgmSourceState.NO_SOURCE))
        assertFalse(shouldEnableCgmAlerts(WatchDataSource.AUTOMATIC, null, null))
        assertTrue(shouldEnableCgmAlerts(WatchDataSource.DEXCOM_G7_WATCH, null, null))
        assertFalse(shouldEnableCgmAlerts(WatchDataSource.PHONE, DataSourceId.DEXCOM_G7_WATCH, CgmSourceState.WATCH_DIRECT))
    }
}
