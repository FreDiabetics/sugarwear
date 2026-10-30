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
val sharedRoot = file("shared/sugarlicious")
check(sharedRoot.resolve("core-model/build.gradle.kts").isFile) {
    "Shared sources missing. Run git submodule update --init --recursive."
}
val sharedModules = listOf("core-model", "dexcom-g7", "wear-protocol", "wear-storage", "ui-shared", "complications")
sharedModules.forEach { module ->
    include(":$module")
    project(":$module").projectDir = sharedRoot.resolve(module)
}
include(":g7watch", ":watchfaces:vigil")
