package app.sugarlicious.sensor.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RebrandingDiagnosticCompatibilityTest {
    @Test fun historicalCycleTimingKeepsNamedSensorCount() {
        val old = requireNotNull(javaClass.getResourceAsStream("/rebranding/legacy-cycle-timing.json")).bufferedReader().use { it.readText() }
        val json = Json { ignoreUnknownKeys = true }
        val timing = json.decodeFromString<CollectorCycleTiming>(old)
        assertEquals(7, timing.scanNamedCgmResults)
        assertEquals(12, timing.scanTotalResults)
        val saved = json.parseToJsonElement(json.encodeToString(timing)).jsonObject
        assertTrue("scanNamedG7Results" in saved)
        assertFalse("scanNamedCgmResults" in saved)
    }
}
