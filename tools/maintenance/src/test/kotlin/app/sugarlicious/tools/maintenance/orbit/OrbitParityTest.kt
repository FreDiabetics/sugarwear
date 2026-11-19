package app.sugarlicious.tools.maintenance.orbit

import app.sugarlicious.tools.maintenance.watchface.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.math.BigDecimal
import java.math.MathContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class OrbitParityTest {
    @Test fun `native generator preserves complete independent Python output semantics`() {
        val directory = Files.createTempDirectory("orbit-native-parity")
        try {
            generateOrbit(directory)
            for ((relative, reference) in listOf("raw/watchface.xml" to "watchface.xml", "values/strings.xml" to "strings.xml")) {
                val expected = directory.resolve("expected-$reference")
                javaClass.getResourceAsStream("/orbit-reference/$reference").use { Files.copy(requireNotNull(it), expected) }
                val actual = parseXml(directory.resolve(relative))
                val old = parseXml(expected)
                if (relative.startsWith("values")) {
                    assertEquals(old.children().associate { it.attr("name") to it.textContent }, actual.children().associate { it.attr("name") to it.textContent })
                } else {
                    compare(old, actual, "WatchFace")
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun compare(expected: Element, actual: Element, path: String) {
        assertEquals(expected.tagName, actual.tagName, path)
        assertEquals(expected.attrs().mapValues { normalize(it.value) }, actual.attrs().mapValues { normalize(it.value) }, "$path attributes")
        assertEquals(text(expected), text(actual), "$path text")
        val left = expected.children()
        val right = actual.children()
        assertEquals(left.size, right.size, "$path children")
        for (index in left.indices) compare(left[index], right[index], "$path/${left[index].tagName}[$index]")
    }

    private fun text(element: Element): String =
        (0 until element.childNodes.length)
            .map { element.childNodes.item(it) }
            .filter { it.nodeType == Node.TEXT_NODE && it.nodeValue.isNotBlank() }
            .joinToString("") { it.nodeValue.trimEnd('\n', '\r', ' ', '\t') }

    // CPython/libm and JVM trigonometry can differ by one ULP. Compare numeric
    // literals to 12 significant digits; preserve operators, fields and ordering.
    private fun normalize(value: String): String =
        Regex("(?<![A-Za-z_])[-+]?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?").replace(value) {
            BigDecimal(it.value).round(MathContext(12)).stripTrailingZeros().toPlainString()
        }
}
