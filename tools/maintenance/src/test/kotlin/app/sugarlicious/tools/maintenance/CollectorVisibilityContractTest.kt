package app.sugarlicious.tools.maintenance

import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

class CollectorVisibilityContractTest {
    @Test fun `Wear declares visibility for the separately installed collector provider`() {
        val root = Path.of(System.getProperty("maintenance.sharedRoot"))
        val manifest =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(root.resolve("app-wear/src/main/AndroidManifest.xml").toFile())
        val queries = manifest.getElementsByTagName("queries")
        val authorities =
            buildSet {
                for (index in 0 until queries.length) {
                    val providers = (queries.item(index) as org.w3c.dom.Element).getElementsByTagName("provider")
                    for (providerIndex in 0 until providers.length) {
                        addAll((providers.item(providerIndex) as org.w3c.dom.Element).getAttribute("android:authorities").split(';'))
                    }
                }
            }
        assertTrue("app.sugarwear.readings" in authorities, "Watch Direct needs explicit collector-provider visibility")
    }
}
