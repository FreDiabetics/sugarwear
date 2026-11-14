package app.sugarlicious.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSchemaStoreTest {
    @Test
    fun `migration is monotonic and runs once`() {
        val preferences =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences("schema-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        var calls = 0
        preferences.ensureSettingsSchema(4) { from, to ->
            assertEquals(0, from)
            assertEquals(4, to)
            calls++
        }
        preferences.ensureSettingsSchema(4) { _, _ -> calls++ }
        assertEquals(1, calls)
        assertEquals(4, preferences.getInt(SETTINGS_SCHEMA_VERSION_KEY, 0))
    }
}
