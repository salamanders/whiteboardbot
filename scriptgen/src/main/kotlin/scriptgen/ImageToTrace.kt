package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.BasicStroke
import java.awt.Color

/**
 * Smaller step pixel-eating algo that tries to "eat towards the middle" on a drawing
 * Much like a line-following bot
 */
class ImageToTrace(fileName: String) : ImageToX(fileName) {

    private val zeroDeg = Vector2D(1.0, 0.0)
    /**
     * Fold back along yourself, then counter-clockwise look for first good pixel
     * TODO: Maybe just start rotation heading outwards?
     */
    private fun getNextLocation(currentLoc: Vector2D): Pair<Vector2D, Color>? {
        // A normalized ray out from the center of the image towards current loc
        val centerToLoc = currentLoc.subtract(center).normalize()
        // Directly AWAY from the center
        val startAngle = Vector2D.angle(zeroDeg, centerToLoc)

        for (hopSize in 1..diagonal) {
            val circumference = 2 * Math.PI * hopSize
            val angleStepSizeRad = (2 * Math.PI) / circumference
            heading@ for (i in 0..circumference.toInt()) {
                val angle = (startAngle - i * angleStepSizeRad) % (2 * Math.PI)
                val offset = angleToVector2D(angle).scalarMultiply(hopSize.toDouble())
                val nextLoc = currentLoc.add(offset)
                if (nextLoc.ix !in 0 until imageDimension.width ||
                        nextLoc.iy !in 0 until imageDimension.height) {
                    continue@heading
                }
                val lum = inputImage.getLum(nextLoc.ix, nextLoc.iy)
                if (lum > 0.5) {
                    continue@heading
                }
                val color = if (hopSize < 5) {
                    Color.GREEN.brighter()
                } else {
                    Color.PINK.brighter()
                }!!
                return Pair(nextLoc, color)
            }

        }
        LOG.warn { "Halting because couldn't find a next step from $currentLoc" }
        return null
    }

    override fun run() {
        // Lots hinges on this
        inputG2d.stroke = BasicStroke(1f)

        // Start in upper-right
        var loc = Vector2D((imageDimension.width - 1).toDouble(), 0.0)
        val allPoints = mutableListOf<Vector2D>()

        do {
            val nextLocation = getNextLocation(loc)
            nextLocation?.let { (nextLoc, color) ->
                inputG2d.color = color
                inputG2d.drawLine(loc.ix, loc.iy, nextLoc.ix, nextLoc.iy)
                loc = nextLoc
                allPoints.add(loc)
            }
        } while (nextLocation != null)

        outputG2d.apply {
            color = Color.CYAN
            stroke = BasicStroke(3f)
        }

        allPoints.zipWithNext { a, b ->
            outputG2d.drawLine(a.ix, a.iy, b.ix, b.iy)
        }

        script.addAll(simplifyPath(allPoints, 4000))
        //script.addAll(visvalingamWhyatt(allPoints, 4000))

        outputG2d.apply {
            color = Color.BLACK
            stroke = BasicStroke(1f)
        }
        script.zipWithNext { a, b ->
            outputG2d.drawLine(a.ix, a.iy, b.ix, b.iy)
        }

        LOG.info { "Found ${allPoints.size} (cyan) reduced to ${script.size} (black)" }
    }
}


fun main() {
    ImageToTrace("sw.png").use { it.run() }
}

