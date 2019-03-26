package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt


/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 */
class ImageToScribble(
        fileName: String,
        private val strokes: Int,
        private val searchSteps: Int,
        maxPctHop: Double
) : ImageToX(fileName, inputRes = 600) {

    private val maxPixelHop = (maxPctHop * Math.min(imageDimension.width, imageDimension.height)).roundToInt()

    private fun getNextLocation(origin: Vector2D): Vector2D {
        var minSqOfLum = Double.MAX_VALUE
        lateinit var bestPoint: Vector2D

        for (i in 0..searchSteps) {

            // Gaussian random hops
            val nextPotentialPoint = Vector2D(
                    (ThreadLocalRandom.current().nextGaussian() * maxPixelHop) + origin.x,
                    (ThreadLocalRandom.current().nextGaussian() * maxPixelHop) + origin.y
            )

            if (nextPotentialPoint.ix !in 0 until imageDimension.width || nextPotentialPoint.iy !in 0 until imageDimension.height) {
                continue
            }
            val sqOfLum = origin.getPointsAlongLine(nextPotentialPoint).map { pointAlongLine ->
                val lum = inputImage.getLum(pointAlongLine)
                lum * lum // 0.99660
            }.average()

            if (sqOfLum < minSqOfLum) {
                bestPoint = nextPotentialPoint
                minSqOfLum = sqOfLum
            }

        }
        return bestPoint

    }

    override fun run() {
        script.add(Vector2D(imageDimension.width / 2.0, imageDimension.height / 2.0)) // Start in center

        for (i in 0..strokes) {
            val nextLoc = getNextLocation(script.last())
            inputG2d.drawLine(script.last().ix, script.last().iy, nextLoc.ix, nextLoc.iy)
            script.add(nextLoc)
        }
    }
}


fun main() {
    ImageToScribble("whale.png", 1_000, 10_000, 0.2).use {
        it.run()
    }
}

