package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectDataDialogTest {
    @Test
    fun `details menu exposes every Health Connect read type`() {
        val items = HealthConnectDataDialog.metricItems(null)

        assertEquals(HealthConnectIntegration.readableRecordTypes.size, items.size)
        assertEquals(24, items.size)
        assertTrue(items.any { it.label == "Blutzucker" })
        assertTrue(items.any { it.label == "VO₂max" })
    }

    @Test
    fun `details menu formats snapshot values`() {
        val items =
            HealthConnectDataDialog
                .metricItems(
                    HealthConnectSnapshot(
                        syncedAtEpochMs = 1L,
                        steps = 12_345,
                        bloodGlucoseMgDl = 123.0,
                        systolicMmHg = 120.0,
                        diastolicMmHg = 80.0,
                    ),
                ).associateBy { it.label }

        assertEquals("12.345", items.getValue("Schritte").value)
        assertEquals("123 mg/dL", items.getValue("Blutzucker").value)
        assertEquals("120/80 mmHg", items.getValue("Blutdruck").value)
    }
}
