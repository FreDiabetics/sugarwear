package app.aapswear.g7watch

import org.junit.Assert.*
import org.junit.Test

class G7SettingsViewModelTest {
    @Test fun `setup advances without granting access and completes once`() {
        var completions = 0
        val vm = G7SetupViewModel { completions++ }
        repeat(6) { assertFalse(vm.advance()) }
        assertEquals(6, vm.state.value.step)
        assertTrue(vm.advance())
        assertTrue(vm.advance())
        assertEquals(1, completions)
    }

    @Test fun `system launch is not repeated while already awaiting result`() {
        val vm = G7SetupViewModel {}
        assertTrue(vm.beginSystemRequest())
        assertFalse(vm.beginSystemRequest())
        vm.systemReturned()
        assertTrue(vm.beginSystemRequest())
        assertEquals(0, vm.state.value.step)
    }

    @Test fun `appearance category and mode survive renderer recreation`() {
        val vm = G7SettingsNavigationViewModel()
        vm.selectCategory("Trendpfeil")
        vm.toggleAbout()
        assertEquals("Trendpfeil", vm.state.value.category)
        assertTrue(vm.state.value.aboutExpanded)
        assertTrue(vm.back())
        assertNull(vm.state.value.category)
        assertFalse(vm.back())
    }

    @Test fun `permanent denial selects settings without recording another request`() {
        val recorded = mutableSetOf<String>()
        val vm = G7PermissionRequestViewModel(recorded::addAll)
        assertFalse(vm.shouldRequest(listOf("scan"), setOf("scan"), emptySet()))
        assertTrue(recorded.isEmpty())
        assertTrue(vm.shouldRequest(listOf("scan"), setOf("scan"), setOf("scan")))
        assertEquals(setOf("scan"), recorded)
        assertFalse(vm.shouldRequest(emptyList(), emptySet(), emptySet()))
    }
}
