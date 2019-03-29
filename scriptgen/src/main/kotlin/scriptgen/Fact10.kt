package scriptgen

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import javax.imageio.ImageIO

class Fact10 : Runnable, AutoCloseable {


    private val outputImage = BufferedImage(RES, RES, BufferedImage.TYPE_INT_RGB)
    private val outputG2d = outputImage.createGraphics()!!.apply {
        color = Color.WHITE
        fillRect(0, 0, outputImage.width, outputImage.height)
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.BLACK
        // a few mm wide pen?
        stroke = BasicStroke(1f)
    }

    override fun close() {
        outputG2d.dispose()
        val name = this.javaClass.simpleName
        ImageIO.write(outputImage, "png", File("scriptgen/out/decimated_$name.png"))
    }

    override fun run() {
        val mountainScale = 0.05
        val rangeStepSize = 5

        // Largest seen "lately" as you scan up the page
        val maxCoverHeights = DoubleArray(RES) { 0.0 }

        for (y in 900 downTo 100 step rangeStepSize) {
            val passLR = randomWalk().take(RES).toList()
            val passRL = randomWalk().take(RES).toList().asReversed()

            val mountainHeights = DoubleArray(RES) { idx ->
                Math.abs(passLR[idx] * passRL[idx]) * mountainScale
            }

            for (x in mountainHeights.indices) {
                mountainHeights[x] *= curve((x - RES / 2.0) / (RES / 2)) + 0.1
            }

            for (x in maxCoverHeights.indices) {
                maxCoverHeights[x] = listOf(0.0, maxCoverHeights[x] - rangeStepSize, mountainHeights[x]).max()!!
            }

            maxCoverHeights.mapIndexed { index, d -> index to d }.zipWithNext().forEach { (a, b) ->
                outputG2d.drawLine(a.first, y - a.second.toInt(), b.first, y - b.second.toInt())
            }
        }
    }

    companion object {
        private const val RES = 1000
    }
}


fun main() = Fact10().use { it.run() }

fun randomWalk() = generateSequence(0.0) { it + ThreadLocalRandom.current().nextGaussian() }

/** (-1,0) to (0,1) to (1,0) */
fun curve(x: Double): Double = if (x < -1 || x > 1) {
    0.0
} else {
    (Math.cos(x * Math.PI) + 1) / 2
}

