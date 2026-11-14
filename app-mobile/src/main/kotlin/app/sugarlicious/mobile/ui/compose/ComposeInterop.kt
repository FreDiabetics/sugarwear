package app.sugarlicious.mobile.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import app.sugarlicious.mobile.ui.theme.SugarliciousTheme

/**
 * Bridge for the gradual View -> Compose migration.
 *
 * Existing screens can keep their current View hierarchy while individual
 * cards/sections are replaced by Compose one at a time.
 */
fun ComposeView.setSugarliciousContent(content: @Composable () -> Unit) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        SugarliciousTheme(content = content)
    }
}
