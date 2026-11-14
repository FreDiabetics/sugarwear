package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.model.DataSourceId
import app.sugarlicious.model.TherapyDisplayState
import app.sugarlicious.protocol.WatchRuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SugarliciousWatchFaceSelectionStoreTest {
    @Test fun `Mobile watch menu only exposes Sugarlicious faces`() {
        org.junit.Assert.assertEquals(listOf(0, 2), watchFaceMenuSections.flatMap { it.second })
        org.junit.Assert.assertFalse(watchFaceMenuSections.any { it.first.contains("SugarWear") })
    }

    @Test
    fun `reading a legacy face during composition never writes preferences`() {
        val preferences = context.getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
        preferences.edit().putInt("watchFaceIndex", 6).commit()
        val before = preferences.all.toMap()
        assertEquals(DIRECT_TO_WATCH_FACE_INDEX, SugarliciousWatchFaceSelectionStore.read(context))
        assertEquals(before, preferences.all)
        SugarliciousWatchFaceSelectionStore.migrate(context)
        assertEquals(2, preferences.getInt("watchFaceCatalogVersion", 0))
        assertEquals(DIRECT_TO_WATCH_FACE_INDEX, SugarliciousWatchFaceSelectionStore.read(context))
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `reserved Digital slot retains its persisted index`() {
        SugarliciousWatchFaceSelectionStore.write(context, DIRECT_TO_WATCH_FACE_INDEX)
        assertEquals(DIRECT_TO_WATCH_FACE_INDEX, SugarliciousWatchFaceSelectionStore.read(context))
    }

    @Test
    fun `Orbit appends without moving existing faces and persists independently`() {
        assertEquals(listOf("ApeX", "Digital", "Orbit"), sugarliciousWatchFaceCards.map { it.name })
        assertEquals(sugarliciousWatchFaceCards.map { it.name }, sugarliciousWatchFaceNames)
        SugarliciousWatchFaceSelectionStore.write(context, 2)
        assertEquals(2, SugarliciousWatchFaceSelectionStore.read(context))
        assertEquals(2, reduceWatchFaceSelection(2, 0, WatchFaceSelectionEvent.CGM_REFRESH))
    }

    @Test
    fun `direct to watch becomes relevant for explicit collector source`() {
        val preferences = DashboardUiPreferences(dataSource = DataSourcePreference.DEXCOM_G7_WATCH)
        assertTrue(SugarliciousWatchFaceSelectionStore.isDirectToWatchRelevant(context, null, preferences))
    }

    @Test
    fun `legacy collector source preference is ignored by AndroidAPS-only Mobile policy`() {
        context
            .getSharedPreferences("dashboard_ui", Context.MODE_PRIVATE)
            .edit()
            .putString("dataSource", DataSourcePreference.DEXCOM_G7_WATCH.name)
            .commit()

        assertFalse(SugarliciousWatchFaceSelectionStore.isDirectToWatchRelevant(context, null))
    }

    @Test
    fun `direct to watch becomes relevant for canonical watch direct state`() {
        val state = TherapyDisplayState(source = DataSourceId.DEXCOM_G7_WATCH, receivedAtEpochMs = 1L)
        assertTrue(
            SugarliciousWatchFaceSelectionStore.isDirectToWatchRelevant(
                context,
                state,
                DashboardUiPreferences(),
            ),
        )
    }

    @Test
    fun `direct to watch is not marked relevant without setup or collector state`() {
        assertFalse(
            SugarliciousWatchFaceSelectionStore.isDirectToWatchRelevant(
                context,
                null,
                DashboardUiPreferences(),
            ),
        )
    }

    @Test
    fun `direct to watch cannot be selected before collector is relevant`() {
        assertFalse(
            SugarliciousWatchFaceSelectionStore.isSelectable(
                DIRECT_TO_WATCH_FACE_INDEX,
                directToWatchRelevant = false,
            ),
        )
        assertTrue(
            SugarliciousWatchFaceSelectionStore.isSelectable(
                DIRECT_TO_WATCH_FACE_INDEX,
                directToWatchRelevant = true,
            ),
        )
    }

    @Test
    fun `unavailable saved direct to watch falls back to legacy selectable face`() {
        assertEquals(
            0,
            SugarliciousWatchFaceSelectionStore.resolveSelectableFallback(
                savedFaceIndex = DIRECT_TO_WATCH_FACE_INDEX,
                legacyFallback = 0,
                directToWatchRelevant = false,
            ),
        )
        assertEquals(
            DIRECT_TO_WATCH_FACE_INDEX,
            SugarliciousWatchFaceSelectionStore.resolveSelectableFallback(
                savedFaceIndex = DIRECT_TO_WATCH_FACE_INDEX,
                legacyFallback = 0,
                directToWatchRelevant = true,
            ),
        )
    }

    @Test
    fun `one hundred cgm collector and data layer refreshes cannot change selected face`() {
        val selectedFace = 0
        SugarliciousWatchFaceSelectionStore.write(context, selectedFace)
        var reduced = selectedFace

        repeat(100) { update ->
            val event =
                when (update % 3) {
                    0 -> WatchFaceSelectionEvent.CGM_REFRESH
                    1 -> WatchFaceSelectionEvent.COLLECTOR_REFRESH
                    else -> WatchFaceSelectionEvent.DATA_LAYER_REFRESH
                }
            reduced = reduceWatchFaceSelection(reduced, update % sugarliciousWatchFaceCards.size, event)
            WatchRuntimeStatusStore.save(
                context,
                WatchRuntimeStatus(
                    activeSugarliciousFaceIndex = update % sugarliciousWatchFaceCards.size,
                    activeComplicationIds = listOf(update),
                    sentAtEpochMs = update.toLong(),
                ),
            )
        }

        assertEquals(selectedFace, reduced)
        assertEquals(selectedFace, SugarliciousWatchFaceSelectionStore.read(context))
    }
}
