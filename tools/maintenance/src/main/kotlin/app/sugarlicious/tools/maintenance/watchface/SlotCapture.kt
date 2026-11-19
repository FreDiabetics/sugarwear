package app.sugarlicious.tools.maintenance.watchface

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal fun prepareSlotCapture(source: Path, destination: Path, name: String) {
    Files.createDirectories(destination)
    Files.walk(source.resolve("res")).use { paths ->
        paths.forEach { path ->
            val target = destination.resolve("res").resolve(source.resolve("res").relativize(path))
            if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    val xml = destination.resolve("res/raw/watchface.xml")
    val root = parseXml(xml)
    root.one("UserConfigurations/Flavors")?.remove()
    root.need("Scene").children().forEach { child ->
        if (child.tagName != "ComplicationSlot" && child.attr("name") != "center-contours") {
            child.remove()
        } else if (child.tagName == "ComplicationSlot") {
            val policy = child.one("DefaultProviderPolicy")
            val kind = policy?.attr("primaryProviderType", policy.attr("defaultSystemProviderType")) ?: ""
            if (child.attr("isCustomizable") != "TRUE" || kind in listOf("SMALL_IMAGE", "LARGE_IMAGE", "MONOCHROMATIC_IMAGE")) child.remove()
        }
    }
    writeXml(root, xml)
    val manifest = parseXml(source.resolve("AndroidManifest.xml"))
    manifest.set("package" to "app.sugarlicious.watchfacepush.slotcapture$name")
    val android = "http://schemas.android.com/apk/res/android"
    manifest.need("application").apply {
        setAttributeNS(android, "android:label", "Slot Capture $name")
        removeAttributeNS(android, "dataExtractionRules")
    }
    manifest.add("uses-sdk").apply {
        setAttributeNS(android, "android:minSdkVersion", "36")
        setAttributeNS(android, "android:targetSdkVersion", "37")
    }
    writeXml(manifest, destination.resolve("AndroidManifest.xml"))
}
