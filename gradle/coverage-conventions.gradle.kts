// Coverage is limited to JVM tests; APKs and code-free WFF modules are untouched.
subprojects {
    val coveredProject = this
    if (!path.startsWith(":watchfaces")) {
        pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
            rootProject.dependencies.add(
                "kover",
                rootProject.dependencies.project(mapOf("path" to coveredProject.path)),
            )
        }
        listOf(
            "org.jetbrains.kotlin.jvm",
            "org.jetbrains.kotlin.android",
            "com.android.application",
            "com.android.library",
        ).forEach { pluginId ->
            pluginManager.withPlugin(pluginId) {
                coveredProject.pluginManager.apply("org.jetbrains.kotlinx.kover")
            }
        }
    }
}