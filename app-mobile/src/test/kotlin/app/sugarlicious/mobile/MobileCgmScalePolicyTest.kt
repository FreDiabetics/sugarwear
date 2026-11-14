package app.sugarlicious.mobile

import app.sugarlicious.model.CgmGraphScaleMode
import app.sugarlicious.model.CgmThresholds
import org.junit.Assert.*
import org.junit.Test

class MobileCgmScalePolicyTest {
    @Test fun `dynamic modes zoom measured range without forcing zero or extreme thresholds`() {
        for (mode in listOf(CgmGraphScaleMode.DYNAMIC, CgmGraphScaleMode.LOGARITHMIC_DYNAMIC)) {
            val scale = resolveMobileCgmScale(mode, listOf(100.0, 130.0), 0.0, 400.0, CgmThresholds())
            assertTrue(scale.minimumMgDl > 40.0)
            assertTrue(scale.maximumMgDl < 220.0)
            assertTrue(scale.ratio(80.0) < .25)
            val high = resolveMobileCgmScale(mode, listOf(300.0, 380.0), 0.0, 400.0, CgmThresholds())
            assertTrue(high.maximumMgDl > 380.0)
        }
    }

    @Test fun `fixed modes preserve configured bounds while using the same value transform`() {
        for (mode in listOf(CgmGraphScaleMode.STATIC, CgmGraphScaleMode.LOGARITHMIC)) {
            val first = resolveMobileCgmScale(mode, listOf(100.0), 0.0, 220.0, CgmThresholds())
            val second = resolveMobileCgmScale(mode, listOf(350.0), 0.0, 220.0, CgmThresholds())
            assertEquals(first, second)
            assertEquals(0.0, first.minimumMgDl, 0.0)
            assertEquals(220.0, first.maximumMgDl, 0.0)
            assertEquals(80.0, first.inverseRatio(first.ratio(80.0)), .000001)
        }
    }
}
