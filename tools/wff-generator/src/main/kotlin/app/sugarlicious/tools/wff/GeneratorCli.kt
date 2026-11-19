package app.sugarlicious.tools.wff

import app.sugarlicious.tools.cwf.WatchfacePackageParser
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size in 2..3) { "Usage: wff-generator <CustomWatchface.zip> <watchface.xml> [--allow-degraded]" }
    val allowDegraded = args.getOrNull(2) == "--allow-degraded"
    val document = WatchfacePackageParser().parse(Path.of(args[0]))
    val result = WffGenerator().generate(document, allowDegraded)
    val output = Path.of(args[1])
    output.parent?.let(Files::createDirectories)
    Files.writeString(output, result.xml)
    println("GENERATED $output with ${result.slotCount} complication slots")
    result.warnings.forEach { println("WARNING $it") }
    result.omittedElements.forEach { println("OMITTED $it") }
}
