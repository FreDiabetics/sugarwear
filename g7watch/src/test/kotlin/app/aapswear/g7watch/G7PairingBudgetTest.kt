package app.aapswear.g7watch

import app.aapswear.g7.G7AuthenticationState
import app.aapswear.g7.G7PersistedState
import app.aapswear.g7.G7Sensor
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7PairingBudgetTest {
    private val setup = G7PersistedState(collectorEnabled = true, sensor = G7Sensor("setup"), pairingDeadlineEpochMs = 1_020L)

    @Test fun `candidate found just before deadline cannot finish authentication after it`() =
        runBlocking {
            var authenticated = false
            val result =
                runCatching {
                    withinG7InitialPairingBudget(setup, 1_000L) {
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
            val result = runCatching { withinG7InitialPairingBudget(setup, 1_020L) { started = true } }
            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
            assertFalse(started)
        }

    @Test fun `already authenticated session is not timed out by an old setup deadline`() =
        runBlocking {
            val paired = setup.copy(sensor = G7Sensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"), authenticationState = G7AuthenticationState.AUTHENTICATED)
            var received = false
            withinG7InitialPairingBudget(paired, 2_000L) {
                delay(30L)
                received = true
            }
            assertTrue(received)
        }

    @Test fun `late authentication and key callbacks are rejected even before timeout timer dispatches`() {
        var committed = false
        val result =
            runCatching {
                requireG7InitialPairingBudget(setup, 1_020L)
                committed = true
            }
        assertFalse(committed)
        val error = result.exceptionOrNull() as G7BleException
        assertTrue(error.errorCode == G7_PAIRING_TIMEOUT_ERROR_CODE && !error.recoverable)
        val paired = setup.copy(sensor = G7Sensor("setup", deviceAddress = "AA:BB:CC:DD:EE:01"), authenticationState = G7AuthenticationState.AUTHENTICATED)
        requireG7InitialPairingBudget(paired, 2_000L)
    }
}
