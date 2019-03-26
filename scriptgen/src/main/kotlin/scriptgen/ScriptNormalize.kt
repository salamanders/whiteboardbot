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

fun ramerDouglasPeucker(points: List<Vector2D>, maxSize: Int = 1_000): List<Vector2D> {
    LOG.info { "ramerDouglasPeucker from ${points.size} to $maxSize" }
    var maxDelta = 0.01
    var iterations = 0
    val result = points.toMutableList()
    result.addAll(points)
    while (result.size > maxSize) {
        iterations++
        result.clear()
        result.addAll(ramerDouglasPeuckerRecursion(points, maxDelta))
        maxDelta *= 1.05
    }
    LOG.info { "maxDelta of $maxDelta is under size cap $maxSize after $iterations passes." }
    return result
}

/** Iteratively delete smallest 3-point triangles.  Could do lots of caching, but meh. */
fun visvalingamWhyatt(points: List<Vector2D>, maxSize: Int = 1_000): List<Vector2D> {
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
 * Delete any midpoints that aren't "interesting enough" to fall outside the error bar
 * Recursively subdivides
 * @see https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
 */
private fun ramerDouglasPeuckerRecursion(points: List<Vector2D>, maxDistanceAllowed: Double): List<Vector2D> {
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
        val leftSide = ramerDouglasPeuckerRecursion(points.subList(0, maxIdx + 1), maxDistanceAllowed)
        val rightSide = ramerDouglasPeuckerRecursion(points.subList(maxIdx, points.size), maxDistanceAllowed)
        leftSide.plus(rightSide.drop(1))
    }
}
