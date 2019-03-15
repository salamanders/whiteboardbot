package info.benjaminhill.wbb

import com.google.gson.JsonParser
import kotlinx.coroutines.*
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging
import java.net.URL
import kotlin.coroutines.CoroutineContext

class RemoteControl : AutoCloseable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    private val config = async(coroutineContext) {
        JsonParser().parse(URL("https://whiteboardbot.firebaseapp.com/script.json").readText()).asJsonObject.getAsJsonObject("wbb").getAsJsonObject("board01")!!.also {
            LOG.debug { "Web script loaded." }
        }
    }

    private val plotter = Plotter().also {
        LOG.debug { "Created Plotter" }
    }

    override fun run() = runBlocking {
        LOG.info { "FRAME:START" }
        // Frame the drawing area
        plotter.location = Point2D.Double(0.001, 0.001)
        plotter.location = Point2D.Double(0.999, 0.001)
        plotter.location = Point2D.Double(0.999, 0.999)
        plotter.location = Point2D.Double(0.001, 0.999)
        plotter.location = Point2D.Double(0.001, 0.001)
        LOG.info { "FRAME:END" }

        val points = config.await().get("script")!!.asJsonArray!!.filterNotNull().map {
            Point2D.Double(
                    it.asJsonObject.get("x").asDouble,
                    it.asJsonObject.get("y").asDouble
            )
        }
        runScript(points)
        Unit
    }

    private fun runScript(path: List<Point2D.Double>) {
        LOG.info { "RC plotting ${path.size} points." }
        path.forEachIndexed { idx, point ->
            plotter.location = point
            if (idx % 100 == 0) {
                LOG.info { "Script Step $idx (${(idx * 100) / path.size}%)" }
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

