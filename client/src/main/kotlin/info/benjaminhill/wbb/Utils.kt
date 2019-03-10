package info.benjaminhill.wbb

import lejos.robotics.geometry.Point2D
import org.w3c.dom.Element
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random


/** Shorter round for the logs */
val Double.str: String
    get() = "%.3f".format(this)

val Point2D.Double.str
    get() = "{x:${this.x.str}, y:${this.y.str}"

operator fun Point2D.Double.component1(): Double = this.x
operator fun Point2D.Double.component2(): Double = this.y

/** Simple REPL keys to commands that always have 'q' to quit */
fun keyboardCommands() = sequence {
    Scanner(System.`in`).useDelimiter("\\s*")!!.use { sc ->
        while (sc.hasNext()) {
            val ch = sc.next()!!
            if (ch == "q") {
                break
            }
            yield(ch[0])
        }
    }
}

/** Hacky parse of a SVG, like what StippleGen2 produces */
fun fileToPath(svgXmlFile: File): List<Point2D> {
    assert(svgXmlFile.canRead())
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svgXmlFile)!!
    val pathElement = doc.getElementsByTagName("path")!!.item(0)!! as Element
    val pen = Scanner(pathElement.getAttribute("d"))
    pen.next() // discard the first character
    val points = mutableListOf<Point2D>()
    while (pen.hasNext()) {
        points.add(Point2D.Double(pen.nextDouble(), pen.nextDouble()))
    }
    return points
}

/** Scale proportionally to fit in a unit sq */
fun normalizePoints(points: List<Point2D>): List<Point2D> {
    val globalMin = Point2D.Double(points.minBy { it.x }!!.x, points.minBy { it.y }!!.y)
    val globalMax = Point2D.Double(points.maxBy { it.x }!!.x, points.maxBy { it.y }!!.y)

    val xScale = 1 / (globalMax.x - globalMin.x)
    val yScale = 1 / (globalMax.y - globalMin.y)
    val scaleFactor = Math.min(xScale, yScale)

    return points.map {
        Point2D.Double(
                ((it.x - globalMin.x) * scaleFactor),
                ((it.y - globalMin.y) * scaleFactor)
        )
    }
}


fun exponentialRetryDelayMs(): Sequence<Long> {
    return generateSequence(500L) { retryIntervalMs ->
        (retryIntervalMs * 1.5 + Random.nextInt(0, 1000)).toLong().takeIf { it < 60 * 1_000 }
    }
}

/** For when you need a temp ID for the duration of an app run */
val sessionId by lazy {
    "23456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ".toList().shuffled().take(4).joinToString()
}

