package info.benjaminhill.wbb.scriptgen

import info.benjaminhill.wbb.getImage
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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
    private val outputImage = BufferedImage(inputImage.width, inputImage.height, BufferedImage.TYPE_USHORT_GRAY)
    protected val outputG2d = outputImage.createGraphics()!!.apply {
        color = Color.WHITE
        fillRect(0, 0, outputImage.width, outputImage.height)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        stroke = BasicStroke(1f)
    }

    override fun close() {
        ImageIO.write(outputImage, "png", File("output.png"))
        ImageIO.write(inputImage, "png", File("input_scribbled.png"))

        inputG2d.dispose()
        outputG2d.dispose()
    }

    companion object {
        private const val SOURCE_MAX_RES = 400
    }
}

