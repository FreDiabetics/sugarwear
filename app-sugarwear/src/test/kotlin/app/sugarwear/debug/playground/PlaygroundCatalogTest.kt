package app.sugarwear.debug.playground

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaygroundCatalogTest {

    @Test
    fun `catalog registers primary entries for home graph alarms notifications tiles`() {
        assertNotNull(PlaygroundCatalog.findById("entry_home"))
        assertNotNull(PlaygroundCatalog.findById("entry_graph"))
        assertNotNull(PlaygroundCatalog.findById("entry_alarm_low"))
        assertNotNull(PlaygroundCatalog.findById("entry_notification_error"))
        assertNotNull(PlaygroundCatalog.findById("entry_tile_collector"))

        assertEquals(RenderingType.COMPOSE, PlaygroundCatalog.findById("entry_home")?.renderingType)
        assertEquals(RenderingType.CANVAS_VIEW, PlaygroundCatalog.findById("entry_graph")?.renderingType)
    }
}
