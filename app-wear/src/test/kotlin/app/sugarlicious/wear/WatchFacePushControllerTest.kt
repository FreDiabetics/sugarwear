package app.sugarlicious.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchFacePushControllerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `active marketplace slot wins over an inactive matching package`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("inactive-match", "app.sugarlicious.watchfacepush.orbit", false),
                ManagedWatchFaceSlot("active-slot", "app.aapswear.watchfacepush.rings", true),
            )

        assertEquals(
            "active-slot",
            selectManagedWatchFaceSlot(slots, "app.sugarlicious.watchfacepush.orbit")?.slotId,
        )
    }

    @Test
    fun `matching package is reused when marketplace has no active face`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("fallback", "app.aapswear.watchfacepush.rings", false),
                ManagedWatchFaceSlot("match", "app.sugarlicious.watchfacepush.orbit", false),
            )

        assertEquals(
            "match",
            selectManagedWatchFaceSlot(slots, "app.sugarlicious.watchfacepush.orbit")?.slotId,
        )
    }

    @Test
    fun `unknown legacy marketplace package still supplies the reusable slot`() {
        val slots =
            listOf(
                ManagedWatchFaceSlot("legacy-slot", "app.aapswear.watchfacepush.old", false),
            )

        assertEquals(
            "legacy-slot",
            selectManagedWatchFaceSlot(slots, "app.sugarlicious.watchfacepush.apex")?.slotId,
        )
    }

    @Test
    fun `successful updates do not imply that the one-shot activation was consumed`() {
        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))

        context
            .getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_applied_at", 1L)
            .commit()

        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))
    }

    @Test
    fun `a migrated activation attempt belongs to the former package`() {
        val preferences = context.getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
        preferences
            .edit()
            .putString("activation_package", "app.aapswear")
            .putBoolean("direct_activation_attempted", true)
            .putLong("last_applied_at", 123L)
            .commit()
        assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))
        assertFalse(preferences.contains("last_applied_at"))
        assertEquals(context.packageName, preferences.getString("activation_package", null))
        preferences.edit().putBoolean("direct_activation_attempted", true).commit()
        assertTrue(SugarliciousWatchFacePush.directActivationWasAttempted(context))
    }

    @Test
    fun `old unscoped activation marker is reset only for acknowledged package migration`() {
        val preferences = context.getSharedPreferences("sugarlicious_watchface_push", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("direct_activation_attempted", true).commit()
        val marker = java.io.File(context.noBackupFilesDir, "product-migration-complete")
        marker.writeText("acknowledged")
        try {
            assertFalse(SugarliciousWatchFacePush.directActivationWasAttempted(context))
        } finally {
            marker.delete()
        }
    }

    @Test
    fun `default watchface is bundled for the system picker`() {
        assertTrue(context.getString(R.string.default_wf_token).isNotBlank())

        context.assets.open("default_watchface.apk").use { apk ->
            assertEquals('P'.code, apk.read())
            assertEquals('K'.code, apk.read())
        }

        val pickerApk = context.assets.open("default_watchface.apk").use { it.readBytes() }
        val selectableApk = context.assets.open("watchfaces/sugarlicious_digital.apk").use { it.readBytes() }
        assertArrayEquals(
            "The picker and the selectable Digital asset must be the same freshly built WFF",
            pickerApk,
            selectableApk,
        )
    }

    @Test
    fun `ApeX Digital and Orbit align with the visible selection cards`() {
        val active = SugarliciousWatchFacePush.activeFaceSpecs

        assertEquals(SUGARLICIOUS_MANAGED_FACE_COUNT, active.size)
        assertEquals(3, active.size)
        assertEquals(
            listOf(
                "app.sugarlicious.watchfacepush.apex",
                "app.sugarlicious.watchfacepush.digital",
                "app.sugarlicious.watchfacepush.orbit",
            ),
            active.map { it.packageName },
        )
        active.forEach { spec ->
            context.assets.open(spec.apkAsset).use { apk ->
                assertEquals('P'.code, apk.read())
                assertEquals('K'.code, apk.read())
            }
            assertTrue(
                context.assets
                    .open(spec.tokenAsset)
                    .bufferedReader()
                    .use { it.readText().isNotBlank() },
            )
        }
    }
}
