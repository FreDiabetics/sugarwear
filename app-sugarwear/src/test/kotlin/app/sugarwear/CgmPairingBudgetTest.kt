package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmPairingBudgetTest {
    private val setup = CgmPersistedState(collectorEnabled = true, sensor = CgmSensor("setup"), pairingDeadlineEpochMs = 1_020L)

    @Test fun `candidate found just before deadline cannot finish authentication after it`() =
        runBlocking {
            var authenticated = false
            val result =
                runCatching {
                    withinCgmInitialPairingBudget(setup, 1_000L) {
                        delay(80L)
                        authenticated = true
                    }
                }
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
            assertFalse(authenticated)
        }

    @Test fun `expired setup does not start GATT work`() =
        runBlocking {
            var started = false
            val result = runCatching { withinCgmInitialPairingBudget(setup, 1_020L) { started = true } }
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
            assertFalse(started)
        }

    @Test fun `authentication without a validated live value remains inside the setup deadline`() =
        runBlocking {
            val authenticated =
                setup.copy(
                    sensor = CgmSensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"),
                    authenticationState = CgmAuthenticationState.AUTHENTICATED,
                )
            var received = false
            val result =
                runCatching {
                    withinCgmInitialPairingBudget(authenticated, 2_000L) {
                        received = true
                    }
                }
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
            assertFalse(received)
        }

    @Test fun `validated live session is not timed out by an old setup deadline`() =
        runBlocking {
            val paired = establishedSession()
            var received = false
            withinCgmInitialPairingBudget(paired, 2_000L) {
                delay(30L)
                received = true
            }
            assertTrue(received)
        }

    @Test fun `late authentication and key callbacks are rejected even before timeout timer dispatches`() {
        var committed = false
        val result =
            runCatching {
                requireCgmInitialPairingBudget(setup, 1_020L)
                committed = true
            }
        assertFalse(committed)
        val error = result.exceptionOrNull() as CgmBleException
        assertTrue(error.errorCode == CGM_PAIRING_TIMEOUT_ERROR_CODE && !error.recoverable)
        val authenticated =
            setup.copy(
                sensor = CgmSensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"),
                authenticationState = CgmAuthenticationState.AUTHENTICATED,
            )
        assertTrue(runCatching { requireCgmInitialPairingBudget(authenticated, 2_000L) }.exceptionOrNull() is CgmBleException)
        requireCgmInitialPairingBudget(establishedSession(), 2_000L)
    }

    private fun establishedSession(): CgmPersistedState =
        setup.copy(
            sensor = CgmSensor("setup", "setup", deviceAddress = "AA:BB:CC:DD:EE:01"),
            authenticationState = CgmAuthenticationState.AUTHENTICATED,
            lastReading =
                CgmReading(
                    id = "live",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "setup",
                    sessionId = "setup",
                    glucoseMgDl = 123.0,
                    timestampEpochMs = 1_500L,
                    receivedAtEpochMs = 1_500L,
                    status = CgmReadingStatus.VALID,
                ),
        )
}
