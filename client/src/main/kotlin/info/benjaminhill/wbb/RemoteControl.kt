package info.benjaminhill.wbb

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging
import java.net.URL
import java.util.*


class RemoteControl : AutoCloseable, Runnable {
    private val config: JsonObject = JsonParser().parse(URL("https://whiteboardbot.firebaseapp.com/config.json").readText()).asJsonObject.getAsJsonObject("wbb").getAsJsonObject("board01")!!.also {
        LOG.debug { "RC config from web: $it" }
    }
    private val spoolDistanceCm = config.get("spoolDistanceCm").asDouble.also {
        LOG.info { "config.spoolDistanceCm:$it" }
    }
    private val plotter = Plotter(spoolDistanceCm).also {
        LOG.info { "RC created plotter" }
    }

    //private val mqtt = MQTT() // TODO something with the commands

    override fun run() {
        if (plotter.calibrate()) {
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
        path.forEach {
            plotter.location = Point2D.Double(it.x, it.y)
        }
    }

    override fun close() {
        LOG.info { "RC close()" }
        //mqtt.close()
        plotter.close()
    }

    companion object {
        private val LOG = KotlinLogging.logger {}
    }

}