package app.sugarlicious.tools.cwf

import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: watchface-package-parser <CustomWatchface.zip>" }
    val document = WatchfacePackageParser().parse(Path.of(args[0]))
    println("VALID ${document.metadata.name} by ${document.metadata.author}")
    println("Canvas ${document.canvasWidth}x${document.canvasHeight}; ${document.elements.size} layout elements")
    document.warnings.forEach { println("WARNING $it") }
}
