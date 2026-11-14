package app.sugarlicious.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BootWidgetRestoreTest {
    @Test fun `incomplete widget on package replacement reports failure without escaping receiver`() =
        runTest {
            val error = IllegalStateException("Widget did not produce a completed bitmap")
            var read = false
            var reported: Exception? = null
            restoreBootWidgets({ read = true }, { throw error }, { reported = it })
            assertTrue(read)
            assertSame(error, reported)
        }

    @Test fun `snapshot failure prevents publication and cancellation remains cancellation`() =
        runTest {
            var updates = 0
            var failures = 0
            restoreBootWidgets({ error("read failed") }, { updates++ }, { failures++ })
            assertEquals(0, updates)
            assertEquals(1, failures)
            val cancellation = CancellationException("stopped")
            try {
                restoreBootWidgets({}, { throw cancellation }, { failures++ })
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancellation, actual)
            }
            assertEquals(1, failures)
        }
}
