package app.sugarlicious.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LiveNotificationManifestTest {
    @Test
    fun `mobile manifest declares promoted permission and deviceVendor ongoing activity once`() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val permissions = document.getElementsByTagName("uses-permission")
        val metadata = document.getElementsByTagName("meta-data")

        assertEquals(
            1,
            (0 until permissions.length).count {
                permissions
                    .item(it)
                    .attributes
                    .getNamedItemNS(androidNamespace, "name")
                    ?.nodeValue ==
                    "android.permission.POST_PROMOTED_NOTIFICATIONS"
            },
        )
        assertEquals(
            1,
            (0 until permissions.length).count {
                permissions
                    .item(it)
                    .attributes
                    .getNamedItemNS(androidNamespace, "name")
                    ?.nodeValue ==
                    "android.permission.SCHEDULE_EXACT_ALARM"
            },
        )
        assertEquals(
            1,
            (0 until metadata.length).count {
                val attributes = metadata.item(it).attributes
                attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue ==
                    "com.samsung.android.support.ongoing_activity" &&
                    attributes.getNamedItemNS(androidNamespace, "value")?.nodeValue == "true"
            },
        )
    }
}
