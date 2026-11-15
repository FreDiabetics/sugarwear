package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmBondRecoveryControllerTest {

    @Test
    fun `reconnect fails with invalid pairing code`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CgmBondRecoveryController(context)
        val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
        val result = controller.reconnect(candidate, "invalid")
        assertTrue(result is CgmRecoveryResult.Failed)
    }

    @Test
    fun `repair bond fails when user confirmation is false`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CgmBondRecoveryController(context)
        val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
        val result = controller.repairBond(candidate, userConfirmed = false)
        assertTrue(result is CgmRecoveryResult.Failed)
    }
}
