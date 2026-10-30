package app.aapswear.g7watch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class G7AdvertisementWakePolicyTest {
    @Test
    fun `paired sensor advertisement wakes enabled collector`() {
        assertTrue(
            shouldForwardG7AdvertisementWake(
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
            shouldForwardG7AdvertisementWake(
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
            shouldForwardG7AdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 2,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = null,
                nowEpochMs = 1_000_000L,
            ),
        )
        assertFalse(
            shouldForwardG7AdvertisementWake(
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
            shouldForwardG7AdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = firstWake,
                nowEpochMs = firstWake + G7_ADVERTISEMENT_WAKE_THROTTLE_MS - 1L,
            ),
        )
        assertTrue(
            shouldForwardG7AdvertisementWake(
                collectorEnabled = true,
                callbackErrorCode = 0,
                hasMatchingResult = true,
                lastForwardedAtEpochMs = firstWake,
                nowEpochMs = firstWake + G7_ADVERTISEMENT_WAKE_THROTTLE_MS,
            ),
        )
    }

    @Test
    fun `clock rollback does not permanently suppress wake path`() {
        assertTrue(
            shouldForwardG7AdvertisementWake(
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
        assertTrue(shouldForwardG7AdvertisementForSlot(true, false, 0, true, slot, null, slot - 20_000L))
        assertFalse(shouldForwardG7AdvertisementForSlot(true, true, 0, true, slot, null, slot))
        assertFalse(shouldForwardG7AdvertisementForSlot(true, false, 0, true, slot, slot, slot + 1_000L))
        assertFalse(shouldForwardG7AdvertisementForSlot(true, false, 0, true, slot, null, slot + G7_ADVERTISEMENT_SLOT_WINDOW_MS + 1L))
    }
}
