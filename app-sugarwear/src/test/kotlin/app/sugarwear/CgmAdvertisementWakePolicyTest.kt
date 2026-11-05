package app.sugarwear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CgmAdvertisementWakePolicyTest {
    @Test
    fun `paired sensor advertisement wakes enabled collector`() {
        assertTrue(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = null,
                nowEpochMs = 1_000_000L,
            ),
        )
    }

    @Test
    fun `disabled collector never wakes from advertisement`() {
        assertFalse(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = false,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = null,
                nowEpochMs = 1_000_000L,
            ),
        )
    }

    @Test
    fun `scan errors and non matching results never start collector`() {
        assertFalse(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 2,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = null,
                nowEpochMs = 1_000_000L,
            ),
        )
        assertFalse(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = false,
                lastForwardedAtEpochMs = null,
                nowEpochMs = 1_000_000L,
            ),
        )
    }

    @Test
    fun `advertisement burst is throttled but next sensor window is accepted`() {
        val firstWake = 1_000_000L
        assertFalse(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = firstWake,
                nowEpochMs = firstWake + CGM_ADVERTISEMENT_WAKE_THROTTLE_MS - 1L,
            ),
        )
        assertTrue(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = firstWake,
                nowEpochMs = firstWake + CGM_ADVERTISEMENT_WAKE_THROTTLE_MS,
            ),
        )
    }

    @Test
    fun `clock rollback does not permanently suppress wake path`() {
        assertTrue(
            shouldForwardCgmAdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = 2_000_000L,
                nowEpochMs = 1_000_000L,
            ),
        )
    }

    @Test
    fun `one advertisement wake is accepted per expected slot only while idle`() {
        val slot = 1_300_000L
        assertTrue(shouldForwardCgmAdvertisementForSlot(true, false, 0, true, slot, null, slot - 20_000L))
        assertFalse(shouldForwardCgmAdvertisementForSlot(true, true, 0, true, slot, null, slot))
        assertFalse(shouldForwardCgmAdvertisementForSlot(true, false, 0, true, slot, slot, slot + 1_000L))
        assertFalse(shouldForwardCgmAdvertisementForSlot(true, false, 0, true, slot, null, slot + CGM_ADVERTISEMENT_SLOT_WINDOW_MS + 1L))
    }
}
