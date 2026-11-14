package app.sugarwear.debug.playground.testing

import android.content.Context
import androidx.wear.tiles.TileService
import app.sugarwear.CgmCollectorTileService
import app.sugarwear.CgmGraphTileService

/**
 * Triggers tile update requests for Wear OS tiles during debug inspection.
 */
object DebugTileController {
    fun requestTileUpdate(context: Context) {
        TileService.getUpdater(context).requestUpdate(CgmCollectorTileService::class.java)
        TileService.getUpdater(context).requestUpdate(CgmGraphTileService::class.java)
    }
}
