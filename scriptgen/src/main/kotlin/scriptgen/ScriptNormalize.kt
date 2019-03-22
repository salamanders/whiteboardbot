package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.w3c.dom.Element
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

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

fun main() {
    var pctScale = 1.0
    for (circle in 1..20) {
        for (deg in 1..360 step 5) {
            pctScale -= (1.0 / (20 * (360 / 5)))
            val p = Vector2D(
                    (pctScale * Math.sin(Math.toRadians(deg.toDouble()))) / 2 + 0.5,
                    (pctScale * Math.cos(Math.toRadians(deg.toDouble()))) / 2 + 0.5
            )
            println("$p,")
        }
    }
}

/** Scale proportionally to fit in a unit sq */
fun normalizePoints(points: List<Vector2D>): List<NormalVector2D> {
    val globalMin = Vector2D(points.minBy { it.x }!!.x, points.minBy { it.y }!!.y)
    val globalMax = Vector2D(points.maxBy { it.x }!!.x, points.maxBy { it.y }!!.y)

    val xScale = 1 / (globalMax.x - globalMin.x)
    val yScale = 1 / (globalMax.y - globalMin.y)
    val scaleFactor = Math.min(xScale, yScale)

    return points.map {
        it.subtract(globalMin).scalarMultiply(scaleFactor)
    }.map {
        NormalVector2D(it.x, it.y)
    }
}