package app.sugarlicious.wear

import app.sugarlicious.model.BasalState
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class WearBasalDetailsTest {
    @Test fun `percent-only TBR uses base times percent and not the unchanged base rate`() {
        val result = wearBasalDetails(BasalState(currentUnitsPerHour = 1.0, tempPercent = 200), 1_000, Locale.GERMAN)
        assertEquals("1U/h @200%", result.base)
        assertTrue(result.temporary.startsWith("2U/h "))
    }

    @Test fun `base and actual temp show source percent and elapsed over programmed minutes`() {
        val result = wearBasalDetails(BasalState(currentUnitsPerHour = .45, tempAbsoluteUnitsPerHour = 3.4, tempPercent = 250, tempStartedAtEpochMs = 1_000L, tempDurationMinutes = 300), 1_000L + 120 * 60_000, Locale.GERMAN)
        assertEquals("0,45U/h @250%", result.base)
        assertEquals("3,4U/h 120/300 m", result.temporary)
    }

    @Test fun `missing expired and future temps never manufacture elapsed time`() {
        assertEquals("—", wearBasalDetails(null, 1_000).temporary)
        assertEquals("Keine TBR", wearBasalDetails(BasalState(currentUnitsPerHour = 1.0, tempAbsoluteUnitsPerHour = 2.0, tempEndsAtEpochMs = 900), 1_000).temporary)
        assertEquals("Keine TBR", wearBasalDetails(BasalState(currentUnitsPerHour = 1.0, tempAbsoluteUnitsPerHour = 2.0, tempStartedAtEpochMs = 2_000), 1_000).temporary)
        assertTrue(wearBasalDetails(BasalState(tempAbsoluteUnitsPerHour = 2.0), 1_000).temporary.endsWith("—/— m"))
    }
}
