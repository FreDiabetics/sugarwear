package app.sugarlicious.tools.screenshot

import java.io.File

fun main(args: Array<String>) {
    require(args.size in 3..4) {
        "Usage: screenshot-comparator <reference.png> <actual.png> <diff.png> [threshold]"
    }
    val metrics =
        ScreenshotComparator().compare(
            reference = File(args[0]),
            actual = File(args[1]),
            diff = File(args[2]),
            threshold = args.getOrNull(3)?.toInt() ?: 0,
        )
    println("${metrics.width}x${metrics.height}")
    println("MAE=${"%.4f".format(metrics.meanAbsoluteError)}")
    println("RMSE=${"%.4f".format(metrics.rootMeanSquareError)}")
    println("mismatched=${"%.4f".format(metrics.mismatchedPixelsPercent)}%")
    println("maxDelta=${metrics.maxChannelDelta}")
}
