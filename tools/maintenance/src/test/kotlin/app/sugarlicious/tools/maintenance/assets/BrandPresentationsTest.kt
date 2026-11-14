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

    @Test fun `both owned symbols have an eighty unit painted height and remain centered`() {
        val root = Path.of(System.getProperty("maintenance.sharedRoot"))
        listOf("design-assets/app-icons/adaptive108/foreground.svg", "ui-shared/src/main/res/drawable/ic_sugarwear_filled.xml").forEach { name ->
            val symbol = parse(root.resolve(name)).documentElement.all("path").single()
            val data = if (name.endsWith("svg")) symbol.getAttribute("d") else symbol.getAttributeNS(ANDROID, "pathData")
            val fit = fitBrandSymbol(data)
            val bounds = cairoPathBounds(data)
            assertEquals(80.0, (bounds.bottom - bounds.top) * fit.scale, .00001)
            assertEquals(54.0, (bounds.left + bounds.right) / 2 * fit.scale + fit.x, .00001)
            assertEquals(54.0, (bounds.top + bounds.bottom) / 2 * fit.scale + fit.y, .00001)
        }
    }
}
