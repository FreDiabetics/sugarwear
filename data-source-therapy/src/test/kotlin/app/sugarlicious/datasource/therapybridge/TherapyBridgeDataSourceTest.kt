package app.sugarlicious.datasource.therapybridge
import kotlin.test.*

class TherapyBridgeDataSourceTest {
    @Test
    fun `official package catalog contains only standard AndroidAPS`() {
        assertEquals(listOf("info.nightscout.androidaps"), TherapyBridgeCapabilityDetector.KNOWN_PACKAGES)
    }

    @Test fun detectsContracts() {
        val d = TherapyBridgeCapabilityDetector()
        assertEquals(TherapyBroadcastContract.UNSUPPORTED, d.detect(emptyMap()))
        assertEquals(
            TherapyBroadcastContract.STABLE_LEGACY_STATUS,
            d.detect(
                mapOf(
                    "glucoseMgdl" to 100,
                    "glucoseTimeStamp" to 1,
                ),
            ),
        )
        assertEquals(
            TherapyBroadcastContract.DEV_EXTENDED_STATUS_V1,
            d.detect(
                mapOf(
                    "glucoseMgdl" to 100,
                    "glucoseTimeStamp" to 1,
                    "deltaMgdl" to 1.0,
                ),
            ),
        )
        assertEquals("AAPS_EXTENDED_STATUS_V1", TherapyBroadcastContract.DEV_EXTENDED_STATUS_V1.id)
    }

    @Test fun keepsOnlyLastValidState() {
        val s = TherapyBridgeDataSource()
        assertNull(s.accept(mapOf("glucoseMgdl" to 1, "glucoseTimeStamp" to 1), 2))
        val valid =
            assertNotNull(
                s.accept(
                    mapOf(
                        "glucoseMgdl" to 100,
                        "glucoseTimeStamp" to 1,
                    ),
                    2,
                ),
            )
        ;assertEquals(valid, s.latest())
    }

    @Test fun rejectsImplausibleFutureState() {
        val s = TherapyBridgeDataSource()
        assertNull(
            s.accept(
                mapOf(
                    "glucoseMgdl" to 100,
                    "glucoseTimeStamp" to 400_001L,
                ),
                100_000L,
            ),
        )
    }

    @Test fun `rejects non finite timestamps and glucose values`() {
        val validator = TherapyPayloadValidator()

        assertFalse(validator.isValid(mapOf("glucoseMgdl" to Double.NaN, "glucoseTimeStamp" to 1L), 2L))
        assertFalse(validator.isValid(mapOf("glucoseMgdl" to 100.0, "glucoseTimeStamp" to Double.POSITIVE_INFINITY), 2L))
    }
}
