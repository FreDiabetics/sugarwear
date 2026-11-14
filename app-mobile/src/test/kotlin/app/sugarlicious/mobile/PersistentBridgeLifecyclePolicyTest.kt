package app.sugarlicious.mobile

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentBridgeLifecyclePolicyTest {
    @Test fun `bridge restores after boot`() {
        assertTrue(shouldRestorePersistentBridge(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test fun `bridge restores after app update`() {
        assertTrue(shouldRestorePersistentBridge(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test fun `unrelated broadcasts do not start bridge`() {
        assertFalse(shouldRestorePersistentBridge(Intent.ACTION_SCREEN_ON))
        assertFalse(shouldRestorePersistentBridge(null))
    }
}
