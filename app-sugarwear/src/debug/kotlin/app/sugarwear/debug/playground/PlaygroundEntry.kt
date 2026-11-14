package app.sugarwear.debug.playground

/**
 * Type-safe representation of an entry in the SugarWear UI Playground catalog.
 */
enum class PlaygroundCategory(val label: String) {
    HOME("Hauptübersicht"),
    GRAPH("Glukosegraph"),
    ALARM("Alarmbildschirme"),
    NOTIFICATION("System-Notifications"),
    TILE("Wear OS Tiles"),
    COMPONENT("Einzelkomponenten"),
}

enum class RenderingType {
    COMPOSE,
    CANVAS_VIEW,
    SYSTEM_NOTIFICATION,
    PROTOLAYOUT_TILE,
}

data class PlaygroundEntry(
    val id: String,
    val title: String,
    val description: String,
    val category: PlaygroundCategory,
    val renderingType: RenderingType,
    val supportedScenarios: List<String> = emptyList(),
    val supportsLiveMode: Boolean = true,
)
