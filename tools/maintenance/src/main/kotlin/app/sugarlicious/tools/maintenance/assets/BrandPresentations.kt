package app.sugarlicious.tools.maintenance.assets

import java.nio.file.Path

internal data class BrandSymbolFit(
    val scale: Double,
    val x: Double,
    val y: Double,
)

/** Non-launcher presentations use painted 80:108 proportions, not adaptive safe-zone insets. */
internal fun fitBrandSymbol(data: String): BrandSymbolFit {
    val bounds = cairoPathBounds(data)
    val height = bounds.bottom - bounds.top
    require(height > 0 && bounds.right > bounds.left) { "Empty brand symbol" }
    val scale = 80.0 / height
    require((bounds.right - bounds.left) * scale <= 108.0) { "Brand symbol would clip horizontally" }
    return BrandSymbolFit(scale, 54.0 - (bounds.left + bounds.right) * scale / 2, 54.0 - (bounds.top + bounds.bottom) * scale / 2)
}

internal fun brandPresentations(args: List<String>, defaultRoot: Path) {
    val opts = options(args, setOf("--product", "--repository-root"))
    val product = opts["--product"] ?: "sugarlicious"
    require(product in setOf("sugarlicious", "sugarwear"))
    val root = Path.of(opts["--repository-root"] ?: defaultRoot.toString()).toAbsolutePath().normalize()
    val wear = product == "sugarwear"
    val directory = root.resolve("design-assets/${if (wear) "sugarwear" else "app-icons"}/adaptive108")
    val source = parse(directory.resolve("foreground.svg")).documentElement.all("path").single()
    val data = source.getAttribute("d")
    val fit = fitBrandSymbol(data)
    val combined = parse(directory.resolve("background.svg"))
    val group = combined.createElementNS("http://www.w3.org/2000/svg", "g")
    group.setAttribute("transform", "translate(${fit.x} ${fit.y}) scale(${fit.scale})")
    combined.documentElement.appendChild(group)
    val path = combined.importNode(source, true) as org.w3c.dom.Element
    path.removeAttribute("class")
    path.setAttribute("fill", "#ffffff")
    group.appendChild(path)
    writeXml(directory.resolve("readme.svg"), combined)
    svgPng(
        java.nio.file.Files
            .readString(directory.resolve("readme.svg")),
        root.resolve("build/reports/brand-presentations/$product.png"),
        324,
    )

    val vector = parseXml("<vector xmlns:android=\"$ANDROID\" xmlns:tools=\"http://schemas.android.com/tools\" android:width=\"24dp\" android:height=\"24dp\" android:viewportWidth=\"108\" android:viewportHeight=\"108\"/>")
    val symbol = vector.documentElement.add("group")
    symbol.android("scaleX", fit.scale)
    symbol.android("scaleY", fit.scale)
    symbol.android("translateX", fit.x)
    symbol.android("translateY", fit.y)
    symbol.add("path").apply {
        android("fillColor", "#FFFFFFFF")
        android("pathData", data)
        if (data.length > 3000) setAttributeNS("http://schemas.android.com/tools", "tools:ignore", "VectorPath")
    }
    writeXml(root.resolve("${if (wear) "app-sugarwear" else "app-wear"}/src/main/res/drawable/ic_tile_brand.xml"), vector)
}
