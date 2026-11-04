package app.aapswear.g7watch

import android.app.Activity
import android.os.Looper
import android.view.View
import org.robolectric.Shadows

/** Attach Compose, drain Main and allow the real IO worker to publish its canonical snapshot. */
internal fun awaitG7SettingsUi(activity: Activity, ready: () -> Boolean) {
    val deadline =
        System.nanoTime() +
            java.util.concurrent.TimeUnit.SECONDS
                .toNanos(5)
    while (System.nanoTime() < deadline) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        activity.window.decorView.apply {
            measure(View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(454, View.MeasureSpec.EXACTLY))
            layout(0, 0, 454, 454)
        }
        if (ready()) return
        Thread.sleep(2)
    }
    throw AssertionError("Settings UI did not reach the expected canonical state")
}
