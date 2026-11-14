package app.sugarwear.debug.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

/**
 * Debug Activity launching the SugarWear Playground visual development environment.
 * Excluded from release builds; operates independently of real active sensor connections.
 */
class PlaygroundActivity : ComponentActivity() {

    private val viewModel by viewModels<PlaygroundViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlaygroundScreen(viewModel = viewModel)
        }
    }
}
