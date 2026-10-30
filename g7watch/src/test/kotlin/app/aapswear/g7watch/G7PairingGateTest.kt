package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.CgmReadingStatus
import app.aapswear.model.DataSourceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7PairingGateTest {
    @Test fun `first start remains gated without validated session`() {
        assertFalse(hasUsableCollectorSession(null, null))
    }

    @Test fun `sensor change cannot reuse prior successful session`() {
        assertFalse(hasUsableCollectorSession(reading("old"), "new"))
    }

    @Test fun `validated matching session survives restart`() {
        assertTrue(hasUsableCollectorSession(reading("sensor"), "sensor"))
    }

    private fun reading(sensorId: String) =
        CgmReading(
            id = "id",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = sensorId,
            sessionId = sensorId,
            glucoseMgDl = 120.0,
            timestampEpochMs = 1,
            receivedAtEpochMs = 1,
            status = CgmReadingStatus.VALID,
        )
}
