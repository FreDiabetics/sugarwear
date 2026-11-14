package app.sugarlicious.mobile

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

/** Keep coroutine Main on the current Robolectric sandbox's real Android Looper. */
@OptIn(ExperimentalCoroutinesApi::class)
class MobileMainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher("MobileRobolectricMain"))
    }

    override fun finished(description: Description) {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }
}
