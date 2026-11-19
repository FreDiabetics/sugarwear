package app.sugarwear

import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmSensor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class CgmConnectionCueTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val state = CgmPersistedState(sensor = CgmSensor("sensor", "session"), collectorEnabled = true, protocolState = CgmProtocolState.AUTHENTICATED)
    private val preferences get() = context.getSharedPreferences("cgm_connection_cue", 0)

    @Before fun reset() {
        preferences.edit().clear().commit()
        context
            .getSharedPreferences("g7_ui_sensor_transition", 0)
            .edit()
            .clear()
            .commit()
        CgmSensorStateStore(context).save(state)
    }

    @Test fun `deliberate connection is confirmed and claimed without an overview subscriber`() {
        CgmCollectorUiTransitionStore.record(context, CgmUiSensorAction.RESUME)
        CgmConnectionCue.confirmed(context, state)
        val confirmed = CgmCollectorUiTransitionStore.read(context)!!
        assertNotNull(confirmed.connectedAt)
        assertNotNull(preferences.getString("claimed", null))
        CgmConnectionCue.confirmed(context, state)
        assertEquals(confirmed, CgmCollectorUiTransitionStore.read(context))
        assertFalse(CgmConnectionCue.claim(context, preferences.getString("claimed", null)!!))
    }

    @Test fun `routine authenticated connections have no cue operation`() {
        CgmConnectionCue.confirmed(context, state)
        assertNull(preferences.getString("claimed", null))
        assertNull(CgmCollectorUiTransitionStore.read(context))
    }

    @Test fun `collector start and terminal authentication failure cannot confirm resume`() {
        CgmCollectorUiTransitionStore.record(context, CgmUiSensorAction.RESUME)
        val operation = CgmCollectorUiTransitionStore.read(context)!!
        CgmConnectionCue.confirmed(context, state.copy(protocolState = CgmProtocolState.SCANNING))
        assertNull(CgmCollectorUiTransitionStore.read(context)!!.connectedAt)
        val error = CgmCollectorError("G7-AUTH-204", false, operation.startedAt, "Authentifizierung fehlgeschlagen")
        CgmConnectionCue.confirmed(context, state.copy(lastError = error))
        assertNull(CgmCollectorUiTransitionStore.read(context)!!.connectedAt)
        assertNull(preferences.getString("claimed", null))
    }

    @Test fun `first pairing confirms once but a different session cannot claim its cue`() {
        CgmCollectorUiTransitionStore.record(context, CgmUiSensorAction.PAIR)
        CgmConnectionCue.confirmed(context, state.copy(sensor = CgmSensor("different", "session")))
        assertNull(preferences.getString("claimed", null))
        CgmConnectionCue.confirmed(context, state)
        assertNotNull(CgmCollectorUiTransitionStore.read(context)!!.connectedAt)
        assertNotNull(preferences.getString("claimed", null))
    }
}
