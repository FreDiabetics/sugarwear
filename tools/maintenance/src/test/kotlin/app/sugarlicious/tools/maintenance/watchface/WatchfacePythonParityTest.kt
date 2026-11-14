package app.sugarlicious.tools.maintenance.watchface

import kotlinx.serialization.json.*
import org.w3c.dom.Element
import java.math.MathContext
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

/** Golden hashes were independently produced by the retained Python tools on temporary DOMs.
 * Only numeric representation is normalized (12 significant digits); XML structure, order,
 * attributes, expressions and text are covered. No Python installation is needed by this test.
 */
class WatchfacePythonParityTest {
    private val golden =
        linkedMapOf(
            "apex:configure_indicator_gradient" to "e7d32631dd621103be7ae747dfc88c0f188c8621fc68bc1552f14f9fcfbf5a6c",
            "apex:segmented_progress" to "090c83c877f7e889e4af25a85ea6f1faa20fb882ac5e15cb6e61952c86691fce",
            "apex:restore_native_hands" to "090c83c877f7e889e4af25a85ea6f1faa20fb882ac5e15cb6e61952c86691fce",
            "apex:user_flavors" to "090c83c877f7e889e4af25a85ea6f1faa20fb882ac5e15cb6e61952c86691fce",
            "apex:unify_apex_progress_settings" to "889d307e7c612e490bcd827bf3c21913a0fce89d5a5bb420361b188908bec7fb",
            "apex:curated_palettes" to "0330d007d76f6ed99972b34ff1c6ccd1c58061032dad961fb9e0e5c4a61b4ae4",
            "apex:configure_apex_progress" to "090c83c877f7e889e4af25a85ea6f1faa20fb882ac5e15cb6e61952c86691fce",
            "orbit:configure_indicator_gradient" to "04358df91c4f74e3b46e7fb77470151f58adccde74f7c087e0f2f684295b6387",
            "orbit:segmented_progress" to "04358df91c4f74e3b46e7fb77470151f58adccde74f7c087e0f2f684295b6387",
            "orbit:restore_native_hands" to "04358df91c4f74e3b46e7fb77470151f58adccde74f7c087e0f2f684295b6387",
            "orbit:user_flavors" to "04358df91c4f74e3b46e7fb77470151f58adccde74f7c087e0f2f684295b6387",
            "apex:legacy_configure" to "e32f39b5e5245dd383b29ceebe92f476111bccff2fdbba108a4d08391e9a9d96",
            "apex:simplify_editor" to "1ad23b3d589e20505952f4716cba0227c00476ee60b3db8c806e942bcf65195f",
        )

    // Rebranding changes only component namespaces; historical geometry/expression hashes remain fixed.
    private fun normalize(value: String) =
        Regex("(?<![A-Za-z_])[-+]?\\d+(?:\\.\\d+)?(?:e[-+]?\\d+)?").replace(value.replace("app.sugarlicious.complications.", "app.aapswear.complications.")) { match ->
            match.value
                .toBigDecimal(MathContext(12))
                .stripTrailingZeros()
                .toPlainString()
        }

    private fun tree(element: Element): JsonObject =
        buildJsonObject {
            put("tag", element.tagName)
            put("attrs", buildJsonObject { element.attrs().toSortedMap().forEach { (k, v) -> put(k, normalize(v)) } })
            put(
                "text",
                normalize(
                    (0 until element.childNodes.length)
                        .asSequence()
                        .map { element.childNodes.item(it) }
                        .takeWhile { it.nodeType != org.w3c.dom.Node.ELEMENT_NODE }
                        .filter { it.nodeType == org.w3c.dom.Node.TEXT_NODE }
                        .joinToString("") { it.nodeValue }
                        .trim(),
                ),
            )
            put("children", JsonArray(element.children().map(::tree)))
        }

    @Test fun everyMigratedDomTransformationMatchesPythonSemanticOutput() {
        val repo = watchfaceRepositoryRoot()
        val failures = mutableListOf<String>()
        golden.forEach { (key, expected) ->
            val (family, command) = key.split(':')
            // Historical parity hashes require historical input, independent of new product features.
            val source = if (family == "orbit") repo.resolve("tools/maintenance/src/test/resources/dom-parity-input/orbit") else repo.resolve("tools/maintenance/src/test/resources/dom-parity-input/apex")
            val face = parseXml(source.resolve(if (family == "orbit") "watchface.xml" else "raw/watchface.xml"))
            val strings = parseXml(source.resolve(if (family == "orbit") "strings.xml" else "values/strings.xml"))
            when (command) {
                "configure_indicator_gradient" -> configureIndicatorGradient(face, family)
                "segmented_progress" -> configureSegmentedProgress(face, family)
                "restore_native_hands" -> restoreNativeHands(face, family)
                "user_flavors" -> replaceUserFlavors(face, strings, family)
                "unify_apex_progress_settings" -> unifyApexProgressSettings(face, strings)
                "curated_palettes" -> applyApexPalettes(face, strings)
                "configure_apex_progress" -> fitOuterValueText(face)
                "legacy_configure" -> {
                    val configs = face.need("UserConfigurations")
                    configs.need("ListConfiguration[@id='progressBars']").remove()
                    configs.add("BooleanConfiguration", "id" to "progressGradient", "defaultValue" to "TRUE")
                    configureApexProgress(face, strings)
                }
                "simplify_editor" -> {
                    face.need("Scene/ComplicationSlot[@slotId='7']/Complication[@type='LONG_TEXT']").set("type" to "SMALL_IMAGE")
                    simplifyApexEditor(face, strings)
                }
            }
            val data = JsonArray(listOf(tree(face), tree(strings))).toString()
            val actual = MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
            if (expected != actual) {
                Files.writeString(repo.resolve("tools/maintenance/build/$family-$command-kotlin.json"), data)
                failures += "$key expected=$expected actual=$actual"
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
