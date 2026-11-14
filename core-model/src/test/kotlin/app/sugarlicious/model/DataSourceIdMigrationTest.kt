package app.sugarlicious.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSourceIdMigrationTest {
    @Test
    fun `xdrip source retains its explicit identity after being enabled again`() {
        assertEquals(DataSourceId.XDRIP_PLUS, Json.decodeFromString<DataSourceId>("\"XDRIP_PLUS\""))
        assertTrue(DataSourceId.entries.any { it.name == "XDRIP_PLUS" })
    }

    @Test
    fun `legacy xdrip history sample keeps its measurement`() {
        val raw = """{"valueMgDl":121.0,"measuredAtEpochMs":1234,"source":"XDRIP_PLUS"}"""
        val sample = Json.decodeFromString<GlucoseSample>(raw)

        assertEquals(121.0, sample.valueMgDl, 0.0)
        assertEquals(1234L, sample.measuredAtEpochMs)
        assertEquals(DataSourceId.XDRIP_PLUS, sample.source)
    }
}
