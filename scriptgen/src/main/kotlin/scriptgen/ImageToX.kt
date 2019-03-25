package scriptgen

import mu.KotlinLogging
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** For those who want to slice images into drawing commands */
abstract class ImageToX(fileName: String) : AutoCloseable, Runnable {
    protected val inputImage = getImage(fileName, SOURCE_MAX_RES)
    /** Gradually white-out the input to avoid revisiting completed areas */
    protected val inputG2d = inputImage.createGraphics()!!.apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color(1f, 1f, 1f, 1.0f) // Higher alpha = more opaque
        stroke = BasicStroke(1f)
    }
    protected val imageDimension = Dimension(inputImage.width, inputImage.height)
    /** A sample rendering */
    private val outputImage = BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_INT_RGB)

    protected val script = mutableListOf<Vector2D>()

    protected val outputG2d = outputImage.createGraphics()!!.apply {
        color = Color.WHITE
        fillRect(0, 0, outputImage.width, outputImage.height)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        stroke = BasicStroke(1f)
    }

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
        val fileName = this.javaClass.simpleName!!
        inputG2d.dispose()
        outputG2d.dispose()
        ImageIO.write(outputImage, "png", File("scriptgen/out/output_$fileName.png"))
        ImageIO.write(inputImage, "png", File("scriptgen/out/input_$fileName.png"))

        if (script.isNotEmpty()) {
            println(normalizePoints(script).joinToString(",\n") { it.toJSON() })
        }
    }

    companion object {
        private const val SOURCE_MAX_RES = 600
        val LOG = KotlinLogging.logger {}
    }
}

