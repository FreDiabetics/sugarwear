package app.sugarwear

import org.junit.Assert.assertEquals
import org.junit.Test

class CgmGlucoseTileModeTest {
    @Test fun `pending alarm takes precedence without discarding pairing or stop state`() {
        assertEquals(CgmGlucoseTileMode.ALARM, cgmGlucoseTileMode(true, CgmPairingScreenStep.ENTER_CODE, true))
        assertEquals(CgmGlucoseTileMode.PAIRING, cgmGlucoseTileMode(false, CgmPairingScreenStep.ENTER_CODE, true))
        assertEquals(CgmGlucoseTileMode.STOP, cgmGlucoseTileMode(false, null, true))
        assertEquals(CgmGlucoseTileMode.SENSOR, cgmGlucoseTileMode(false, null, false))
    }
}
