package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingOrigin
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.*
import org.junit.Test

class CgmOverviewPresentationPolicyTest {
    private val start = 1_000_000L
    private val reading = CgmReading("r", DataSourceId.DEXCOM_G7_WATCH, "s", "session", 120.0, start + 1, start + 2)
    private val state = CgmPersistedState(sensor = CgmSensor("s", "session"), collectorEnabled = true, lastReading = reading, lastSuccessfulConnectionEpochMs = start + 1)
    private val resume = CgmUiSensorTransition(CgmUiSensorAction.RESUME, start, "s", "session")

    private fun display(s: CgmPersistedState = state, t: CgmUiSensorTransition? = resume, now: Long = start + 100) = cgmOverviewPresentation(s, true, t, now)

    @Test fun `connection without a new measurement remains inline`() {
        val result = display(state.copy(lastReading = reading.copy(timestampEpochMs = start - 1)))
        assertEquals(CgmOverviewMode.CONNECTING, result.mode)
        assertEquals("Verbunden – warte auf Glukosewert", result.phase)
        assertFalse(result.animate)
    }

    @Test fun `only fresh live measurements of current session end resume`() {
        assertEquals(CgmOverviewMode.LIVE, display().mode)
        listOf(
            reading.copy(origin = CgmReadingOrigin.BACKFILL),
            reading.copy(displayOnly = true),
            reading.copy(sessionId = "previous"),
            reading.copy(sensorId = "previous"),
            reading.copy(timestampEpochMs = start + 200),
            reading.copy(status = CgmReadingStatus.INVALID),
        ).forEach { assertEquals(CgmOverviewMode.CONNECTING, display(state.copy(lastReading = it)).mode) }
    }

    @Test fun `pause deadline is exact and survives reconstruction`() {
        val pause = resume.copy(action = CgmUiSensorAction.PAUSE)
        val paused = state.copy(collectorEnabled = false)
        assertNotNull(display(paused, pause, start + CGM_PAUSED_VALUE_DURATION_MS - 1).reading)
        assertNull(display(paused, pause.copy(), start + CGM_PAUSED_VALUE_DURATION_MS).reading)
        assertEquals(CgmOverviewMode.PAUSED, display(paused, pause, start + CGM_PAUSED_VALUE_DURATION_MS).mode)
    }

    @Test fun `no sensor and explicit pause take precedence over radio and historical errors`() {
        val pause = resume.copy(action = CgmUiSensorAction.PAUSE)
        assertEquals(CgmOverviewMode.NO_SENSOR, display(state.copy(sensor = null)).mode)
        assertEquals(
            CgmOverviewMode.PAUSED,
            cgmOverviewPresentation(
                state.copy(collectorEnabled = false),
                true,
                pause,
                start + 100,
                false,
                false,
            ).mode,
        )
        assertEquals(CgmOverviewMode.BLUETOOTH_OFF, cgmOverviewPresentation(state, true, resume, start + 100, false).mode)
        assertEquals(CgmOverviewMode.PERMISSION_REQUIRED, cgmOverviewPresentation(state, true, resume, start + 100, true, false).mode)
    }

    @Test fun `failed first pairing never looks like a paused established sensor`() {
        val incomplete =
            state.copy(
                collectorEnabled = false,
                lastReading = null,
                lastSuccessfulConnectionEpochMs = null,
                lastError = CgmCollectorError("G7-AUTH-204", false, start + 2, "Authentifizierung fehlgeschlagen"),
            )
        assertEquals(CgmOverviewMode.NO_SENSOR, display(incomplete, resume.copy(action = CgmUiSensorAction.PAUSE)).mode)
        assertEquals(CgmOverviewMode.NO_SENSOR, display(incomplete, resume.copy(action = CgmUiSensorAction.PAIR)).mode)
        assertEquals(CgmOverviewMode.NO_SENSOR, display(incomplete, null).mode)
        assertEquals(
            CgmOverviewMode.PAUSED,
            cgmOverviewPresentation(
                incomplete,
                true,
                resume.copy(action = CgmUiSensorAction.PAUSE),
                start + 100,
                lastValidReading = reading,
            ).mode,
        )
    }

    @Test fun `disabled collector without explicit pause never renders paused during pairing`() {
        val waiting =
            state.copy(
                collectorEnabled = false,
                lastReading = reading.copy(timestampEpochMs = start - 1),
            )
        val result = display(waiting, resume, start + 100)
        assertEquals(CgmOverviewMode.CONNECTING, result.mode)
        assertNotEquals(CgmOverviewMode.PAUSED, result.mode)
    }

    @Test fun `terminal failure stops resume and completed resume never reappears`() {
        val error = CgmCollectorError("G7-AUTH-204", false, start + 2, "Authentifizierung fehlgeschlagen")
        assertEquals(CgmOverviewMode.ERROR, display(state.copy(lastReading = null, lastError = error)).mode)
        assertEquals(CgmOverviewMode.SIGNAL_LOSS, display(state.copy(lastReading = null), resume.copy(completed = true)).mode)
        assertEquals(
            CgmOverviewMode.SIGNAL_LOSS,
            display(state.copy(collectorEnabled = false), resume.copy(completed = true)).mode,
        )
    }

    @Test fun `a replacement sensor cannot inherit previous resume`() {
        assertEquals(CgmOverviewMode.SIGNAL_LOSS, display(state.copy(sensor = CgmSensor("new", "new"), lastReading = null)).mode)
        assertEquals(CgmOverviewMode.SIGNAL_LOSS, display(state.copy(lastReading = null), resume.copy(sensorId = null, sessionId = null)).mode)
    }
}
