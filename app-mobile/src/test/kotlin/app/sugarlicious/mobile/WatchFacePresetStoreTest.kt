package app.sugarlicious.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchFacePresetStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context
            .getSharedPreferences("sugarlicious_watchface_presets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `preset list stays aligned with every Sugarlicious watch face`() {
        assertEquals(sugarliciousWatchFaceCards.indices, WatchFacePresetStore.supportedFaceIndices)
        assertEquals(sugarliciousWatchFaceCards.size, WatchFacePresetStore.readAll(context).size)
        assertEquals(List(sugarliciousWatchFaceCards.size) { emptyList<Int>() }, WatchFacePresetStore.readAll(context))
    }

    @Test
    fun `ApeX keeps its own preset`() {
        WatchFacePresetStore.save(context, faceIndex = 0, complicationIds = listOf(1))

        assertEquals(listOf(1), WatchFacePresetStore.read(context, faceIndex = 0))
    }

    @Test
    fun `native slot presets retain every editable slot and repeated providers independently per face`() {
        val slots = context.resources.openRawResource(R.raw.native_apex_slots).use(::readNativeWatchSlots)
        val editable = nativeSlotPreviewEntries(slots).filter(::isNativeSlotEditable)
        val assignments = editable.associate { it.id to eligibleNativeVariants(it).first().id }
        WatchFacePresetStore.saveSlots(context, 0, assignments)
        assertEquals(editable.map { it.id }.toSet(), assignments.keys)
        org.junit.Assert.assertTrue(slots.filterNot(::isNativeSlotEditable).none { it.id in assignments })
        assertEquals(assignments, WatchFacePresetStore.readSlots(context, 0))
        assertEquals(emptyMap<Int, Int>(), WatchFacePresetStore.readSlots(context, 2))
    }

    @Test
    fun `native slot presets reject unknown slots and incompatible graph providers`() {
        val slots = context.resources.openRawResource(R.raw.native_apex_slots).use(::readNativeWatchSlots)
        val outer = slots.first { it.bounds.kind == "BoundingArc" }
        WatchFacePresetStore.saveSlots(context, 0, mapOf(999 to 1, outer.id to app.sugarlicious.model.SugarliciousComplicationIds.GRAPH))
        assertEquals(emptyMap<Int, Int>(), WatchFacePresetStore.readSlots(context, 0))
    }

    @Test
    fun `isolated middle preview uses a padded circular backdrop`() {
        val slot =
            context.resources
                .openRawResource(R.raw.native_apex_slots)
                .use(::readNativeWatchSlots)
                .single { it.id == 4 }
        val backdrop = nativeDynamicPreviewBackdrop(slot, null).getBounds()
        val ink = nativeDynamicPreviewBounds(slot, null)
        assertEquals(backdrop.width, backdrop.height, .01f)
        org.junit.Assert.assertTrue(backdrop.width >= ink.width)
        org.junit.Assert.assertTrue(backdrop.height >= ink.height)
    }
}
