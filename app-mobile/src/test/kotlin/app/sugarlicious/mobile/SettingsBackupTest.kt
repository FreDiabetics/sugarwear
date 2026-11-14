package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exportedPreferenceFiles =
        listOf(
            "dashboard_ui",
            "complication_setup",
            "sugarlicious_watchface_presets",
        )

    @Before
    fun setUp() = clearPreferences()

    @After
    fun tearDown() = clearPreferences()

    @Test
    fun `backup round trip preserves supported values and excludes private runtime data`() {
        context
            .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", true)
            .putInt("hours", 3)
            .putLong("timestamp", 123456789L)
            .putFloat("opacity", 0.45f)
            .putString("themeMode", "DARK")
            .putStringSet("visible", setOf("cgm", "iob"))
            .commit()
        context
            .getSharedPreferences("complication_setup", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_ids", "101,202")
            .commit()
        context
            .getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
            .edit()
            .putString("private_error", "must-not-leave-device")
            .commit()
        context
            .getSharedPreferences("nightscout_treatment_secret", Context.MODE_PRIVATE)
            .edit()
            .putString("value", "encrypted-nightscout-secret-must-not-leave-device")
            .commit()

        val output = ByteArrayOutputStream()
        SettingsBackup.write(context, output, exportedAtEpochMs = 42L)
        val document = output.toString(Charsets.UTF_8.name())

        assertTrue(document.contains("sugarlicious-settings"))
        assertFalse(document.contains("must-not-leave-device"))
        assertFalse(document.contains("encrypted-nightscout-secret"))
        clearPreferences()

        val result = SettingsBackup.restore(context, ByteArrayInputStream(output.toByteArray()))
        val restored = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)

        assertEquals(3, result.preferenceFileCount)
        assertEquals(7, result.valueCount)
        assertTrue(restored.getBoolean("enabled", false))
        assertEquals(3, restored.getInt("hours", 0))
        assertEquals(123456789L, restored.getLong("timestamp", 0L))
        assertEquals(0.45f, restored.getFloat("opacity", 0f), 0.0001f)
        assertEquals("DARK", restored.getString("themeMode", null))
        assertEquals(setOf("cgm", "iob"), restored.getStringSet("visible", emptySet()))
        assertEquals(
            "101,202",
            context
                .getSharedPreferences("complication_setup", Context.MODE_PRIVATE)
                .getString("selected_ids", null),
        )
    }

    @Test
    fun `invalid document is rejected before existing preferences change`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        preferences.edit().putString("themeMode", "LIGHT").commit()

        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.restore(
                context,
                ByteArrayInputStream("{\"format\":\"not-sugarlicious\",\"version\":1}".toByteArray()),
            )
        }

        assertEquals("LIGHT", preferences.getString("themeMode", null))
    }

    @Test
    fun `widget color overrides are included in the bounded settings backup`() {
        val chosen = 0xFF1234AB.toInt()
        WidgetColorStore.save(context, WidgetColorRole.TREND, chosen)
        val output = ByteArrayOutputStream()

        SettingsBackup.write(context, output, exportedAtEpochMs = 77L)
        clearPreferences()
        SettingsBackup.restore(context, ByteArrayInputStream(output.toByteArray()))

        assertEquals(chosen, WidgetColorStore.load(context).argb(WidgetColorRole.TREND))
        assertTrue(WidgetColorStore.hasOverride(context, WidgetColorRole.TREND))
    }

    private fun clearPreferences() {
        (
            exportedPreferenceFiles +
                listOf("diagnostics", "nightscout_treatment_secret", "nightscout_treatment_history", "nightscout_treatment_config")
        ).forEach { name ->
            context
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
