package app.sugarwear

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CgmPreferenceWritesTest {
    @Test fun `successful synchronous cleanup removes persisted records`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("cleanup-test", Context.MODE_PRIVATE)
        preferences.edit().putString("record", "value").commit()
        preferences.clearSynchronously("Cleanup failed")
        assertFalse(preferences.contains("record"))
    }

    @Test(expected = IllegalStateException::class)
    fun `failed disk commit cannot acknowledge cleanup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("cleanup-test", Context.MODE_PRIVATE)
        val failingPreferences =
            object : SharedPreferences by preferences {
                override fun edit(): SharedPreferences.Editor =
                    object : SharedPreferences.Editor by preferences.edit() {
                        override fun commit(): Boolean = false
                    }
            }
        failingPreferences.clearSynchronously("Cleanup failed")
    }
}
