package app.sugarlicious.mobile

import app.sugarlicious.mobile.ui.theme.SugarliciousColorRole
import app.sugarlicious.model.CgmThresholds
import org.junit.Assert.assertEquals
import org.junit.Test

class OverviewGlucoseColorTest {
    @Test fun `current glucose keeps its configured color through all five ranges`() {
        val thresholds = CgmThresholds(veryLowMgDl = 55.0, lowMgDl = 80.0, highMgDl = 160.0, veryHighMgDl = 250.0)
        listOf(
            40.0 to SugarliciousColorRole.GLUCOSE_VERY_LOW,
            70.0 to SugarliciousColorRole.GLUCOSE_LOW,
            120.0 to SugarliciousColorRole.GLUCOSE_IN_RANGE,
            200.0 to SugarliciousColorRole.GLUCOSE_HIGH,
            300.0 to SugarliciousColorRole.GLUCOSE_VERY_HIGH,
            200.0 to SugarliciousColorRole.GLUCOSE_HIGH,
            120.0 to SugarliciousColorRole.GLUCOSE_IN_RANGE,
        ).forEach { (value, expected) ->
            assertEquals("value=$value", expected, overviewGlucoseColorRole(widgetGlucoseColorRole(value, thresholds)))
        }
        assertEquals(SugarliciousColorRole.TEXT_PRIMARY, overviewGlucoseColorRole(WidgetColorRole.TEXT))
    }
}
