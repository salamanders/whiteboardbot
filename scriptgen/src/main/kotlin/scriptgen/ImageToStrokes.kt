package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import kotlinx.coroutines.*
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.*
import java.awt.geom.Line2D
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import javax.imageio.ImageIO
import kotlin.math.roundToInt


/**
 * Decimate an image by drawing white lines over it.
 * Each white line is the "most beneficial" next step (based on dark luminosity removed)
 * Doesn't use normal lum array
 */
class ImageToStrokes(fileName: String,
                     private val strokes: Int,
                     private val searchSteps: Int,
                     private val maxPctHop: Double
) : Runnable, AutoCloseable {
    private val dispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()

    private val inputBi = ImageIO.read(File("scriptgen/in/$fileName").toURI().toURL())!!
    private val inputDim = Rectangle(inputBi.width, inputBi.height)
    private val inputG2D = inputBi.createGraphics()!!.apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.WHITE
        stroke = BasicStroke(1.5f)  // a few mm wide pen?
    }
    private val script = mutableListOf<Point>()

    private fun getNextLocation(origin: Point): Point = runBlocking(dispatcher) {
        val largestHop = maxPctHop * Math.max(inputDim.width, inputDim.height)

        val samples: List<Deferred<Pair<Point, Double>?>> = (0..searchSteps).map {
            async {
                // Gaussian random hops
                val nextPotentialPoint = Point(
                        (origin.x + ThreadLocalRandom.current().nextGaussian() * largestHop).roundToInt(),
                        (origin.y + ThreadLocalRandom.current().nextGaussian() * largestHop).roundToInt()
                )
                if (inputDim.contains(nextPotentialPoint) && origin.distance(nextPotentialPoint) > 2) {
                    val line = Line2D.Double(origin, nextPotentialPoint)
                    val avgInk = line.points().map { point ->
                        1 - inputBi.getLum(point.x.toInt(), point.y.toInt())
                    }.map { it * it }.average()
                    Pair(nextPotentialPoint, avgInk)
                } else {
                    null
                }
            }
        }

        val doneSamples = samples.mapNotNull { it.await() }
        val (bestPt, _) = doneSamples.maxBy { it.second }!!
        inputG2D.drawLine(origin.x, origin.y, bestPt.x, bestPt.y)
        bestPt
    }

    override fun run() {
        script.add(Point(inputDim.width / 2, inputDim.height / 2)) // Start in center
        for (i in 0..strokes) {
            script.add(getNextLocation(script.last()))
        }
    }

    override fun close() {
        inputG2D.dispose()
        val name = this.javaClass.simpleName
        ImageIO.write(inputBi, "png", File("scriptgen/out/decimated_$name.png"))
        ImageToX.writeScriptFiles(NormalVector2D.normalizePoints(script.map { Vector2D(it.x.toDouble(), it.y.toDouble()) }), name)
        dispatcher.close()
    }
}

fun main() {
    ImageToStrokes("sundar5.png", 2_000, 20_000, 0.5).use {
        it.run()
    }
}

