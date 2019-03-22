package info.benjaminhill.wbb.scriptgen

import info.benjaminhill.wbb.*
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt


/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 */
class ImageToScribble(fileName: String, private val strokes: Int, private val searchSteps: Int, maxPctHop: Double) : ImageToX(fileName) {

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
        val realPoints = mutableListOf<Vector2D>()
        realPoints.add(Vector2D(imageDimension.width / 2, imageDimension.height / 2)) // Start in center

        for (i in 0..strokes) {
            //val nextLoc = inputImage.getDarkestNear(realPoints.last(), 8_000)
            val nextLoc = getNextLocation(realPoints.last())
            inputG2d.drawLine(realPoints.last().ix, realPoints.last().iy, nextLoc.ix, nextLoc.iy)
            outputG2d.drawLine(realPoints.last().ix, realPoints.last().iy, nextLoc.ix, nextLoc.iy)
            realPoints.add(nextLoc)
        }

        val maxX = realPoints.maxBy { it.x }!!.x
        val maxY = realPoints.maxBy { it.y }!!.y
        val scale = Math.min(1 / maxX, 1 / maxY)
        realPoints.forEach {
            val pt = Vector2D(it.x * scale, it.y * scale)
            println("$pt,")
        }
    }
}


fun main() {
    ImageToScribble("falcon.png", 1_200, 8_000, 0.2).use {
        it.run()
    }
}

