package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.Color

/**
 * Smaller step pixel-eating algo that tries to "eat towards the middle" on a drawing
 * Much like a line-following bot
 * TODO: Make less BROKEN
 */
class ImageToTrace(fileName: String) : ImageToX(fileName) {

    private val zeroDeg = Vector2D(1.0, 0.0)
    private val center = NormalVector2D(.5, .5)
    private val maxHopSize = Math.sqrt(2.0)
    /**
     * Fold back along yourself, then counter-clockwise look for first good pixel
     * TODO: Maybe just start rotation heading outwards?
     */
    private fun getNextLocation(currentLoc: NormalVector2D): Pair<Vector2D, Color>? {
        // A normalized ray out from the center of the image towards current loc
        val centerToLoc = currentLoc.subtract(center).normalize()
        // Directly AWAY from the center
        val startAngle = Vector2D.angle(zeroDeg, centerToLoc)

        var hopSize = 0.0
        val hopSizeIncrease = 1 / 500.0

        while (hopSize < maxHopSize) {
            hopSize += hopSizeIncrease
            val circumference = 2 * Math.PI * hopSize
            val angleStepSizeRad = (2 * Math.PI) / circumference
            heading@ for (i in 0..circumference.toInt()) {
                val angle = (startAngle - i * angleStepSizeRad) % (2 * Math.PI)
                val offset = angleToVector2D(angle).scalarMultiply(hopSize)
                val nextLoc = NormalVector2D.toNormal(currentLoc.add(offset))
                if (nextLoc.x <= 0 || nextLoc.x >= 1 ||
                        nextLoc.y <= 0 || nextLoc.y >= 1) {
                    continue@heading
                }
                val ink = getInk(nextLoc)
                if (ink < 0.5) {
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

    fun run() {
        /*
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

  script.addAll(ramerDouglasPeucker(allPoints, 5000))

  outputG2d.apply {
      color = Color.BLACK
      stroke = BasicStroke(1f)
  }
  script.zipWithNext { a, b ->
      outputG2d.drawLine(a.ix, a.iy, b.ix, b.iy)
  }


        LOG.info { "Found ${allPoints.size} (cyan) reduced to ${script.size} (black)" }

         */
    }
}


fun main() {
    ImageToTrace("sw.png").use { it.run() }
}

