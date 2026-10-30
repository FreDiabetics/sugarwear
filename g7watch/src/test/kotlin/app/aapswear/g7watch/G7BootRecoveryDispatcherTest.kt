package app.aapswear.g7watch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class G7ReceiverWorkDispatcherTest {
    @Test fun `ordered receiver launcher preserves broadcast delivery order`() {
        val launcher = G7OrderedWorkLauncher("test-g7-receiver-work")
        val completed = CountDownLatch(2)
        val order = mutableListOf<String>()

        try {
            launcher.launch {
                order += "older"
                completed.countDown()
            }
            launcher.launch {
                order += "newer"
                completed.countDown()
            }

            assertTrue(completed.await(1, TimeUnit.SECONDS))
            assertEquals(listOf("older", "newer"), order)
        } finally {
            launcher.close()
        }
    }

    @Test fun `application receiver dispatchers serialize different recovery entry points`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        G7ReceiverWork
            .dispatcher { _, _ ->
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }.dispatch(context, "source") {}
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))

        G7ReceiverWork
            .dispatcher { _, _ -> secondEntered.countDown() }
            .dispatch(context, "watchdog") {}
        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
    }

    @Test fun `dispatchers sharing a recovery lock cannot mutate collector state concurrently`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executors.newFixedThreadPool(2)
        val recoveryLock = Any()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val dispatcher = { action: String ->
            G7ReceiverWorkDispatcher(
                launch = { block -> executor.execute(block) },
                recover = { _, _ ->
                    if (action == "first") {
                        firstEntered.countDown()
                        releaseFirst.await(2, TimeUnit.SECONDS)
                    } else {
                        secondEntered.countDown()
                    }
                },
                recoveryLock = recoveryLock,
            )
        }

        try {
            dispatcher("first").dispatch(context, "first") {}
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS))
            dispatcher("second").dispatch(context, "second") {}
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

            releaseFirst.countDown()
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun `boot recovery is deferred until the background dispatcher runs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queued = ArrayDeque<() -> Unit>()
        var recoveryRan = false
        var finished = false
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = queued::addLast,
                recover = { _, action ->
                    assertEquals(Intent.ACTION_BOOT_COMPLETED, action)
                    recoveryRan = true
                },
            )

        dispatcher.dispatch(context, Intent.ACTION_BOOT_COMPLETED) { finished = true }

        assertFalse(recoveryRan)
        assertFalse(finished)
        assertEquals(1, queued.size)

        queued.removeFirst().invoke()

        assertTrue(recoveryRan)
        assertTrue(finished)
    }

    @Test fun `handoff is acquired synchronously before alarm work is queued`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<String>()
        val queued = ArrayDeque<() -> Unit>()
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = { block ->
                    events += "queued"
                    queued.addLast(block)
                },
                recover = { _, _ -> events += "recovered" },
            )

        dispatcher.dispatch(
            context,
            "alarm",
            onBeforeLaunch = { events += "handoff" },
            onLaunchFailure = { events += "released" },
            onFinished = { events += "finished" },
        )

        assertEquals(listOf("handoff", "queued"), events)
        queued.removeFirst().invoke()
        assertEquals(listOf("handoff", "queued", "recovered", "finished"), events)
    }

    @Test fun `pending broadcast is always finished when recovery fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queued = ArrayDeque<() -> Unit>()
        var finished = false
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = queued::addLast,
                recover = { _, _ -> error("broken recovery") },
            )

        dispatcher.dispatch(context, Intent.ACTION_MY_PACKAGE_REPLACED) { finished = true }
        runCatching { queued.removeFirst().invoke() }

        assertTrue(finished)
    }

    @Test fun `receiver deadline finishes pending result exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val queued = ArrayDeque<() -> Unit>()
        var timeout: (() -> Unit)? = null
        var finishes = 0
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = queued::addLast,
                recover = { _, _ -> },
                scheduleTimeout = { _, block -> timeout = block },
            )

        dispatcher.dispatch(context, "alarm", onFinished = { finishes += 1 })
        timeout!!.invoke()
        queued.removeFirst().invoke()

        assertEquals(1, finishes)
    }

    @Test fun `receiver setup failure finishes pending result exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var timeout: (() -> Unit)? = null
        var launchFailures = 0
        var finishes = 0
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = { error("must not launch") },
                recover = { _, _ -> },
                scheduleTimeout = { _, block -> timeout = block },
            )

        runCatching {
            dispatcher.dispatch(
                context,
                "alarm",
                onBeforeLaunch = { error("setup failed") },
                onLaunchFailure = { launchFailures += 1 },
                onFinished = { finishes += 1 },
            )
        }
        timeout!!.invoke()

        assertEquals(1, launchFailures)
        assertEquals(1, finishes)
    }

    @Test fun `receiver launch failure finishes pending result exactly once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var timeout: (() -> Unit)? = null
        var launchFailures = 0
        var finishes = 0
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = { error("queue failed") },
                recover = { _, _ -> },
                scheduleTimeout = { _, block -> timeout = block },
            )

        runCatching {
            dispatcher.dispatch(
                context,
                "alarm",
                onLaunchFailure = { launchFailures += 1 },
                onFinished = { finishes += 1 },
            )
        }
        timeout!!.invoke()

        assertEquals(1, launchFailures)
        assertEquals(1, finishes)
    }

    @Test fun `receiver cleanup failure cannot hide launch failure or skip finish`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var finishes = 0
        val dispatcher =
            G7ReceiverWorkDispatcher(
                launch = { error("queue failed") },
                recover = { _, _ -> },
                scheduleTimeout = { _, _ -> },
            )

        val failure =
            runCatching {
                dispatcher.dispatch(
                    context,
                    "alarm",
                    onLaunchFailure = { error("cleanup failed") },
                    onFinished = { finishes += 1 },
                )
            }.exceptionOrNull()!!

        assertEquals("queue failed", failure.message)
        assertEquals("cleanup failed", failure.suppressed.single().message)
        assertEquals(1, finishes)
    }
}
