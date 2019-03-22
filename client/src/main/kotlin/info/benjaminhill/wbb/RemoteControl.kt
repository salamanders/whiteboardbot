package info.benjaminhill.wbb

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.net.URL
import kotlin.coroutines.CoroutineContext

class RemoteControl(scriptURL: String) : AutoCloseable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + backgroundPool

    private val script = async {
        val config = JsonParser().parse(URL(scriptURL).readTextSupportGZIP()).asJsonObject!!
        val listOfPointsType = object : TypeToken<List<NormalizedVector2D>>() {}.type!!
        Gson().fromJson<List<NormalizedVector2D>>(config.getAsJsonArray("script"), listOfPointsType)!!.also {
            LOG.debug { "Web script loaded: ${it.size}" }
        }
    }

    private val plotter = Plotter().also {
        LOG.debug { "Created Plotter" }
    }

    override fun run() = runBlocking {
        LOG.info { "FRAME:START" }
        script.start()
        // Already at plotter.location = WPoint(1.0, 0.0)

        //plotter.location = WPoint(1.0, 1.0)
        //plotter.location = WPoint(0.0, 1.0)
        //plotter.location = WPoint(0.0, 0.0)
        LOG.info { "FRAME:END" }

        runScript(script.await())
        Unit
    }

    private fun runScript(path: List<NormalizedVector2D>) {
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

