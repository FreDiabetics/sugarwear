package app.aapswear.g7watch

import app.aapswear.g7.CgmReading
import app.aapswear.g7.G7CollectorError
import app.aapswear.g7.G7CollectorHealth
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7ProtocolState
import app.aapswear.g7.G7Sensor
import app.aapswear.g7.G7SensorAvailability
import app.aapswear.g7.G7SensorState
import app.aapswear.g7.G7SessionState
import app.aapswear.model.DataSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class G7UserStatusTest {
    private val now = 2_000_000L
    private val sensor = G7Sensor("sensor", "session", "DXCMZU")

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
            G7PersistedState(
                sensor = sensor.copy(state = G7SensorState.ACTIVE),
                collectorEnabled = true,
                protocolState = G7ProtocolState.RECOVERING,
                lastReading = reading(now - 5 * 60_000L),
            )

        assertEquals(G7StatusPillState.CONNECTED, deriveG7StatusPillState(healthy, true, now))
        assertEquals(
            G7StatusPillState.SIGNAL_LOSS,
            deriveG7StatusPillState(healthy.copy(lastReading = reading(now - G7_SIGNAL_LOSS_AFTER_MS)), true, now),
        )
        assertEquals(
            G7StatusPillState.SENSOR_ERROR,
            deriveG7StatusPillState(healthy.copy(sensor = sensor.copy(state = G7SensorState.ERROR)), true, now),
        )
        assertEquals(G7StatusPillState.PAUSED, deriveG7StatusPillState(healthy.copy(collectorEnabled = false), true, now))
        assertEquals(G7StatusPillState.NO_ACTIVE_SENSOR, deriveG7StatusPillState(G7PersistedState(), false, now))
        assertEquals(
            listOf("Verbunden", "Sensorempfang pausiert", "Signalverlust", "Sensorfehler", "Kein aktiver Sensor verbunden"),
            G7StatusPillState.entries.map { it.title },
        )
    }

    @Test fun `collector phases do not alter a healthy connected pill`() {
        val intermediatePhases =
            listOf(
                G7ProtocolState.SCANNING,
                G7ProtocolState.CONNECTING,
                G7ProtocolState.AUTHENTICATING,
                G7ProtocolState.BACKFILL,
                G7ProtocolState.RECOVERING,
                G7ProtocolState.WAITING_FOR_NEXT_READING,
            )

        intermediatePhases.forEach { phase ->
            val state =
                G7PersistedState(
                    sensor = sensor.copy(state = G7SensorState.ACTIVE),
                    collectorEnabled = true,
                    protocolState = phase,
                    lastReading = reading(now - 5 * 60_000L),
                )
            assertEquals(phase.name, G7StatusPillState.CONNECTED, deriveG7StatusPillState(state, true, now))
        }
    }

    @Test fun `waiting between healthy readings is explicit normal operation`() {
        val status =
            deriveG7UserStatus(
                G7PersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                    sessionState = G7SessionState.WAITING_FOR_NEXT_READING,
                    lastReading = reading(now - 5 * 60_000L),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(G7UserStatusLevel.OK, status.level)
        assertEquals("Sensor aktiv", status.title)
        assertTrue(status.status.contains("Datenfluss in Ordnung"))
        assertTrue(status.action.contains("Kein Eingriff"))
    }

    @Test fun `stale bonded sensor never appears connected and ownership remains a heuristic`() {
        val status =
            deriveG7UserStatus(
                G7PersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = G7ProtocolState.RECOVERING,
                    sessionState = G7SessionState.RECOVERING,
                    lastReading = reading(now - G7_SIGNAL_LOSS_AFTER_MS),
                    health = G7CollectorHealth(sensorAvailability = G7SensorAvailability.POSSIBLY_OWNED_BY_OTHER_COLLECTOR),
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
            deriveG7UserStatus(
                G7PersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = G7ProtocolState.RECOVERING,
                    sessionState = G7SessionState.RECOVERING,
                    lastReading = reading(now - 6 * 60_000L),
                    lastError = G7CollectorError("G7-BLE-107", true, now, "Kein sendender Dexcom-G7-Sensor gefunden"),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(G7UserStatusLevel.WORKING, status.level)
        assertEquals("Autom. Wiederverb.", status.title)
        assertTrue(status.action.contains("Nichts zurücksetzen"))
    }

    @Test fun `sixteen minutes without reading becomes signal loss`() {
        val status =
            deriveG7UserStatus(
                G7PersistedState(
                    sensor = sensor,
                    collectorEnabled = true,
                    protocolState = G7ProtocolState.RECOVERING,
                    sessionState = G7SessionState.RECOVERING,
                    lastReading = reading(now - G7_SIGNAL_LOSS_AFTER_MS),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(G7UserStatusLevel.ATTENTION, status.level)
        assertEquals("Signalverlust", status.title)
    }

    @Test fun `sensor error is surfaced without presenting the last valid value as current`() {
        val status =
            deriveG7UserStatus(
                G7PersistedState(
                    sensor = sensor.copy(state = G7SensorState.ERROR),
                    collectorEnabled = true,
                    protocolState = G7ProtocolState.WAITING_FOR_NEXT_READING,
                    sessionState = G7SessionState.WAITING_FOR_NEXT_READING,
                    lastReading = reading(now - 5 * 60_000L),
                ),
                credentialsPresent = true,
                nowEpochMs = now,
            )

        assertEquals(G7UserStatusLevel.ATTENTION, status.level)
        assertEquals("Sensorfehler", status.title)
        assertTrue(status.status.contains("Kein gültiger"))
    }
}
