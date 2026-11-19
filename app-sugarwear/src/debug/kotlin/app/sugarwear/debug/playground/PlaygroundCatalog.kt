package app.sugarwear.debug.playground

import app.sugarwear.debug.playground.demo.DemoScenarioCatalog

/**
 * Central catalog registering all discovered SugarWear UI screens and components for debug visualization.
 */
object PlaygroundCatalog {
    val entries: List<PlaygroundEntry> =
        listOf(
            PlaygroundEntry(
                id = "entry_home",
                title = "Hauptübersicht",
                description = "Vollständige SugarWear Overview Kachel mit Live/Demo Glukose-Karte",
                category = PlaygroundCategory.HOME,
                renderingType = RenderingType.COMPOSE,
                supportedScenarios = DemoScenarioCatalog.allScenarios.map { it.id },
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_graph",
                title = "Glukosegraph Canvas",
                description = "Interaktiver CgmCollectorGraphView Canvas Renderer mit Live Visual Style Tuning",
                category = PlaygroundCategory.GRAPH,
                renderingType = RenderingType.CANVAS_VIEW,
                supportedScenarios = DemoScenarioCatalog.allScenarios.map { it.id },
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_alarm_low",
                title = "Alarm: Niedrig (Low)",
                description = "Full-screen Alarm overlay for LOW glucose alerts",
                category = PlaygroundCategory.ALARM,
                renderingType = RenderingType.COMPOSE,
                supportedScenarios = listOf("scenario_hypo"),
                supportsLiveMode = false,
            ),
            PlaygroundEntry(
                id = "entry_alarm_high",
                title = "Alarm: Hoch (High)",
                description = "Full-screen Alarm overlay for HIGH glucose alerts",
                category = PlaygroundCategory.ALARM,
                renderingType = RenderingType.COMPOSE,
                supportedScenarios = listOf("scenario_hyper"),
                supportsLiveMode = false,
            ),
            PlaygroundEntry(
                id = "entry_notification_error",
                title = "System-Notification: Fehler",
                description = "High-priority direct-Watch collector error system notification",
                category = PlaygroundCategory.NOTIFICATION,
                renderingType = RenderingType.SYSTEM_NOTIFICATION,
                supportedScenarios = listOf("G7-SIGNAL-LOSS", "G7-AUTH-FAIL"),
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_tile_collector",
                title = "Collector Main Tile",
                description = "ProtoLayout tile displaying current glucose value and status",
                category = PlaygroundCategory.TILE,
                renderingType = RenderingType.PROTOLAYOUT_TILE,
                supportedScenarios = DemoScenarioCatalog.allScenarios.map { it.id },
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_tile_graph",
                title = "Graph Tile",
                description = "ProtoLayout tile rendering bitmap graph history",
                category = PlaygroundCategory.TILE,
                renderingType = RenderingType.PROTOLAYOUT_TILE,
                supportedScenarios = DemoScenarioCatalog.allScenarios.map { it.id },
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_component_card",
                title = "Komponente: Glukose-Karte",
                description = "Isolierte WearGlucoseCard View",
                category = PlaygroundCategory.COMPONENT,
                renderingType = RenderingType.COMPOSE,
                supportedScenarios = DemoScenarioCatalog.allScenarios.map { it.id },
                supportsLiveMode = true,
            ),
            PlaygroundEntry(
                id = "entry_component_dots",
                title = "Komponente: Connection Dots",
                description = "Isolierte CgmConnectionDotsView Animierte Punkte",
                category = PlaygroundCategory.COMPONENT,
                renderingType = RenderingType.CANVAS_VIEW,
                supportedScenarios = emptyList(),
                supportsLiveMode = false,
            ),
        )

    fun findById(id: String): PlaygroundEntry? = entries.firstOrNull { it.id == id }

    fun getByCategory(category: PlaygroundCategory): List<PlaygroundEntry> = entries.filter { it.category == category }
}
