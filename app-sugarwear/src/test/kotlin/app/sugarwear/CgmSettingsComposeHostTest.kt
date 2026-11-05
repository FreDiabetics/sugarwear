package app.sugarwear

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class PreloadedSettingsTestActivity : CgmSettingsComposeActivity() {
    private val loaded = MutableStateFlow("ready before first composition")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindSettingsState(loaded) {
            showSettingsView(TextView(this).apply { text = loaded.value })
        }
        showSettingsView(TextView(this).apply { text = "loading" })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmSettingsComposeHostTest {
    @get:org.junit.Rule val mainDispatcher = CgmMainDispatcherRule()

    @Test fun `preloaded state replaces initial loading view on first composition`() {
        val controller = Robolectric.buildActivity(PreloadedSettingsTestActivity::class.java).setup().visible()
        try {
            val decor = controller.get().window.decorView
            val deadline =
                System.nanoTime() +
                    java.util.concurrent.TimeUnit.SECONDS
                        .toNanos(5)
            while (!hasReadyText(decor) && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
                decor.measure(View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 454, 454)
                Thread.sleep(2)
            }
            assertTrue(hasReadyText(decor))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun hasReadyText(view: View): Boolean =
        (view is TextView && view.text == "ready before first composition") ||
            (view is ViewGroup && (0 until view.childCount).any { hasReadyText(view.getChildAt(it)) })
}
