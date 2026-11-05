package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmCollectorError
import app.sugarlicious.sensor.protocol.CgmCollectorHealth
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmProtocolState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmSensor
import app.sugarlicious.sensor.protocol.CgmSensorAvailability
import app.sugarlicious.sensor.protocol.CgmSensorState
import app.sugarlicious.sensor.protocol.CgmSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmUserStatusTest {
    private val now = 2_000_000L
    private val sensor = CgmSensor("sensor", "session", "DXCMZU")

    private fun reading(at: Long) =
        CgmReading(
            id = "reading-$at",
            source = DataSourceId.DEXCOM_G7_WATCH,
            sensorId = "sensor",
            sessionId = "session",
            glucoseMgDl = 123.0,
            timestampEpochMs = at,
            receivedAtEpochMs = at,
        )

    @Test fun `pill distinguishes paused reception from an unpaired sensor`() {
        val healthy =
            CgmPersistedState(
                sensor = sensor.copy(state = CgmSensorState.ACTIVE),
                collectorEnabled = true,
                protocolState = CgmProtocolState.RECOVERING,
                lastReading = reading(now - 5 * 60_000L),
            )

        assertEquals(CgmStatusPillState.CONNECTED, deriveCgmStatusPillState(healthy, true, now))
        assertEquals(
            CgmStatusPillState.SIGNAL_LOSS,
            deriveCgmStatusPillState(healthy.copy(lastReading = reading(now - CGM_SIGNAL_LOSS_AFTER_MS)), true, now),
        )
        assertEquals(
            CgmStatusPillState.SENSOR_ERROR,
            deriveCgmStatusPillState(healthy.copy(sensor = sensor.copy(state = CgmSensorState.ERROR)), true, now),
        )
        assertEquals(CgmStatusPillState.PAUSED, deriveCgmStatusPillState(healthy.copy(collectorEnabled = false), true, now))
        assertEquals(CgmStatusPillState.NO_ACTIVE_SENSOR, deriveCgmStatusPillState(CgmPersistedState(), false, now))
        assertEquals(
            listOf("Verbunden", "Sensorempfang pausiert", "Signalverlust", "Sensorfehler", "Kein aktiver Sensor verbunden"),
            CgmStatusPillState.entries.map { it.title },
        )
    }

    @Test fun `collector phases do not alter a healthy connected pill`() {
        val intermediatePhases =
            listOf(
                CgmProtocolState.SCANNING,
                CgmProtocolState.CONNECTING,
                CgmProtocolState.AUTHENTICATING,
                CgmProtocolState.BACKFILL,
                CgmProtocolState.RECOVERING,
                CgmProtocolState.WAITING_FOR_NEXT_READING,
            )

        intermediatePhases.forEach { phase ->
            val state =
                CgmPersistedState(
                    sensor = sensor.copy(state = CgmSensorState.ACTIVE),
                    collectorEnabled = true,
                    protocolState = phase,
                    lastReading = reading(now - 5 * 60_000L),
                )
            assertEquals(phase.name, CgmStatusPillState.CONNECTED, deriveCgmStatusPillState(state, true, now))
        }
    }

    @Test fun `waiting between healthy readings is explicit normal operation`() {
        val status =
            deriveCgmUserStatus(
                CgmPersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = CgmProtocolState.WAITING_FOR_NEXT_READING,
                    sessionState = CgmSessionState.WAITING_FOR_NEXT_READING,
                    lastReading = reading(now - 5 * 60_000L),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(CgmUserStatusLevel.OK, status.level)
        assertEquals("Sensor aktiv", status.title)
        assertTrue(status.status.contains("Datenfluss in Ordnung"))
        assertTrue(status.action.contains("Kein Eingriff"))
    }

    @Test fun `stale bonded sensor never appears connected and ownership remains a heuristic`() {
        val status =
            deriveCgmUserStatus(
                CgmPersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = CgmProtocolState.RECOVERING,
                    sessionState = CgmSessionState.RECOVERING,
                    lastReading = reading(now - CGM_SIGNAL_LOSS_AFTER_MS),
                    health = CgmCollectorHealth(sensorAvailability = CgmSensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )
        assertEquals("Signalverlust", status.title)
        assertTrue(status.description.contains("Möglicherweise"))
        assertTrue(status.action.contains("freigeben"))
    }

    @Test fun `single missed window remains automatic recovery before sixteen minutes`() {
        val status =
            deriveCgmUserStatus(
                CgmPersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = CgmProtocolState.RECOVERING,
                    sessionState = CgmSessionState.RECOVERING,
                    lastReading = reading(now - 6 * 60_000L),
                    lastError = CgmCollectorError("G7-BLE-107", true, now, "Kein sendender Dexcom-G7-Sensor gefunden"),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(CgmUserStatusLevel.WORKING, status.level)
        assertEquals("Autom. Wiederverb.", status.title)
        assertTrue(status.action.contains("Nichts zurücksetzen"))
    }

    @Test fun `sixteen minutes without reading becomes signal loss`() {
        val status =
            deriveCgmUserStatus(
                CgmPersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = CgmProtocolState.RECOVERING,
                    sessionState = CgmSessionState.RECOVERING,
                    lastReading = reading(now - CGM_SIGNAL_LOSS_AFTER_MS),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(CgmUserStatusLevel.ATTENTION, status.level)
        assertEquals("Signalverlust", status.title)
    }

    @Test fun `sensor error is surfaced without presenting the last valid value as current`() {
        val status =
            deriveCgmUserStatus(
                CgmPersistedState(
                    sensor = sensor.copy(state = CgmSensorState.ERROR),
                    collectorEnabled = true,
                    protocolState = CgmProtocolState.WAITING_FOR_NEXT_READING,
                    sessionState = CgmSessionState.WAITING_FOR_NEXT_READING,
                    lastReading = reading(now - 5 * 60_000L),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(CgmUserStatusLevel.ATTENTION, status.level)
        assertEquals("Sensorfehler", status.title)
        assertTrue(status.status.contains("Kein gültiger"))
    }
}
