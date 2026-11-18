package app.sugarlicious.tools.maintenance.standalone

import app.sugarlicious.tools.maintenance.migration.verifyCodefreeVigil
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*

class StandaloneBuildTest {
    private val root = Path.of(System.getProperty("maintenance.repositoryRoot"))
    private val shared = Path.of(System.getProperty("maintenance.sharedRoot"))

    @Test fun standaloneBuildUsesItsOwnLibraryModules() {
        val settings = root.resolve("settings.gradle.kts").readText()
        val moduleText = assertNotNull(Regex("val sharedModules = listOf\\((.*?)\\)").find(settings)).groupValues[1]
        val modules = Regex("\"([^\" ]+)\"").findAll(moduleText).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("core-model", "cgm-protocol", "wear-protocol", "wear-storage", "ui-shared", "complications"), modules)
        assertContains(settings, "project(\":\$module\").projectDir = sharedRoot.resolve(module)")
        assertContains(settings, "include(\":app-sugarwear\", \":watchfaces:vigil\")")
        assertContains(settings, "?: file(\".\")")
        assertEquals(root, shared)
        modules.forEach { assertTrue(root.resolve("$it/build.gradle.kts").isRegularFile(), "Local module missing: $it") }
        listOf("app-mobile", "app-wear").forEach { assertFalse(root.resolve("$it/build.gradle.kts").exists()) }
        assertContains(settings, "include(\":tools:maintenance\")")
        assertContains(settings, "project(\":tools:maintenance\").projectDir = sharedRoot.resolve(\"tools/maintenance\")")
        assertTrue(root.resolve("tools/maintenance/build.gradle.kts").isRegularFile())
    }

    @Test fun installerOnlyBuildsOwnedWatchPackages() {
        val build = root.resolve("build.gradle.kts").readText()
        assertContains(build, "dependsOn(\":app-sugarwear:assembleDebug\", \":watchfaces:vigil:assembleDebug\")")
        assertContains(build, "extra[\"productPhoneApks\"] = emptyList<String>()")
        assertEquals(2, Regex("\"[^\"\\n]+/build/outputs/apk/debug/[^\"\\n]+\\.apk\"").findAll(build).count())
        assertContains(build, "sharedRoot.resolve(\"gradle/product-installer.gradle\")")
        val installer = shared.resolve("gradle/product-installer.gradle").readText()
        listOf("watches.size() != 1", "devices.findAll { isWatch(it) }", "unique { physicalId(it) }", "'-s', serial, 'install', '-r', '-t'", "equalsIgnoreCase('Success')").forEach { assertContains(installer, it) }
        listOf("app-mobile", "app-wear", "uninstall").forEach { assertFalse(it in build) }
    }

    @Test fun ciBuildsAndValidatesOnlySugarWearAndVigil() {
        val workflow = root.resolve(".github/workflows/build.yml").readText()
        listOf(":watchfaces:vigil:assembleRelease", "-WatchfaceFiles", "watchfaces/vigil/src/main/res/raw/watchface.xml").forEach { assertContains(workflow, it) }
        listOf("app-mobile", "app-wear", "verify-codefree-watchfaces.ps1", "submodules: recursive").forEach { assertFalse(it in workflow) }
        // The original Python checks are now executed by their canonical Kotlin module.
        assertContains(workflow, ":tools:maintenance:test")
        assertContains(workflow, "verify-codefree-vigil")
        assertFalse("python3 tools/verify_codefree_vigil.py" in workflow)
    }

    @Test fun codefreeVerifierAcceptsResourcesAndRejectsDex() {
        val apk = Files.createTempDirectory("codefree-vigil").resolve("vigil.apk")

        fun archive(dex: Boolean) {
            ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
                zip.putNextEntry(ZipEntry("res/raw/watchface.xml"))
                zip.write("<WatchFace />".toByteArray())
                zip.closeEntry()
                if (dex) {
                    zip.putNextEntry(ZipEntry("classes2.dex"))
                    zip.write("dex".toByteArray())
                    zip.closeEntry()
                }
            }
        }
        archive(false)
        assertEquals(64, verifyCodefreeVigil(apk).length)
        archive(true)
        val error = assertFailsWith<IllegalArgumentException> { verifyCodefreeVigil(apk) }
        assertContains(assertNotNull(error.message), "executable DEX")
    }
}
