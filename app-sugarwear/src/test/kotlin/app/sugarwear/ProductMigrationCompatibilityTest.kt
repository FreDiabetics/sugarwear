package app.sugarwear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sugarlicious.uishared.ProductDataMigrationProvider
import app.sugarlicious.uishared.ProductMigrationLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductMigrationCompatibilityTest {
    @Test fun unmarkedTargetStaysReady() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("app.sugarwear", context.packageName)
        for (marker in listOf("product-migration-complete", "product-migration-failure", "product-migration-quiesced", "product-migration-retired")) {
            File(context.noBackupFilesDir, marker).delete()
        }
        val settings = context.getSharedPreferences("g7_setup", Context.MODE_PRIVATE)
        settings.edit().putBoolean("enabled", true).commit()
        val provider = Robolectric.buildContentProvider(ProductMigrationProvider::class.java).create("app.sugarwear.product-migration")
        try {
            assertNull(ProductDataMigrationProvider.migrationFailure(context))
            ProductMigrationLifecycle.requireReady(context)
            assertEquals(true, settings.getBoolean("enabled", false))
        } finally {
            provider.get().shutdown()
        }
    }
}
