package app.sugarlicious.tools.maintenance.migration

import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class ProductInstallerTest {
    private val root = Path.of(System.getProperty("maintenance.sharedRoot", System.getProperty("user.dir")))

    @Test fun publicIdsStayStableWithProjectOwnedNamespaces() {
        mapOf("app-mobile" to "app.sugarlicious", "app-wear" to "app.sugarlicious", "watchfaces/sugarlicious-analog" to "app.sugarlicious.watchfacepush.apex", "watchfaces/sugarlicious-orbit" to "app.sugarlicious.watchfacepush.orbit", "watchfaces/sugarlicious-digital" to "app.sugarlicious.watchfacepush.digital").forEach { (module, id) ->
            val source = root.resolve("$module/build.gradle.kts").readText()
            assertContains(source, "applicationId = \"$id\"")
            assertFalse(source.contains("legacyProductMigration"))
            assertTrue(Regex("namespace = \"app\\.sugarlicious\\.").containsMatchIn(source))
        }
    }

    @Test fun sugarliciousInstallerAndWearBuildExcludeStandaloneCollector() {
        val source = root.resolve("build.gradle.kts").readText()
        val block = source.substringAfter("tasks.named(\"installSugarliciousDebug\") {").substringBefore("\n}")
        assertEquals(setOf(":app-mobile:assembleDebug", ":app-wear:assembleDebug"), Regex("\"(:[^\"\\n]+:assembleDebug)\"").findAll(block).map { it.groupValues[1] }.toSet())
        assertFalse(source.contains("installSugarWearDebug"))
        val settings = root.resolve("settings.gradle.kts").readText()
        assertFalse(settings.contains("\":app-sugarwear\""))
        assertFalse(settings.contains("\":watchfaces:sugarlicious-direct-to-watch\""))
        assertFalse(root.resolve("app-sugarwear/build.gradle.kts").exists())
        assertFalse(root.resolve("watchfaces/sugarlicious-direct-to-watch/build.gradle.kts").exists())
        val wear = root.resolve("app-wear/build.gradle.kts").readText()
        assertFalse(wear.contains(":watchfaces:sugarlicious-direct-to-watch:assembleRelease"))
        assertFalse(wear.contains("prepareDirectToWatchFace"))
        assertContains(wear, "androidResources.ignoreAssetsPattern = \"sugarlicious_direct_to_watch.apk:sugarlicious_direct_to_watch_token.txt\"")
    }
}
