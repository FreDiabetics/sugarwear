package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DexcomShareUploadStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("sugarwear_dexcom_share", Context.MODE_PRIVATE)
    private val store = DexcomShareUploadStore(context)
    private val config = DexcomShareUploadConfig("account", "password", "AB12345678", DexcomShareRegion.INTERNATIONAL, 3L)

    @Before fun before() {
        preferences
            .edit()
            .clear()
            .putBoolean("enabled", true)
            .putLong("revision", 3L)
            .commit()
    }

    @After fun after() {
        preferences.edit().clear().commit()
    }

    @Test fun `account setup and existing receiver assignment are reported independently`() {
        preferences
            .edit()
            .putString("encrypted_account", "sealed")
            .putString("encrypted_password", "sealed")
            .commit()
        assertTrue(store.status().configured)
        assertFalse(store.status().hasReceiver)
        preferences.edit().putString("encrypted_receiver", "sealed").commit()
        assertTrue(store.status().hasReceiver)
    }

    @Test fun `upload cursor is session and account isolated`() {
        assertTrue(store.markAccepted(config, "sensor", "session1", 1_800_000_000_000L, 1_800_000_001_000L))
        assertEquals(1_800_000_000_000L, store.cursor(config, "sensor", "session1"))
        assertEquals(0L, store.cursor(config, "sensor", "session2"))
        assertEquals(0L, store.cursor(config.copy(account = "different"), "sensor", "session1"))
        assertEquals(0L, store.cursor(config.copy(receiverSerial = "CD87654321"), "sensor", "session1"))
        assertEquals(0L, store.cursor(config.copy(region = DexcomShareRegion.US), "sensor", "session1"))
    }

    @Test fun `replays and old revision acknowledgements cannot move cursor backwards`() {
        assertTrue(store.markAccepted(config, "sensor", "session", 1_800_000_000_000L, 1_800_000_001_000L))
        assertTrue(store.markAccepted(config, "sensor", "session", 1_799_999_999_000L, 1_800_000_002_000L))
        assertEquals(1_800_000_000_000L, store.cursor(config, "sensor", "session"))
        preferences.edit().putLong("revision", 4L).commit()
        assertFalse(store.markAccepted(config, "sensor", "session", 1_800_000_200_000L, 1_800_000_200_000L))
        assertEquals(1_800_000_000_000L, store.cursor(config.copy(revision = 4L), "sensor", "session"))
    }

    @Test fun `disabling prevents late upload acknowledgement`() {
        preferences.edit().putBoolean("enabled", false).commit()
        assertFalse(store.markAccepted(config, "sensor", "session", 1_800_000_000_000L, 1_800_000_001_000L))
        assertEquals(0L, store.status().lastUploadEpochMs)
    }

    @Test fun `account verification cannot erase receiver upload error`() {
        preferences.edit().putString("error", DexcomShareUploadError.RECEIVER_MISMATCH.name).commit()
        store.markVerified(config, 1_800_000_000_000L)
        assertEquals(DexcomShareUploadError.RECEIVER_MISMATCH, store.status().error)
        assertEquals(1_800_000_000_000L, store.status().lastConnectionEpochMs)
        assertEquals(0L, store.status().lastUploadEpochMs)
    }

    @Test fun `removing an account invalidates old in-flight acceptance even after reenabling`() {
        assertTrue(store.markAccepted(config, "sensor", "session", 1_800_000_000_000L, 1_800_000_001_000L))
        store.clear()
        assertEquals(4L, preferences.getLong("revision", -1L))
        preferences
            .edit()
            .putBoolean("enabled", true)
            .putLong("revision", 5L)
            .commit()
        assertFalse(store.markAccepted(config, "sensor", "session", 1_800_000_300_000L, 1_800_000_301_000L))
        assertEquals(0L, store.status().lastUploadEpochMs)
    }

    @Test fun `failed auth error clears only after later successful login`() {
        store.markError(config, DexcomShareUploadError.AUTHENTICATION)
        assertEquals(DexcomShareUploadError.AUTHENTICATION, store.status().error)
        store.markVerified(config, 1_800_000_000_000L)
        assertEquals(DexcomShareUploadError.NONE, store.status().error)
    }
}
