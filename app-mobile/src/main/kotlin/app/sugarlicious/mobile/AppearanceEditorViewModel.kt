package app.sugarlicious.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.sugarlicious.model.ArgbColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class AppearanceNumberEditorState(
    val open: Boolean = false,
    val text: String = "",
)

@dagger.hilt.android.lifecycle.HiltViewModel
internal class AppearanceNumberEditorViewModel
    @javax.inject.Inject
    constructor() : ViewModel() {
        private val mutableState = MutableStateFlow(AppearanceNumberEditorState())
        val uiState = mutableState.asStateFlow()

        fun open(value: Float) {
            mutableState.value = AppearanceNumberEditorState(true, value.toString().trimEnd('0').trimEnd('.'))
        }

        fun enter(text: String) {
            mutableState.value = uiState.value.copy(text = text)
        }

        fun dismiss() {
            mutableState.value = uiState.value.copy(open = false)
        }

        fun confirm(range: ClosedFloatingPointRange<Float>): Float? {
            val value =
                uiState.value.text
                    .replace(',', '.')
                    .toFloatOrNull()
                    ?.takeIf { it.isFinite() && it in range } ?: return null
            dismiss()
            return value
        }
    }

internal data class AppearanceColorEditorState(
    val openingArgb: Int,
    val selected: WidgetPickerColor,
    val hex: String,
    val spectrum: Boolean = false,
    val active: Boolean = true,
) {
    val parsedHex: Int? get() = ArgbColor.parse(hex)
}

@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = AppearanceColorEditorViewModel.Factory::class)
internal class AppearanceColorEditorViewModel
    @dagger.assisted.AssistedInject
    constructor(
        @dagger.assisted.Assisted initialArgb: Int,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(initial(initialArgb))
        val uiState = mutableState.asStateFlow()

        @dagger.assisted.AssistedFactory
        interface Factory {
            fun create(initialArgb: Int): AppearanceColorEditorViewModel
        }

        private var externalArgb = initialArgb

        fun open(argb: Int) {
            if (!uiState.value.active) {
                externalArgb = argb
                mutableState.value = initial(argb)
            }
        }

        fun close() {
            mutableState.value = uiState.value.copy(active = false)
        }

        fun update(value: WidgetPickerColor) {
            mutableState.value = uiState.value.copy(selected = value, hex = ArgbColor.format(value.argb))
        }

        fun setSpectrum(value: Boolean) {
            mutableState.value = uiState.value.copy(spectrum = value)
        }

        fun enterHex(value: String) {
            val hex =
                value
                    .trim()
                    .uppercase()
                    .let { if (it.startsWith("#")) it else "#$it" }
                    .take(9)
            val color = ArgbColor.parse(hex)
            mutableState.value = uiState.value.copy(hex = hex, selected = color?.let(WidgetPickerColor::fromArgb) ?: uiState.value.selected)
        }

        /** Async reset/copy results update the draft while the opening preview remains fixed. */
        fun syncExternal(argb: Int) {
            if (argb == externalArgb) return
            externalArgb = argb
            if (argb != uiState.value.selected.argb) update(WidgetPickerColor.fromArgb(argb))
        }

        private fun initial(argb: Int) = AppearanceColorEditorState(argb, WidgetPickerColor.fromArgb(argb), ArgbColor.format(argb))
    }

/** Uses the Hilt owner factory and preserves a separate retained draft per opening. */
internal fun injectedViewModelProvider(
    owner: androidx.lifecycle.ViewModelStoreOwner,
    configure: (androidx.lifecycle.viewmodel.CreationExtras) -> androidx.lifecycle.viewmodel.CreationExtras = { it },
): ViewModelProvider {
    val defaults = owner as androidx.lifecycle.HasDefaultViewModelProviderFactory
    return ViewModelProvider.create(owner, defaults.defaultViewModelProviderFactory, configure(defaults.defaultViewModelCreationExtras))
}

@Composable
internal fun <T : ViewModel> rememberAppearanceEditorModel(
    key: String,
    type: Class<T>,
    configure: (androidx.lifecycle.viewmodel.CreationExtras) -> androidx.lifecycle.viewmodel.CreationExtras = { it },
): T {
    val context = LocalContext.current
    val owner = appearanceViewModelStoreOwner(LocalView.current, context)
    val identity =
        rememberSaveable(key) {
            java.util.UUID
                .randomUUID()
                .toString()
        }
    return remember(owner, key, identity) { injectedViewModelProvider(owner, configure)["$key:$identity", type] }
}
