package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import kotlinx.coroutines.asCoroutineDispatcher
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Rectangle
import java.awt.RenderingHints
import java.io.File
import java.util.concurrent.Executors
import javax.imageio.ImageIO

abstract class AbstractImageToX(fileName: String) : Runnable, AutoCloseable {
    protected val dispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()).asCoroutineDispatcher()

    protected val inputBi = ImageIO.read(File("scriptgen/in/$fileName").toURI().toURL())!!
    protected val inputDim = Rectangle(inputBi.width, inputBi.height)
    protected val inputG2D = inputBi.createGraphics()!!.apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        color = Color.WHITE
        stroke = BasicStroke(1.5f)  // a few mm wide pen?
    }
    protected val script = mutableListOf<Vector2D>()

    abstract fun getNextLocation(origin: Vector2D): Vector2D?

    override fun close() {
        inputG2D.dispose()
        val name = this.javaClass.simpleName
        ImageIO.write(inputBi, "png", File("scriptgen/out/decimated_$name.png"))
        AbstractImageToScaleFree.writeScriptFiles(NormalVector2D.normalizePoints(script.map { Vector2D(it.x.toDouble(), it.y.toDouble()) }), name)
        dispatcher.close()
    }
}