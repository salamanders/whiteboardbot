package scriptgen

import mu.KotlinLogging
import org.apache.commons.math3.geometry.euclidean.twod.Line
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.w3c.dom.Element
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

val LOG = KotlinLogging.logger {}

/** Hacky parse of a SVG, like what StippleGen2 produces */
fun fileToPath(svgXmlFile: File): List<Vector2D> {
    assert(svgXmlFile.canRead())
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svgXmlFile)!!
    val pathElement = doc.getElementsByTagName("path")!!.item(0)!! as Element
    val pen = Scanner(pathElement.getAttribute("d"))
    pen.next() // discard the first character
    val points = mutableListOf<Vector2D>()
    while (pen.hasNext()) {
        points.add(Vector2D(pen.nextDouble(), pen.nextDouble()))
    }
    return points
}

/** Scale proportionally to fit inside a unit sq, trimming to shape */
fun normalizePoints(points: List<Vector2D>): List<NormalVector2D> {
    val globalMin = Vector2D(points.minBy { it.x }!!.x, points.minBy { it.y }!!.y)
    val globalMax = Vector2D(points.maxBy { it.x }!!.x, points.maxBy { it.y }!!.y)

    val xScale = 1 / (globalMax.x - globalMin.x)
    val yScale = 1 / (globalMax.y - globalMin.y)
    val scaleFactor = Math.min(xScale, yScale)

    val scaled = points.map {
        it.subtract(globalMin).scalarMultiply(scaleFactor)!!
    }.map {
        NormalVector2D(it.x, it.y)
    }

    val scaledMax = NormalVector2D(scaled.maxBy { it.x }!!.x, scaled.maxBy { it.y }!!.y)
    val centeringOffset = Vector2D((1 - scaledMax.x) / 2, (1 - scaledMax.y) / 2)

    return scaled.map {
        it.add(centeringOffset)
    }.map {
        NormalVector2D(it.x, it.y)
    }
}

fun simplifyPath(points: List<Vector2D>, maxSize: Int = 1_000): List<Vector2D> {
    var maxDelta = 0.01
    var iterations = 0
    val smoothed = mutableListOf<Vector2D>()
    smoothed.addAll(points)
    while (smoothed.size > maxSize) {
        iterations++
        smoothed.clear()
        smoothed.addAll(ramerDouglasPeucker(points, maxDelta))
        maxDelta *= 1.05
    }
    LOG.info { "maxDelta of $maxDelta is under size cap $maxSize after $iterations passes." }
    return smoothed
}

/** Hacky cache.  Not sure if it actually helps! */
fun visvalingamWhyatt(points: List<Vector2D>, maxSize: Int): List<Vector2D> {
    val result = points.toMutableList()
    while (result.size > maxSize) {
        if (result.size % 1000 == 0) {
            LOG.info { "visvalingamWhyatt ${result.size}" }
        }
        val triangles = result.windowed(3).map { Triple(it[0], it[1], it[2]) }.map {
            it to Math.abs(it.first.x * (it.second.y - it.third.y) + it.second.x * (it.third.y - it.first.y) + it.third.x * (it.first.y - it.second.y)) / 2.0
        }
        val minTriArea = triangles.minBy { it.second }!!
        val idx = triangles.indexOf(minTriArea)
        result.removeAt(idx + 1)
    }
    return result
}


/**
 * Delete the most boring midpoints.
 * @see https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 */
private fun ramerDouglasPeucker(points: List<Vector2D>, maxDistanceAllowed: Double): List<Vector2D> {
    if (points.size <= 2) {
        return points
    }

    val line = Line(points.first(), points.last(), 1.0E-10)
    val distances = points.mapIndexed { index, vector2D ->
        index to vector2D
    }.filter { (index, _) ->
        index != 0 && index != points.size - 1
    }.map { (index, vector2D) ->
        index to line.distance(vector2D)
    }

    val (maxIdx, maxDist) = distances.maxBy { (_, distance) -> distance }!!
    return if (maxDist < maxDistanceAllowed) {
        listOf(points.first(), points.last())
    } else {
        val leftSide = ramerDouglasPeucker(points.subList(0, maxIdx + 1), maxDistanceAllowed)
        val rightSide = ramerDouglasPeucker(points.subList(maxIdx, points.size), maxDistanceAllowed)
        leftSide.plus(rightSide.drop(1))
    }
}
