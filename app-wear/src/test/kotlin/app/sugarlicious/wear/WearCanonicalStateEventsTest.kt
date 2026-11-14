package app.sugarlicious.wear

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WearCanonicalStateEventsTest {
    @Test fun `local G7 reading invalidates open overview without phone state or clock tick`() =
        runBlocking {
            val update =
                async(start = CoroutineStart.UNDISPATCHED) {
                    WearCanonicalStateEvents.updates.first()
                    "refreshed"
                }

            WearCanonicalStateEvents.publishLocalReadingUpdate()

            assertEquals("refreshed", update.await())
        }
}
