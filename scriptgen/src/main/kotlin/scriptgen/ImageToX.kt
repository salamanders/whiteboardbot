package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import mu.KotlinLogging
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.imgscalr.Scalr
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** For those who want to slice images into drawing commands */
abstract class ImageToX(fileName: String, inputRes: Int = 1000) : AutoCloseable, Runnable {
    protected val inputImage = getImage(fileName, inputRes)

    /** Gradually white-out the input to avoid revisiting completed areas */
    protected val inputG2d = inputImage.createGraphics()!!.apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color(1f, 1f, 1f, 1.0f) // Higher alpha = more opaque
        stroke = BasicStroke(1f)
    }
    protected val imageDimension = Dimension(inputImage.width, inputImage.height)

    protected val script = mutableListOf<Vector2D>()

    protected val diagonal by lazy {
        Math.sqrt((imageDimension.width * imageDimension.width + imageDimension.height * imageDimension.height).toDouble()).roundToInt().also {
            LOG.info { "Diagonal: $it" }
        }
    }

    protected val center by lazy {
        Vector2D(imageDimension.width / 2.0, imageDimension.height / 2.0).also {
            LOG.info { "Center: $it" }
        }
    }

    override fun close() {
        if (script.isNotEmpty()) {
            LOG.info { "Script steps: ${script.size}" }
            val plotterResolution = 1.0 / 1_000
            val outputImageRes = 2_000

            /** A sample rendering */
            val outputImage = BufferedImage(outputImageRes, outputImageRes, BufferedImage.TYPE_INT_RGB)
            val outputG2d = outputImage.createGraphics()!!.apply {
                color = Color.WHITE
                fillRect(0, 0, outputImage.width, outputImage.height)
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                color = Color.BLACK
                stroke = BasicStroke(1f)
            }

            val normalScript = NormalVector2D.normalizePoints(script)
            /*
            // Needs a better way to tell if a point matters or not.
            .distinctBy {
        (it.x / plotterResolution).roundToInt() to (it.y / plotterResolution).roundToInt()
    }
    */
            LOG.info { "Normal Distinct Script steps: ${normalScript.size}" }
            normalScript.zipWithNext().forEach { (a, b) ->
                outputG2d.drawLine((a.x * outputImageRes).roundToInt(), (a.y * outputImageRes).roundToInt(), (b.x * outputImageRes).roundToInt(), (b.y * outputImageRes).roundToInt())
            }
            outputG2d.dispose()
            ImageIO.write(outputImage, "png", File("scriptgen/out/output_${this.javaClass.simpleName}.png"))

            File("scriptgen/out/output_${this.javaClass.simpleName}.txt").writeText(normalScript.joinToString(",\n") { it.toString() })
        }

        inputG2d.dispose()
        ImageIO.write(inputImage, "png", File("scriptgen/out/input_${this.javaClass.simpleName}.png"))
    }

    companion object {
        val LOG = KotlinLogging.logger {}

        fun getImage(fileName: String, res: Int = 500): BufferedImage {
            val resource = fileName.let {
                object {}.javaClass::class.java.getResource(it)
                        ?: File("scriptgen/in/$it").toURI().toURL()
            }!!
            return Scalr.resize(ImageIO.read(resource)!!, Scalr.Method.ULTRA_QUALITY, res, res)!!
        }
    }
}

