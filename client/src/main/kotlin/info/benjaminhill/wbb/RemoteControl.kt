package info.benjaminhill.wbb

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import lejos.hardware.Battery
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext


class RemoteControl : AutoCloseable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    private val config: JsonObject = JsonParser().parse(URL("https://whiteboardbot.firebaseapp.com/config.json").readText()).asJsonObject.getAsJsonObject("wbb").getAsJsonObject("board01")!!.also {
        LOG.debug { "RC config from web: $it" }
    }
    private val spoolDistanceCm = config.get("spoolDistanceCm").asDouble.also {
        LOG.info { "config.spoolDistanceCm:$it" }
    }
    private val plotter = Plotter(spoolDistanceCm).also {
        LOG.info { "RC created plotter" }
    }

    private val mqtt = MQTT()

    init {
        LOG.info { "Launching 60 second telemetry" }
        launch {
            while (job.isActive) {
                val (x, y) = plotter.location
                mqtt.sendTelemetry(mapOf(
                        "voltage" to Battery.getVoltageMilliVolt(),
                        "x" to x,
                        "y" to y,
                        "spool0" to plotter.spool0Length,
                        "spool1" to plotter.spool1Length
                ))
                delay(60 * 1_000)
            }
        }
    }

    override fun run() = runBlocking {
        while (plotter.calibrate()) {
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

            plotter.location = Point2D.Double(0.5, 0.5)

            config.get("script")?.asString?.let {
                runScript(it)
            }
        }
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
        LOG.info("RC plotting ${path.size} points.")
        path.forEachIndexed { idx, point ->
            LOG.info { "Script $idx: ${point.str}" }
            plotter.location = Point2D.Double(point.x, point.y)
        }
    }

    override fun close() {
        LOG.info { "RC close()" }
        mqtt.close()
        plotter.close()
        job.cancel()
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
    }

}