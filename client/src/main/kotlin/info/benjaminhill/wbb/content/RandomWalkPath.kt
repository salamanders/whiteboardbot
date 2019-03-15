package info.benjaminhill.wbb.content

import info.benjaminhill.wbb.checkNormal
import info.benjaminhill.wbb.str
import lejos.robotics.geometry.Point2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import javax.imageio.ImageIO

const val STROKES = 800
const val MAX_HOP = 0.3
const val WHITEOUT_WIDTH = 2f

/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 */
fun main() {
    val realPoints = mutableListOf<Point>()

    val inputImage = ImageIO.read(ClassLoader.getSystemResource("xwing2.png")!!)!!

    // Gradually white-out the input to avoid revisiting completed areas
    val inputG2d = inputImage.createGraphics()!!.apply {
        setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color(1f, 1f, 1f, .9f) // Higher alpha = more opaque
        stroke = BasicStroke(WHITEOUT_WIDTH)
    }
    val startingLum = inputImage.getAverageLum()

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

    realPoints.add(Point(inputImage.width / 2, inputImage.height / 2))
    // Reset to start anywhere in entire image 1 hop from center
    // realPoints.add(inputImage.getDarkestNear(Point(inputImage.width / 2, inputImage.height / 2), 10_000))

    for (i in 0..STROKES) {
        val nextLoc = inputImage.getDarkestNear(realPoints.last(), 4_000)
        inputG2d.drawLine(realPoints.last().x, realPoints.last().y, nextLoc.x, nextLoc.y)
        outputG2d.drawLine(realPoints.last().x, realPoints.last().y, nextLoc.x, nextLoc.y)
        realPoints.add(nextLoc)
    }

    inputG2d.dispose()
    outputG2d.dispose()

    ImageIO.write(outputImage, "png", File("output.png"))
    ImageIO.write(inputImage, "png", File("input_scribbled.png"))

    val endingLum = inputImage.getAverageLum()
    val pctGainLum = (endingLum - startingLum) / (1.0 - startingLum)

    println("Score: ${(pctGainLum * 100).toInt()}%")

    val maxX = realPoints.maxBy { it.x }!!.x.toDouble()
    val maxY = realPoints.maxBy { it.y }!!.y.toDouble()
    val scale = Math.min(1 / maxX, 1 / maxY)
    realPoints.forEach {
        val pt = Point2D.Double(it.x * scale, it.y * scale)
        pt.checkNormal()
        println("{\"x\":${pt.x.str},\"y\":${pt.y.str}},")
    }

}

fun BufferedImage.getLum(loc: Point): Float {
    val color = getRGB(loc.x, loc.y)
    val red = color.ushr(16) and 0xFF
    val green = color.ushr(8) and 0xFF
    val blue = color.ushr(0) and 0xFF
    return (red * 0.2126f + green * 0.7152f + blue * 0.0722f) / 255
}

fun BufferedImage.getAverageLum(): Double {
    var totalLum = 0.0
    for (x in 0 until width) {
        for (y in 0 until height) {
            totalLum += getLum(Point(x, y))
        }
    }
    return totalLum / (width * height)
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

/** Fixed size step */
fun circularFixedSteps(origin: Point): List<Point> = (0..360).map {
    Math.toRadians(it.toDouble())
}.map {
    Point(
            origin.x + (Math.cos(it) * 5).toInt(),
            origin.y + (Math.sin(it) * 5).toInt()
    )
}

fun randomGaussianSteps(origin: Point, searchSize: Int, maxHop: Double) = (0..searchSize).map {
    Point(
            (ThreadLocalRandom.current().nextGaussian() * maxHop).toInt() + origin.x,
            (ThreadLocalRandom.current().nextGaussian() * maxHop).toInt() + origin.y
    )
}


/**
 * Given the generators's steps,
 * samples along a line to see what potential next move would wipe out the most darkness
 */
fun BufferedImage.getDarkestNear(
        origin: Point,
        searchSize: Int
): Point =
        randomGaussianSteps(origin, searchSize, width * MAX_HOP)
                //circularFixedSteps(origin)
                .filter {
                    // Reject if out of bounds
                    it.x in 0 until width && it.y in 0 until height
                }.map { nextLoc ->
                    nextLoc to origin.getPointsAlongLine(nextLoc).map { pointAlongLine ->
                        val lum = getLum(pointAlongLine)
                        lum * lum // 0.99660
                    }.average().toFloat()
                }.minBy { it.second }!!.first
