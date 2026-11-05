package app.sugarwear

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmCompanionConsentLauncherTest {
    @Test fun `late cancellation belongs to its original launch and never cancels a replacement`() {
        val registry = Registry()
        val results = mutableListOf<Pair<Int, Int>>()
        val adapter = CgmCompanionConsentLauncher(registry) { request, result -> results += request to result }
        val sender = PendingIntent.getBroadcast(ApplicationProvider.getApplicationContext<Context>(), 1, Intent("consent-test"), PendingIntent.FLAG_IMMUTABLE).intentSender
        adapter.launch(sender, 101)
        adapter.launch(sender, 102)
        registry.dispatchResult(registry.launches[0], Activity.RESULT_CANCELED, null)
        assertEquals(listOf(101 to Activity.RESULT_CANCELED), results)
        registry.dispatchResult(registry.launches[1], Activity.RESULT_OK, null)
        assertEquals(listOf(101 to Activity.RESULT_CANCELED, 102 to Activity.RESULT_OK), results)
        adapter.close()
    }

    @Test fun `destroyed window cannot deliver a stale result into its replacement`() {
        val registry = Registry()
        val results = mutableListOf<Pair<Int, Int>>()
        val sender = PendingIntent.getBroadcast(ApplicationProvider.getApplicationContext<Context>(), 2, Intent("replacement-test"), PendingIntent.FLAG_IMMUTABLE).intentSender
        val old = CgmCompanionConsentLauncher(registry) { request, result -> results += request to result }
        old.launch(sender, 101)
        old.close()
        val replacement = CgmCompanionConsentLauncher(registry) { request, result -> results += request to result }
        replacement.launch(sender, 101)
        registry.dispatchResult(registry.launches[0], Activity.RESULT_CANCELED, null)
        assertEquals(emptyList<Pair<Int, Int>>(), results)
        registry.dispatchResult(registry.launches[1], Activity.RESULT_OK, null)
        assertEquals(listOf(101 to Activity.RESULT_OK), results)
        replacement.close()
    }

    private class Registry : ActivityResultRegistry() {
        val launches = mutableListOf<Int>()

        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches += requestCode
        }
    }
}
