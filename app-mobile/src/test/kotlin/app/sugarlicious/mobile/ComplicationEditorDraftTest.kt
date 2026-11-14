package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationEditorDraftTest {
    @Test
    fun `choosing another type is pending until saved and reverting clears changes`() {
        val draft = ComplicationEditorDraft(11, ComplicationAppearanceSettings())
        assertFalse(draft.hasChanges)
        val edited = draft.copy(variantId = 12)
        assertTrue(edited.hasChanges)
        assertEquals(11, edited.initialVariantId)
        assertFalse(edited.copy(variantId = 11).hasChanges)
    }

    @Test
    fun `appearance edits do not mutate the baseline and reverting clears changes`() {
        val original = ComplicationAppearanceSettings(trendOffsetXPercent = 7)
        val draft = ComplicationEditorDraft(11, original)
        val edited = draft.copy(appearance = original.copy(trendOffsetXPercent = 15))
        assertTrue(edited.hasChanges)
        assertEquals(7, edited.initialAppearance.trendOffsetXPercent)
        assertFalse(edited.copy(appearance = original).hasChanges)
    }
}
