package app.sugarwear.debug.playground.demo

/**
 * Catalog of available demo scenarios.
 */
object DemoScenarioCatalog {
    val allScenarios: List<DemoScenario> = DemoScenario.entries

    fun findById(id: String): DemoScenario = allScenarios.firstOrNull { it.id == id } ?: DemoScenario.STABLE
}
