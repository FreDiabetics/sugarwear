pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.xgouchet") }
        }
    }
}

rootProject.name = "SugarWear"
val localSharedRoot = file("local.properties").takeIf { it.isFile }?.inputStream()?.use { input ->
    java.util.Properties().apply { load(input) }.getProperty("sharedSourceRoot")
}
val sharedRoot = (providers.gradleProperty("sharedSourceRoot").orNull ?: localSharedRoot)?.let(::file) ?: file("shared/sugarlicious")
check(sharedRoot.resolve("core-model/build.gradle.kts").isFile) {
    "Shared sources missing. Run git submodule update --init --recursive."
}
val sharedModules = listOf("core-model", "cgm-protocol", "wear-protocol", "wear-storage", "ui-shared", "complications")
sharedModules.forEach { module ->
    include(":$module")
    project(":$module").projectDir = sharedRoot.resolve(module)
}
include(":app-sugarwear", ":watchfaces:vigil")
include(":tools:maintenance")
project(":tools:maintenance").projectDir = sharedRoot.resolve("tools/maintenance")
