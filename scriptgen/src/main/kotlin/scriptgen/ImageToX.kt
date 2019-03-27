package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import mu.KotlinLogging
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * For those who want to slice images into drawing commands.
 * All access to the input and output images are done in a normalized manner (0.0 until 1.0)
 */
abstract class ImageToX(fileName: String) : AutoCloseable {

    /** A way to get lums from a normalized surface */
    private val inputImageInk: FloatArray
    private val inputDimension: Int

    init {
        val bi = ImageIO.read(File("scriptgen/in/$fileName").toURI().toURL())!!

        val (xScoot, yScoot) = if (bi.width > bi.height) {
            inputDimension = bi.width
            Pair(0, (bi.width - bi.height) / 2)
        } else {
            inputDimension = bi.height
            Pair((bi.height - bi.width) / 2, 0)
        }
        inputImageInk = FloatArray(inputDimension * inputDimension) { 0f }

        for (x in 0 until bi.width) {
            for (y in 0 until bi.height) {
                val actualX = x + xScoot
                val actualY = y + yScoot
                inputImageInk[actualY * inputDimension + actualX] = 1 - bi.getLum(x, y)
            }
        }
        LOG.info { "Loaded lums for $fileName $inputDimension (${inputImageInk.size})" }
    }

    protected fun getInk(a: NormalVector2D): Float {
        val xa = (a.x * inputDimension).toInt()
        val ya = (a.y * inputDimension).toInt()
        val idx = ya * inputDimension + xa
        return if (idx < inputImageInk.size) inputImageInk[idx] else 0f
    }

    protected val script = mutableListOf<NormalVector2D>()

    override fun close() {
        if (script.isNotEmpty()) {
            writeScriptFiles(script, this.javaClass.simpleName)
        }
    }

    companion object {
        val LOG = KotlinLogging.logger {}

        fun writeScriptFiles(script: List<NormalVector2D>, name: String) {
            LOG.info { "Script steps: ${script.size}" }
            val outputImageRes = 2_000

            /** A sample rendering */
            val outputImage = BufferedImage(outputImageRes, outputImageRes, BufferedImage.TYPE_INT_RGB)
            val outputG2d = outputImage.createGraphics()!!.apply {
                color = Color.WHITE
                fillRect(0, 0, outputImage.width, outputImage.height)
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                color = Color.BLACK
                // a few mm wide pen?
                stroke = BasicStroke((2f / 1000) * outputImageRes)
            }

            // Needs a better way to tell if a point matters or not.
            // distinctBy {(it.x / plotterResolution).roundToInt() to (it.y / plotterResolution).roundToInt() }
            val scriptScaledToImage = script.map {
                Vector2D(it.x, it.y).scalarMultiply(outputImageRes.toDouble())
            }.zipWithNext()

            scriptScaledToImage.forEach { (a, b) ->
                check(a.x.roundToInt() in 0..outputImageRes)
                check(a.y.roundToInt() in 0..outputImageRes)
                outputG2d.drawLine(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt())
            }

            outputG2d.dispose()
            ImageIO.write(outputImage, "png", File("scriptgen/out/output_$name.png"))

            File("scriptgen/out/output_$name.txt").writeText(script.joinToString(",\n") { it.toString() })
        }
    }
}

