package app.sugarlicious.datasource.manufacturer

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManufacturerBroadcastAdapterTest {
    private val now = 1_800_000_000_000L

    private fun payload(vararg samples: Pair<Long, Int>) =
        Bundle().apply {
            putString("sensorType", "G7")
            putString("packageName", "com.dexcom.g7")
            putLong("sensorInsertionTime", (now - 86_400_000L) / 1000)
            putBundle(
                "glucoseValues",
                Bundle().apply {
                    samples.forEachIndexed { i, (time, value) ->
                        putBundle(
                            i.toString(),
                            Bundle().apply {
                                putLong("timestamp", time / 1000)
                                putInt("glucoseValue", value)
                                putString("trendArrow", "Flat")
                            },
                        )
                    }
                },
            )
        }

    @Test fun `backfill before sensor insertion cannot acquire the new session identity`() {
        val input = payload(now to 120, now - 600_000 to 110).apply { putLong("sensorInsertionTime", (now - 300_000) / 1000) }
        val result = requireNotNull(ManufacturerBroadcastAdapter.parse(input, now))
        assertEquals(1, result.glucoseHistory.size)
        assertEquals(now, result.glucoseHistory.single().measuredAtEpochMs)
    }

    @Test fun `APK batch seconds become milliseconds and newest reading wins regardless of bundle order`() {
        val result = requireNotNull(ManufacturerBroadcastAdapter.parse(payload(now to 125, now - 300_000 to 120), now))
        assertEquals(now, result.glucose!!.measuredAtEpochMs)
        assertEquals(125.0, result.glucose!!.valueMgDl, 0.0)
        assertEquals("DEXCOM_G7_APP", result.source.name)
        assertNotNull(result.glucose!!.sessionId)
        assertEquals(2, result.glucoseHistory.size)
    }

    @Test fun `malformed future and invalid glucose samples cannot displace valid readings`() {
        val input = payload(now to 120, now + 600_000 to 140, now - 300_000 to 0)
        val result = requireNotNull(ManufacturerBroadcastAdapter.parse(input, now))
        assertEquals(1, result.glucoseHistory.size)
        assertEquals(120.0, result.glucose!!.valueMgDl, 0.0)
    }

    @Test fun `wrong source empty batch and future sensor identity are rejected`() {
        assertNull(ManufacturerBroadcastAdapter.parse(payload(), now))
        assertNull(ManufacturerBroadcastAdapter.parse(payload(now to 120).apply { putString("sensorType", "G6") }, now))
        assertNull(ManufacturerBroadcastAdapter.parse(payload(now to 120).apply { putLong("sensorInsertionTime", (now + 600_000) / 1000) }, now))
    }

    @Test fun `duplicate samples are deduplicated without manufacturing therapy data`() {
        val result = requireNotNull(ManufacturerBroadcastAdapter.parse(payload(now to 120, now to 120), now))
        assertEquals(1, result.glucoseHistory.size)
        assertNull(result.insulin)
        assertNull(result.basal)
        assertNull(result.carbs)
    }
}
