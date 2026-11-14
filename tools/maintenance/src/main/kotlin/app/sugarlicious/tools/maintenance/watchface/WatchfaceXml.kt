package app.sugarlicious.tools.maintenance.watchface

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal fun parseXml(path: Path): Element =
    DocumentBuilderFactory
        .newInstance()
        .apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder()
        .parse(path.toFile())
        .documentElement
        .also { root ->
            fun stripIndentation(node: Node) {
                val children = buildList { for (index in 0 until node.childNodes.length) add(node.childNodes.item(index)) }
                for (child in children) {
                    if (child.nodeType == Node.TEXT_NODE && child.nodeValue.isBlank()) node.removeChild(child) else stripIndentation(child)
                }
            }
            stripIndentation(root)
        }

internal fun Element.rewriteText(rewrite: (String) -> String) {
    for (element in all("descendant-or-self::*")) {
        for (index in 0 until element.childNodes.length) {
            val child = element.childNodes.item(index)
            if (child.nodeType == Node.TEXT_NODE) child.nodeValue = rewrite(child.nodeValue)
        }
    }
}

internal fun writeXml(root: Element, path: Path) {
    path.parent?.toFile()?.mkdirs()
    TransformerFactory
        .newInstance()
        .apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        .newTransformer()
        .apply {
            setOutputProperty("encoding", "UTF-8")
            // Indenting mixed-content WFF Template nodes inserts visible whitespace and
            // grows it on every rewrite. Keep their exact text and serialize compactly.
            setOutputProperty("indent", "no")
        }.transform(DOMSource(root.ownerDocument), StreamResult(path.toFile()))
}

internal fun Element.all(path: String): List<Element> {
    val nodes = XPathFactory.newInstance().newXPath().evaluate(path, this, XPathConstants.NODESET) as org.w3c.dom.NodeList
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

internal fun Element.one(path: String): Element? = all(path).firstOrNull()

internal fun Element.need(path: String): Element = requireNotNull(one(path)) { "Missing XML element $path" }

internal fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

internal fun Element.attr(key: String, default: String = ""): String = if (hasAttribute(key)) getAttribute(key) else default

internal fun Element.attrs(): Map<String, String> = (0 until attributes.length).associate { attributes.item(it).nodeName to attributes.item(it).nodeValue }

internal fun Element.set(vararg attrs: Pair<String, Any?>): Element = apply { attrs.forEach { (key, value) -> setAttribute(key, value.toString()) } }

internal fun Element.add(tag: String, vararg attrs: Pair<String, Any?>): Element = ownerDocument.createElement(tag).set(*attrs).also { appendChild(it) }

internal fun Element.clear() {
    while (hasChildNodes()) removeChild(firstChild)
    attrs().keys.forEach(::removeAttribute)
}

internal fun Element.remove() {
    parentNode.removeChild(this)
}

internal fun Element.copy(): Element = cloneNode(true) as Element

internal fun Element.insertBeforeOrAppend(child: Node, reference: Node?) {
    insertBefore(child, reference)
}

internal fun Element.rewriteAttributes(rewrite: (String) -> String) {
    all("descendant-or-self::*").forEach { element -> element.attrs().forEach { (key, value) -> element.set(key to rewrite(value)) } }
}

internal fun label(strings: Element, key: String, value: String) {
    (strings.one("string[@name='$key']") ?: strings.add("string", "name" to key)).textContent = value
}

internal fun pyFloat(value: Double): String = value.toString()

internal fun general(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else "%.6g".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

internal fun fixed(value: Double, digits: Int): String = ("%.${digits}f").format(java.util.Locale.ROOT, value)

internal fun pyRound(value: Double): Long = Math.rint(value).toLong()

internal const val RANGE_FRACTION = "([COMPLICATION.RANGED_VALUE_MAX] <= [COMPLICATION.RANGED_VALUE_MIN] ? 0 : clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1))"
internal val SEGMENTED = "(" + listOf(2, 3, 6, 7).joinToString(" || ") { "[CONFIGURATION.progressBars] == \"$it\"" } + ")"
