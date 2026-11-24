package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmPairingGateTest {
    @Test fun `first start remains gated without validated session`() {
        assertFalse(hasUsableCollectorSession(null, null))
    }

    @Test fun `sensor change cannot reuse prior successful session`() {
        assertFalse(hasUsableCollectorSession(reading("old"), "new"))
    }

    @Test fun `validated matching live session survives restart`() {
        assertTrue(hasUsableCollectorSession(reading("sensor"), "sensor", "sensor"))
        assertFalse(
            hasUsableCollectorSession(
                reading("sensor").copy(sessionId = "old-session"),
                "sensor",
                "sensor",
            ),
        )
        assertFalse(
            hasUsableCollectorSession(
                reading("sensor").copy(origin = CgmReadingOrigin.BACKFILL),
                "sensor",
                "sensor",
            ),
        )
        assertFalse(
            hasUsableCollectorSession(
                reading("sensor").copy(displayOnly = true),
                "sensor",
                "sensor",
            ),
        )
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
