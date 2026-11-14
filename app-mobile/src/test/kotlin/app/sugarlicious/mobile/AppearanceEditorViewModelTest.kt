package app.sugarlicious.mobile

import androidx.lifecycle.setViewTreeViewModelStoreOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearanceEditorViewModelTest {
    @get:org.junit.Rule val mainDispatcher = MobileMainDispatcherRule()

    @Test fun `numeric entry validates finite range and keeps invalid draft open`() {
        val model = AppearanceNumberEditorViewModel()
        model.open(2.4f)
        model.enter("NaN")
        assertEquals(null, model.confirm(1.5f..6f))
        assertTrue(model.uiState.value.open)
        model.enter("3,25")
        assertEquals(3.25f, model.confirm(1.5f..6f))
        assertFalse(model.uiState.value.open)
    }

    @Test fun `color draft preserves opening preview hue and invalid hex until valid edit`() {
        val model = AppearanceColorEditorViewModel(0xff00ff00.toInt())
        model.enterHex("BAD INPUT")
        assertEquals(null, model.uiState.value.parsedHex)
        assertEquals(0xff00ff00.toInt(), model.uiState.value.selected.argb)
        model.update(
            model.uiState.value.selected
                .spectrum(h = 240f),
        )
        assertEquals(0xff00ff00.toInt(), model.uiState.value.openingArgb)
        assertEquals(240f, model.uiState.value.selected.hue)
        model.syncExternal(0xffff0000.toInt())
        assertEquals(0xffff0000.toInt(), model.uiState.value.selected.argb)
        assertEquals(0xff00ff00.toInt(), model.uiState.value.openingArgb)
    }

    @Test fun `appearance models use inherited host owner when themed context wraps application`() {
        val context =
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
        val themed = android.view.ContextThemeWrapper(context, android.R.style.Theme_Material)
        val parent = android.widget.FrameLayout(themed)
        val child = android.view.View(themed)
        val owner =
            object : androidx.lifecycle.ViewModelStoreOwner {
                override val viewModelStore = androidx.lifecycle.ViewModelStore()
            }
        parent.setViewTreeViewModelStoreOwner(owner)
        parent.addView(child)
        assertSame(owner, appearanceViewModelStoreOwner(child, themed))
        owner.viewModelStore.clear()
    }
}
