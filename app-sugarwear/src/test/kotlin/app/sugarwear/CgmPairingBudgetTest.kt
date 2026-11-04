package app.sugarwear

import app.sugarlicious.sensor.protocol.CgmAuthenticationState
import app.sugarlicious.sensor.protocol.CgmPersistedState
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

    @Test fun `already authenticated session is not timed out by an old setup deadline`() =
        runBlocking {
            val paired = setup.copy(sensor = CgmSensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"), authenticationState = CgmAuthenticationState.AUTHENTICATED)
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
        val paired = setup.copy(sensor = CgmSensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"), authenticationState = CgmAuthenticationState.AUTHENTICATED)
        requireCgmInitialPairingBudget(paired, 2_000L)
    }
}
