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
    fun `reconnect fails with invalid pairing code`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val controller = CgmBondRecoveryController(context)
            val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
            val result = controller.reconnect(candidate, "invalid")
            assertTrue(result is CgmRecoveryResult.Failed)
        }

    @Test
    fun `repair bond fails when user confirmation is false`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val controller = CgmBondRecoveryController(context)
            val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
            val result = controller.repairBond(candidate, userConfirmed = false)
            assertTrue(result is CgmRecoveryResult.Failed)
        }

    @Test
    fun `inspection requests permission before accessing Bluetooth`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            org.robolectric.Shadows
                .shadowOf(context as android.app.Application)
                .denyPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
            assertEquals(CgmRecoveryState.PermissionRequired, CgmBondRecoveryController(context).inspect())
        }

    @Test
    fun `repair without Bluetooth permission preserves collector ownership`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            org.robolectric.Shadows
                .shadowOf(context as android.app.Application)
                .denyPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
            val store = CgmSensorStateStore(context)
            val before = store.read().copy(collectorEnabled = true)
            store.save(before)
            val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
            val result = CgmBondRecoveryController(context).repairBond(candidate, userConfirmed = true)
            assertTrue(result is CgmRecoveryResult.Failed)
            assertEquals(before, store.read())
        }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    @Config(shadows = [CancelledRecoveryCredentials::class])
    fun `reconnect preserves coroutine cancellation`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CgmSensorStateStore(context).save(
                app.sugarlicious.sensor.protocol
                    .CgmPersistedState(),
            )
            val candidate = CgmBondCandidate("AA:BB:CC:DD:EE:FF", "DXCM1234", isBonded = true)
            CgmBondRecoveryController(context).reconnect(candidate, "1234")
        }
}

@org.robolectric.annotation.Implements(CgmCredentialStore::class, isInAndroidSdk = false)
internal class CancelledRecoveryCredentials {
    @org.robolectric.annotation.Implementation
    fun hasSetupMaterial(): Boolean = throw kotlinx.coroutines.CancellationException("Recovery cancelled")
}
