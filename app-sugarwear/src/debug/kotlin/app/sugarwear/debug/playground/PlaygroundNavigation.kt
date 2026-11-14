package app.sugarwear.debug.playground

/**
 * Tab and category navigation helper for Playground.
 */
object PlaygroundNavigation {
    val categories: List<PlaygroundCategory> = PlaygroundCategory.entries

    fun defaultEntryForCategory(category: PlaygroundCategory): PlaygroundEntry {
        return PlaygroundCatalog.getByCategory(category).firstOrNull()
            ?: PlaygroundCatalog.entries.first()
    }
}
