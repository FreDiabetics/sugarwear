package app.sugarlicious.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RebrandingWireCompatibilityTest {
    @Test fun existingDisplaySemanticsFieldRemainsReadableAndWritable() {
        val old = """{"receivedAtEpochMs":123,"source":"ANDROID_APS","aapsDisplaySemantics":{"insulinDigits":3}}"""
        val restored = Json.decodeFromString<TherapyDisplayState>(old)
        assertEquals(3, restored.therapyBridgeDisplaySemantics?.insulinDigits)
        val saved = Json.parseToJsonElement(Json.encodeToString(restored)).jsonObject
        assertTrue("aapsDisplaySemantics" in saved)
        assertFalse("therapyBridgeDisplaySemantics" in saved)
        assertEquals(DataSourceId.ANDROID_APS, restored.source)
    }

    @Test fun sourceProvenanceWireIdentifiersRemainStable() {
        val identifiers = listOf("DEXCOM_G7_WATCH", "ANDROID_APS", "DEXCOM_G7_APP", "DEXCOM_SHARE", "NIGHTSCOUT", "OTHER", "XDRIP_PLUS", "LIBRE_PATCHED")
        for (identifier in identifiers) {
            val source = Json.decodeFromString<DataSourceId>("\"$identifier\"")
            assertEquals("\"$identifier\"", Json.encodeToString(source))
        }
    }
}
