package info.benjaminhill.wbb

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging
import java.net.URL
import kotlin.coroutines.CoroutineContext


class RemoteControl : AutoCloseable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    private val script = async(coroutineContext) {
        val config = JsonParser().parse(URL("https://whiteboardbot.firebaseapp.com/config.json").readText()).asJsonObject!!
        val listOfPointsType = object : TypeToken<List<Point2D.Double>>() {}.type!!
        Gson().fromJson<List<Point2D.Double>>(config.getAsJsonArray("script"), listOfPointsType)!!.also {
            LOG.debug { "Web script loaded: ${it.size}" }
        }
    }

    private val plotter = Plotter().also {
        LOG.debug { "Created Plotter" }
    }

    override fun run() = runBlocking {
        LOG.info { "FRAME:START" }
        // Frame the drawing area
        plotter.location = Point2D.Double(0.0, 0.0)
        plotter.location = Point2D.Double(1.0, 0.0)
        plotter.location = Point2D.Double(1.0, 1.0)
        plotter.location = Point2D.Double(0.0, 1.0)
        plotter.location = Point2D.Double(0.0, 0.0)
        LOG.info { "FRAME:END" }

        runScript(script.await())
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

