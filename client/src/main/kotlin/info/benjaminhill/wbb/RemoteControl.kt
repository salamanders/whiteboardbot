package info.benjaminhill.wbb

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext

class RemoteControl : AutoCloseable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    private val config = async(coroutineContext) {
        JsonParser().parse(URL("https://whiteboardbot.firebaseapp.com/config.json").readText()).asJsonObject.getAsJsonObject("wbb").getAsJsonObject("board01")!!.also {
            LOG.debug { "Web config loaded." }
        }
    }

    private val plotter = Plotter().also {
        LOG.debug { "Created Plotter" }
    }

    override fun run() = runBlocking {
        LOG.info { "Framing drawing area" }
        // Frame the drawing area
        plotter.location = Point2D.Double(0.0, 0.0)

        plotter.location = Point2D.Double(0.5, 0.0)
        plotter.location = Point2D.Double(1.0, 0.0)

        plotter.location = Point2D.Double(1.0, 0.5)
        plotter.location = Point2D.Double(1.0, 1.0)

        plotter.location = Point2D.Double(0.5, 1.0)
        plotter.location = Point2D.Double(0.0, 1.0)

        plotter.location = Point2D.Double(0.0, 0.5)
        plotter.location = Point2D.Double(0.0, 0.0)

        LOG.info { "Finished frame, rendering script" }

        config.await().get("script")?.asString?.let {
            runScript(it)
        }
        Unit
    }

    private fun runScript(script: String) {
        val path = mutableListOf<Point2D.Double>()
        Scanner(script).useDelimiter("[^-.\\d]+").use { scanner ->
            while (scanner.hasNextDouble()) {
                val x = scanner.nextDouble()
                if (scanner.hasNextDouble()) {
                    val y = scanner.nextDouble()
                    path.add(Point2D.Double(x, y))
                }
            }
        }
        LOG.info { "RC plotting ${path.size} points." }
        path.forEachIndexed { idx, point ->
            plotter.location = Point2D.Double(point.x, point.y)
            if (idx % 100 == 0) {
                LOG.info { "Step $idx (${(idx * 100) / path.size}%)" }
            }

        }
    }

    override fun close() {
        LOG.debug { "RC close()" }
        // mqtt.close()
        plotter.close()
        job.cancel()
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
    }
}

