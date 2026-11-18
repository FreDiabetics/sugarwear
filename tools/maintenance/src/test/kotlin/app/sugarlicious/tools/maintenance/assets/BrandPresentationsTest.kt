package app.sugarlicious.tools.maintenance.assets

import java.nio.file.Path
import kotlin.test.*

class BrandPresentationsTest {
    @Test fun `fit uses painted height rather than shrinking the whole canvas`() {
        val fit = fitBrandSymbol("M20,10H60V90H20Z")
        assertEquals(1.0, fit.scale, .00001)
        assertEquals(14.0, fit.x, .00001)
        assertEquals(4.0, fit.y, .00001)
    }

    @Test fun `SugarWear symbol fits at eighty units and remains centered`() {
        val symbol = sourcePath("ui-shared/src/main/res/drawable/ic_sugarwear_filled.xml")
        assertCenteredSymbol(symbol.getAttributeNS(ANDROID, "pathData"))
    }

    @org.junit.jupiter.api.Tag("sugarlicious-product")
    @Test
    fun `Sugarlicious symbol fits at eighty units and remains centered`() {
        val symbol = sourcePath("design-assets/app-icons/adaptive108/foreground.svg")
        assertCenteredSymbol(symbol.getAttribute("d"))
    }

    private fun sourcePath(name: String) =
        parse(Path.of(System.getProperty("maintenance.sharedRoot")).resolve(name)).documentElement.all("path").single()

    private fun assertCenteredSymbol(data: String) {
        val fit = fitBrandSymbol(data)
        val bounds = cairoPathBounds(data)
        assertEquals(80.0, (bounds.bottom - bounds.top) * fit.scale, .00001)
        assertEquals(54.0, (bounds.left + bounds.right) / 2 * fit.scale + fit.x, .00001)
        assertEquals(54.0, (bounds.top + bounds.bottom) / 2 * fit.scale + fit.y, .00001)
    }
}
