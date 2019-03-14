package info.benjaminhill.wbb.content

import info.benjaminhill.wbb.str
import info.benjaminhill.wbb.x
import info.benjaminhill.wbb.y
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import javax.imageio.ImageIO

const val STROKES = 1_500
const val MAX_HOP = 0.2
const val WHITEOUT_WIDTH = 2f

/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 */
fun main() {
    val realPoints = mutableListOf<Point>()

    val inputImage = ImageIO.read(ClassLoader.getSystemResource("sundar_edge.png")!!)!!

    // Gradually white-out the input to avoid revisiting completed areas
    val inputG2d = inputImage.createGraphics()!!.apply {
        setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color(1f, 1f, 1f, .7f) // Higher alpha = more opaque
        stroke = BasicStroke(WHITEOUT_WIDTH)
    }

    val outputImage = BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_USHORT_GRAY)
    val outputG2d = outputImage.createGraphics()!!.apply {
        color = Color.WHITE
        fillRect(0, 0, outputImage.width, outputImage.height)
        setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        stroke = BasicStroke(1f)
    }

    // Reset to start anywhere in entire image 1 hop from center
    realPoints.add(inputImage.getDarkestNear(Point(inputImage.width / 2, inputImage.height / 2), 9_000))

    for (i in 0..STROKES) {
        val nextLoc = inputImage.getDarkestNear(realPoints.last(), 3_000)
        inputG2d.drawLine(realPoints.last().x, realPoints.last().y, nextLoc.x, nextLoc.y)
        outputG2d.drawLine(realPoints.last().x, realPoints.last().y, nextLoc.x, nextLoc.y)
        realPoints.add(nextLoc)
    }

    inputG2d.dispose()
    outputG2d.dispose()

    ImageIO.write(outputImage, "png", File("output.png"))
    ImageIO.write(inputImage, "png", File("input_scribbled.png"))

    val maxX = realPoints.maxBy { it.x }!!.x.toDouble()
    val maxY = realPoints.maxBy { it.y }!!.y.toDouble()
    val scale = Math.min(1 / maxX, 1 / maxY)
    realPoints.map {
        Pair(it.x * scale, it.y * scale)
    }.forEach {
        print("${it.x.str},${it.y.str}\\n")
    }

}

fun BufferedImage.getLum(loc: Point): Float {
    val color = getRGB(loc.x, loc.y)
    val red = color.ushr(16) and 0xFF
    val green = color.ushr(8) and 0xFF
    val blue = color.ushr(0) and 0xFF
    return (red * 0.2126f + green * 0.7152f + blue * 0.0722f) / 255
}


fun Point.getPointsAlongLine(other: Point): List<Point> {
    val dx = other.x - x
    val dy = other.y - y
    val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
    return (0..distance.toInt()).map {
        it / distance
    }.map { pct ->
        Point((x + (pct * dx)).toInt(), (y + (pct * dy)).toInt())
    }
}

/** Samples along a line to see what potential next move would wipe out the most darkness */
fun BufferedImage.getDarkestNear(
        origin: Point,
        searchSize: Int
): Point {
    var darkestLum = Float.MAX_VALUE
    var darkestLoc = origin

    for (i in 0..searchSize) {
        val nextLoc = Point(
                (ThreadLocalRandom.current().nextGaussian() * (width * MAX_HOP)).toInt() + origin.x,
                (ThreadLocalRandom.current().nextGaussian() * (height * MAX_HOP)).toInt() + origin.y
        )

        // out of bounds
        if (nextLoc.x !in 0 until width || nextLoc.y !in 0 until height) {
            continue
        }

        val averageLum = origin.getPointsAlongLine(nextLoc).map {
            getLum(it)
        }.average().toFloat()

        if (averageLum < darkestLum) {
            darkestLum = averageLum
            darkestLoc = nextLoc
        }
    }
    return darkestLoc
}
