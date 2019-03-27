package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Smaller step pixel-eating algo that tries to "eat towards the middle" on a drawing
 * Much like a line-following bot
 * TODO: Make less BROKEN
 */
class ImageToTrace(fileName: String) : AbstractImageToX(fileName) {

    private val zeroDeg = Vector2D(1.0, 0.0)
    private val center = Vector2D(inputDim.width / 2.0, inputDim.height / 2.0)
    private val maxHopSize = center.norm * 2

    override fun getNextLocation(origin: Vector2D): Vector2D? {
        // A normalized ray out from the center of the image towards current loc
        val centerToLoc = origin.subtract(center).normalize()
        // Directly AWAY from the center
        val startAngle = Vector2D.angle(zeroDeg, centerToLoc)

        var hopSize = 0.0
        val hopSizeIncrease = 1.0

        while (hopSize < maxHopSize) {
            hopSize += hopSizeIncrease
            val circumference = 2 * Math.PI * hopSize
            val angleStepSizeRad = (2 * Math.PI) / circumference
            heading@ for (i in 0..circumference.toInt()) {
                val angle = (startAngle - i * angleStepSizeRad) % (2 * Math.PI)
                val offset = angleToVector2D(angle).scalarMultiply(hopSize)
                val nextLoc = origin.add(offset)

                if (!inputDim.contains(nextLoc)) {
                    continue@heading
                }
                val ink = 1 - inputBi.getLum(nextLoc.x.toInt(), nextLoc.y.toInt())
                if (ink < 0.5) {
                    continue@heading
                }
                // White out the current move
                inputG2D.drawLine(origin.x.toInt(), origin.y.toInt(), nextLoc.x.toInt(), nextLoc.y.toInt())
                return nextLoc
            }
        }
        LOG.warn { "Halting because couldn't find a next step from $origin" }
        return null
    }


    override fun run() {
        // Lots hinges on this
        inputG2D.stroke = BasicStroke(1f)

        // Start in upper-right
        script.add(Vector2D((inputDim.width - 1).toDouble(), 0.0))
        while (true) {
            script.add(getNextLocation(script.last()) ?: break)
        }

        val outputImage = BufferedImage(inputDim.width, inputDim.height, BufferedImage.TYPE_INT_RGB)
        val outputG2d = outputImage.createGraphics()!!.apply {
            color = Color.WHITE
            fillRect(0, 0, outputImage.width, outputImage.height)
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }

        outputG2d.apply {
            color = Color.CYAN
            stroke = BasicStroke(3f)
        }

        script.zipWithNext { a, b ->
            outputG2d.drawLine(a.x.toInt(), a.y.toInt(), b.x.toInt(), b.y.toInt())
        }

        val tmp = script.toMutableList()
        script.clear()
        script.addAll(ramerDouglasPeucker(tmp, 5000))

        outputG2d.apply {
            color = Color.BLACK
            stroke = BasicStroke(1f)
        }
        script.zipWithNext { a, b ->
            outputG2d.drawLine(a.x.toInt(), a.y.toInt(), b.x.toInt(), b.y.toInt())
        }
        LOG.info { "Found ${tmp.size} (cyan) reduced to ${script.size} (black)" }
        outputG2d.dispose()
        ImageIO.write(outputImage, "png", File("scriptgen/out/overlay_${this.javaClass.simpleName}.png"))
    }
}

fun main() = ImageToTrace("sw.png").use { it.run() }

