package scriptgen

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.Point
import java.awt.geom.Line2D
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt


/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 * Is input image scale dependent
 */
class ImageToStrokes(fileName: String,
                     private val strokes: Int,
                     private val searchSteps: Int,
                     private val maxPctHop: Double
) : AbstractImageToX(fileName) {

    override fun getNextLocation(origin: Vector2D): Vector2D = runBlocking(dispatcher) {
        val largestHop = maxPctHop * Math.max(inputDim.width, inputDim.height)
        val pOrigin = Point(origin.x.roundToInt(), origin.y.roundToInt())
        val samples: List<Deferred<Pair<Point, Double>?>> = (0..searchSteps).map {
            async {
                // Gaussian random hops.  This would be a really good swarm-optimizer!
                val p0 = Point(
                        (origin.x + ThreadLocalRandom.current().nextGaussian() * largestHop).roundToInt(),
                        (origin.y + ThreadLocalRandom.current().nextGaussian() * largestHop).roundToInt()
                )

                if (inputDim.contains(p0) && pOrigin.distance(p0) > 2) {

                    val avgInk0 = Line2D.Double(pOrigin, p0).points().map { point ->
                        1 - inputBi.getLum(point.x.toInt(), point.y.toInt())
                    }.map { it * it }.average()

                    Pair(p0, avgInk0)
                } else {
                    null
                }
            }
        }

        val doneSamples = samples.mapNotNull { it.await() }
        val (bestPt, _) = doneSamples.maxBy { it.second }!!
        // White out the current move
        inputG2D.drawLine(pOrigin.x, pOrigin.y, bestPt.x, bestPt.y)
        Vector2D(bestPt.x.toDouble(), bestPt.y.toDouble())
    }

    override fun run() {
        script.add(Vector2D(inputDim.width / 2.0, inputDim.height / 2.0)) // Start in center
        for (i in 0..strokes) {
            if (i % 100 == 0) {
                LOG.info { "$i of $strokes" }
            }
            script.add(getNextLocation(script.last()))
        }
    }
}

fun main() = ImageToStrokes(
        "ray.png",
        1_200,
        10_000,
        0.3).use { it.run() }

