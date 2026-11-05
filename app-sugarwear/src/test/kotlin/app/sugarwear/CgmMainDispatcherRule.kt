package app.sugarwear

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Shadows

/** Robolectric replaces its Application/Looper per test; the process-wide coroutine Main must follow it. */
@OptIn(ExperimentalCoroutinesApi::class)
class CgmMainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher("G7RobolectricMain"))
    }

    override fun finished(description: Description) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }
}
