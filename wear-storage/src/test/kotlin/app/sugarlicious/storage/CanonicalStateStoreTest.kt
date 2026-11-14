package app.sugarlicious.storage

import app.sugarlicious.model.TherapyDisplayState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalStateStoreTest {
    @Test
    fun `commit writes one newer revision to both stores`() =
        runBlocking {
            val backend = FakeBackend(phone = state(3), display = state(3))

            val committed = CanonicalStateStore(backend).commit(state(0, 200))

            assertEquals(4L, committed.canonicalRevision)
            assertEquals(committed, backend.phone)
            assertEquals(committed, backend.display)
        }

    @Test
    fun `restart repairs interruption after phone write before display write`() =
        runBlocking {
            val backend = FakeBackend(phone = state(5, 500), display = state(4, 400))

            val restored = CanonicalStateStore(backend).reconcile()

            assertEquals(state(5, 500), restored)
            assertEquals(restored, backend.phone)
            assertEquals(restored, backend.display)
        }

    @Test
    fun `failed second write never reports a committed result and restart repairs it`() =
        runBlocking {
            val backend = FakeBackend(phone = state(1), display = state(1), failDisplayOnce = true)
            val store = CanonicalStateStore(backend)

            assertTrue(runCatching { store.commit(state(0, 200)) }.exceptionOrNull() is IllegalStateException)
            assertEquals(2L, backend.phone?.canonicalRevision)
            assertEquals(1L, backend.display?.canonicalRevision)

            val restored = CanonicalStateStore(backend).reconcile()
            assertEquals(2L, restored?.canonicalRevision)
            assertEquals(restored, backend.display)
        }

    @Test
    fun `legacy mismatch prefers raw phone input and converges both copies`() =
        runBlocking {
            val phone = state(0, 300)
            val backend = FakeBackend(phone = phone, display = state(0, 200))

            assertEquals(phone, CanonicalStateStore(backend).reconcile())
            assertEquals(phone, backend.display)
        }

    private fun state(revision: Long, receivedAt: Long = 100L) =
        TherapyDisplayState(receivedAtEpochMs = receivedAt, canonicalRevision = revision)

    private class FakeBackend(
        var phone: TherapyDisplayState?,
        var display: TherapyDisplayState?,
        var failDisplayOnce: Boolean = false,
    ) : CanonicalStateBackend {
        override suspend fun readPhone() = phone

        override suspend fun readDisplay() = display

        override suspend fun writePhone(value: TherapyDisplayState) {
            phone = value
        }

        override suspend fun writeDisplay(value: TherapyDisplayState) {
            if (failDisplayOnce) {
                failDisplayOnce = false
                error("injected display write failure")
            }
            display = value
        }
    }
}
