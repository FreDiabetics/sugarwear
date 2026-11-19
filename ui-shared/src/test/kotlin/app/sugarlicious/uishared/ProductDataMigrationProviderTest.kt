package app.sugarlicious.uishared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProductDataMigrationProviderTest {
    @Test fun importedMarkerRetriesAcknowledgementWithoutImporting() {
        assertTrue(
            ProductDataMigrationProvider.migrationState("imported") ==
                ProductDataMigrationProvider.Companion.MigrationState.ACKNOWLEDGE,
        )
        listOf("acknowledged", "fresh").forEach {
            assertTrue(
                ProductDataMigrationProvider.migrationState(it) ==
                    ProductDataMigrationProvider.Companion.MigrationState.FINISHED,
            )
        }
        assertTrue(
            ProductDataMigrationProvider.migrationState(null) ==
                ProductDataMigrationProvider.Companion.MigrationState.IMPORT,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun installedLegacyWithoutBridgeCannotBecomeFreshInstall() {
        ProductDataMigrationProvider.requireFreshInstallAllowed(true)
    }

    @Test fun absentLegacyAllowsFreshInstall() {
        ProductDataMigrationProvider.requireFreshInstallAllowed(false)
    }

    @Test fun canonicalizedRootAllowsPlatformDirectoryAliases() {
        val root = Files.createTempDirectory("migration-alias-test").toFile()
        try {
            val alias = File(root, "nested/..")
            File(root, "nested").mkdir()
            val canonicalRoot = alias.canonicalFile
            assertTrue(ProductDataMigrationProvider.safeDestination(canonicalRoot, "files/a") == File(root, "files/a"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsTraversalAndNonDataPaths() {
        listOf(
            "../shared_prefs/a.xml",
            "/shared_prefs/a.xml",
            "files/../secret",
            "files//a",
            "files/./a",
            "files\\a",
            "cache/a",
            "files/logs/a",
            "files/a.apk",
            "files/diagnostics/a",
            "files/a.log",
            "files/" + "a".repeat(1024),
        ).forEach { assertFalse(it, ProductDataMigrationProvider.allowedPath(it)) }
    }

    @Test fun acceptsPreferencesDatabasesAndDatastore() {
        listOf(
            "shared_prefs/settings.xml",
            "databases/history.db",
            "databases/history.db-wal",
            "files/datastore/settings.preferences_pb",
            "files/sensor/session.json",
        ).forEach { assertTrue(it, ProductDataMigrationProvider.allowedPath(it)) }
    }

    @Test fun resolvesInsideDataRoot() {
        val root = Files.createTempDirectory("migration-test").toFile()
        try {
            assertTrue(ProductDataMigrationProvider.safeDestination(root, "files/a").path.startsWith(root.path))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutsideDestination() {
        ProductDataMigrationProvider.safeDestination(File("/data/app"), "files/../../outside")
    }
}
