package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DiagnosticEvent
import app.sugarlicious.model.DiagnosticSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class DiagnosticBundleExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `bundle contains structured ledgers and excludes secrets`() {
        context
            .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            .edit()
            .putString("nightscoutToken", "do-not-export")
            .putBoolean("graphEnabled", true)
            .commit()
        val file =
            DiagnosticBundleExporter.create(
                context,
                listOf(
                    DiagnosticEvent("1", 1000L, "MOBILE", "RESOLVER", "RESOLVER-200", DiagnosticSeverity.INFO, "accepted"),
                    DiagnosticEvent("2", 2000L, "WATCH", "G7", "G7-BLE-133", DiagnosticSeverity.WARNING, "retry"),
                ),
            )

        ZipFile(file).use { zip ->
            assertTrue(zip.getEntry("resolver_events.jsonl") != null)
            assertTrue(zip.getEntry("collector_events.jsonl") != null)
            val settings = zip.getInputStream(zip.getEntry("settings_redacted.json")).bufferedReader().readText()
            assertTrue(settings.contains("graphEnabled"))
            assertFalse(settings.contains("do-not-export"))
            assertFalse(settings.contains("nightscoutToken"))
        }
    }
}
