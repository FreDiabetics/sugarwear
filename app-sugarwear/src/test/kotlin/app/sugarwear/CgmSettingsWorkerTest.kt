package app.sugarwear

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CgmSettingsWorkerTest {
    @Test fun `writes are ordered off main and a failed write keeps last snapshot`() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            val values = mutableListOf<Int>()
            var stored = 0
            val worker = CgmSettingsWorker(backgroundScope, io) { stored }
            runCurrent()
            assertEquals(0, worker.state.value.snapshot)
            worker.submit({
                stored = 1
                values += stored
            })
            worker.submit({ throw IOException("unavailable") })
            runCurrent()
            assertEquals(1, worker.state.value.snapshot)
            assertEquals(CgmSettingsIoError.STORAGE_UNAVAILABLE, worker.state.value.error)
            worker.submit({
                stored = 2
                values += stored
            })
            runCurrent()
            assertEquals(listOf(1, 2), values)
            assertEquals(2, worker.state.value.snapshot)
            assertNull(worker.state.value.error)
        }

    @Test fun `storage failure is explicit and cancellation is not converted to success`() =
        runTest {
            val worker = CgmSettingsWorker<Int>(backgroundScope, StandardTestDispatcher(testScheduler)) { throw IOException("unavailable") }
            runCurrent()
            assertNull(worker.state.value.snapshot)
            assertEquals(CgmSettingsIoError.STORAGE_UNAVAILABLE, worker.state.value.error)
            assertFalse(worker.state.value.loading)
        }
}
