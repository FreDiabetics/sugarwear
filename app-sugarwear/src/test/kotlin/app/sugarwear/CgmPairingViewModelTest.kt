package app.sugarwear

import app.sugarlicious.model.DataSourceId
import app.sugarlicious.sensor.protocol.CgmPersistedState
import app.sugarlicious.sensor.protocol.CgmReading
import app.sugarlicious.sensor.protocol.CgmReadingStatus
import app.sugarlicious.sensor.protocol.CgmSensor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CgmPairingViewModelTest {
    @Test fun `restored success retains existing three second dismissal`() =
        runTest {
            val vm = SensorConnectionViewModel(Commands(), scope = backgroundScope)
            vm.open(CgmPairingScreenStep.CONNECTED)
            runCurrent()
            advanceTimeBy(3_000)
            runCurrent()
            assertNull(vm.state.value.step)
        }

    @Test fun `presentation clock stops while window is not visible`() =
        runTest {
            val vm = SensorConnectionViewModel(Commands(), scope = backgroundScope, clock = { testScheduler.currentTime })
            vm.open(CgmPairingScreenStep.CONNECTING, 0L)
            vm.setVisible(true)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(1_000L, vm.state.value.now)
            vm.setVisible(false)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1_000L, vm.state.value.now)
        }

    @Test fun `start is single flight and consent precedes connect`() =
        runTest {
            val commands = Commands()
            val vm = SensorConnectionViewModel(commands, scope = backgroundScope, clock = { testScheduler.currentTime }, sdkInt = 36)
            vm.open()
            vm.start("1234")
            vm.start("1234")
            runCurrent()
            assertEquals(listOf("release"), commands.calls)
            val effect = vm.state.value.effect as CgmPairingEffect.Access
            vm.accessReady(effect.token)
            runCurrent()
            assertEquals(listOf("release", "connect"), commands.calls)
            assertEquals(CgmPairingScreenStep.CONNECTING, vm.state.value.step)
            assertFalse(vm.state.value.startInFlight)
        }

    @Test fun `cancelled consent cannot start collector through late callbacks`() =
        runTest {
            val commands = Commands()
            val vm = SensorConnectionViewModel(commands, scope = backgroundScope)
            vm.open()
            vm.start("1234")
            runCurrent()
            val token = checkNotNull(vm.state.value.effect).token
            vm.cancelPendingAccess()
            vm.accessReady(token)
            vm.accessFailed(token, "late")
            runCurrent()
            assertNull(vm.state.value.step)
            assertEquals(listOf("release"), commands.calls)
            assertNull(vm.state.value.failureMessage)
        }

    @Test fun `platform recreation invalidates old callbacks and retains pending request`() =
        runTest {
            val commands = Commands()
            val vm = SensorConnectionViewModel(commands, scope = backgroundScope)
            vm.open()
            vm.start("1234")
            runCurrent()
            val old = checkNotNull(vm.state.value.effect).token
            vm.platformDetached()
            val current = checkNotNull(vm.state.value.effect).token
            assertNotEquals(old, current)
            vm.accessFailed(old, "old window")
            assertNull(vm.state.value.failureMessage)
            vm.accessReady(current)
            runCurrent()
            assertEquals(listOf("release", "connect"), commands.calls)
        }

    @Test fun `success verifies bond once and closes after exact existing delay`() =
        runTest {
            val vm = SensorConnectionViewModel(Commands(), scope = backgroundScope, clock = { testScheduler.currentTime }, sdkInt = 36)
            vm.open(CgmPairingScreenStep.CONNECTING, 0L)
            vm.sensorChanged(connected())
            val verify = vm.state.value.effect as CgmPairingEffect.VerifyBond
            vm.bondReady(verify.token)
            runCurrent()
            assertEquals(CgmPairingScreenStep.CONNECTED, vm.state.value.step)
            advanceTimeBy(2_999)
            runCurrent()
            assertEquals(CgmPairingScreenStep.CONNECTED, vm.state.value.step)
            advanceTimeBy(1)
            runCurrent()
            assertNull(vm.state.value.step)
        }

    @Test fun `access denial returns to code without running connect`() =
        runTest {
            val commands = Commands()
            val vm = SensorConnectionViewModel(commands, scope = backgroundScope)
            vm.open()
            vm.start("1234")
            runCurrent()
            vm.accessFailed(checkNotNull(vm.state.value.effect).token, "denied")
            assertEquals(CgmPairingScreenStep.ENTER_CODE, vm.state.value.step)
            assertEquals("denied", vm.state.value.failureMessage)
            assertEquals(listOf("release"), commands.calls)
        }

    @Test fun `unlink is single flight and result is explicit`() =
        runTest {
            val commands = Commands()
            val vm = SensorConnectionViewModel(commands, scope = backgroundScope)
            vm.unlink()
            vm.unlink()
            runCurrent()
            vm.accessReady(checkNotNull(vm.state.value.effect).token)
            runCurrent()
            assertEquals(listOf("release", "unlink"), commands.calls)
            assertFalse(vm.state.value.unlinkInFlight)
            assertFalse(vm.state.value.unlinkFailed)
        }

    private class Commands : CgmPairingCommands {
        val calls = mutableListOf<String>()

        override suspend fun releaseOwnership() {
            calls += "release"
        }

        override suspend fun connect(code: String) {
            calls += "connect"
        }

        override suspend fun unlink(): Boolean {
            calls += "unlink"
            return true
        }
    }

    private fun connected() =
        CgmPersistedState(
            sensor = CgmSensor("sensor", deviceAddress = "AA:BB:CC:DD:EE:FF"),
            lastReading =
                CgmReading(
                    id = "sample",
                    source = DataSourceId.DEXCOM_G7_WATCH,
                    sensorId = "sensor",
                    sessionId = "sensor",
                    glucoseMgDl = 120.0,
                    timestampEpochMs = 1,
                    receivedAtEpochMs = 1,
                    status = CgmReadingStatus.VALID,
                ),
        )
}
