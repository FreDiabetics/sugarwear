package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmSensorLifetimeTest {
    private val day = 24L * 60L * 60_000L
    private val start = 1_000_000_000L

    private fun state(
        end: Long? = null,
        readingEnd: Long? = null,
        matching: Boolean = true,
        graceEnd: Long? = null,
        readingGraceEnd: Long? = null,
    ) =
        CgmPersistedState(
            sensor = CgmSensor(
                sensorId = "G7",
                sessionId = "session-A",
                sensorStartEpochMs = start,
                sensorEndEpochMs = end,
                graceEndEpochMs = graceEnd,
            ),
            lastReading =
                readingEnd?.let {
                    CgmReading(
                        id = "reading",
                        source = DataSourceId.DEXCOM_G7_WATCH,
                        sensorId = "G7",
                        sessionId = if (matching) "session-A" else "old-session",
                        glucoseMgDl = 111.0,
                        timestampEpochMs = start,
                        receivedAtEpochMs = start,
                        sensorEndEpochMs = it,
                        graceEndEpochMs = readingGraceEnd,
                    )
                },
        )

    @Test
    fun `ten verified remaining days draw all ten segments`() {
        val result = checkNotNull(cgmSensorLifetime(state(end = start + 10L * day), start))
        assertTrue(result.hasVerifiedEnd)
        assertEquals(10, result.days)
        assertEquals(0, result.hours)
        assertEquals(0, result.minutes)
        assertEquals(10, result.remainingDaySegments)
    }

    @Test
    fun `partial days never misrepresent the remaining time`() {
        val result = checkNotNull(cgmSensorLifetime(state(end = start + 10L * day), start + day + 60_000L))
        assertEquals(8, result.days)
        assertEquals(23, result.hours)
        assertEquals(59, result.minutes)
        assertEquals(9, result.remainingDaySegments)
    }

    @Test
    fun `end in current session reading is usable when sensor metadata lacks end`() {
        val result = checkNotNull(cgmSensorLifetime(state(readingEnd = start + day), start))
        assertTrue(result.hasVerifiedEnd)
        assertEquals(1, result.remainingDaySegments)
    }

    @Test
    fun `different session reading cannot invent a remaining lifetime`() {
        val result = checkNotNull(cgmSensorLifetime(state(readingEnd = start + day, matching = false), start))
        assertFalse(result.hasVerifiedEnd)
        assertEquals(0, result.remainingDaySegments)
    }

    @Test
    fun `missing end is explicitly unknown and an expired sensor has zero segments`() {
        assertFalse(checkNotNull(cgmSensorLifetime(state(), start)).hasVerifiedEnd)
        val expired = checkNotNull(cgmSensorLifetime(state(end = start + day), start + day + 1L))
        assertTrue(expired.expired)
        assertEquals(0, expired.remainingDaySegments)
    }

    @Test
    fun `regular countdown is abbreviated while verified 12h grace stays separate`() {
        val end = start + 10L * day
        val now = end - day - 20L * 60L * 60_000L - 18L * 60_000L
        val lifetime = checkNotNull(cgmSensorLifetime(state(end = end, graceEnd = end + 12L * 60L * 60_000L), now))
        assertEquals("1T 20h 18m", lifetime.compactRemainingTime())
        assertEquals("+ 12h Toleranzzeit", lifetime.toleranceTimeText())
        assertEquals(2, lifetime.remainingDaySegments)
        assertTrue(lifetime.hasVerifiedGraceEnd)
        assertFalse(lifetime.inGracePeriod)
    }

    @Test
    fun `grace begins after regular end and shows its actual remaining time`() {
        val end = start + 10L * day
        val graceEnd = end + 12L * 60L * 60_000L
        val lifetime = checkNotNull(cgmSensorLifetime(state(end = end, graceEnd = graceEnd), end + 5L * 60_000L))
        assertTrue(lifetime.expired)
        assertTrue(lifetime.inGracePeriod)
        assertFalse(lifetime.graceExpired)
        assertEquals(0, lifetime.remainingDaySegments)
        assertEquals("0T 0h 0m", lifetime.compactRemainingTime())
        assertEquals("11h 55m Toleranzzeit", lifetime.toleranceTimeText())
    }

    @Test
    fun `after grace expires no tolerance time is claimed`() {
        val end = start + 10L * day
        val graceEnd = end + 12L * 60L * 60_000L
        val lifetime = checkNotNull(cgmSensorLifetime(state(end = end, graceEnd = graceEnd), graceEnd + 1L))
        assertTrue(lifetime.graceExpired)
        assertFalse(lifetime.inGracePeriod)
        assertEquals("Toleranzzeit beendet", lifetime.toleranceTimeText())
    }

    @Test
    fun `missing or inconsistent grace metadata never invents 12 hours`() {
        val end = start + 10L * day
        val noGrace = checkNotNull(cgmSensorLifetime(state(end = end), start))
        assertFalse(noGrace.hasVerifiedGraceEnd)
        assertNull(noGrace.toleranceTimeText())
        val invalidGrace = checkNotNull(cgmSensorLifetime(state(end = end, graceEnd = end + day), start))
        assertFalse(invalidGrace.hasVerifiedGraceEnd)
        assertNull(invalidGrace.toleranceTimeText())
    }

    @Test
    fun `grace deadline from reading must match active sensor session`() {
        val end = start + 10L * day
        val graceEnd = end + 12L * 60L * 60_000L
        val fresh = checkNotNull(
            cgmSensorLifetime(state(readingEnd = end, readingGraceEnd = graceEnd), start),
        )
        assertTrue(fresh.hasVerifiedGraceEnd)
        val unrelated = checkNotNull(
            cgmSensorLifetime(state(readingEnd = end, readingGraceEnd = graceEnd, matching = false), start),
        )
        assertFalse(unrelated.hasVerifiedGraceEnd)
    }

    @Test
    fun `no sensor does not create a lifetime`() {
        assertNull(cgmSensorLifetime(CgmPersistedState(), start))
    }
}
