package info.benjaminhill.wbb

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.Closeable
import java.net.URL
import kotlin.coroutines.CoroutineContext

class RemoteControl(scriptURL: String) : AutoCloseable, Closeable, Runnable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + backgroundPool

    private val script = async {
        val config = JsonParser().parse(URL(scriptURL).readTextSupportGZIP()).asJsonObject!!
        val listOfPointsType = object : TypeToken<List<NormalVector2D>>() {}.type!!
        Gson().fromJson<List<NormalVector2D>>(config.getAsJsonArray("script"), listOfPointsType)!!.also {
            LOG.debug { "Web script loaded: ${it.size}" }
        }
    }

    private val plotter = Plotter().also {
        LOG.debug { "Created Plotter" }
    }

    override fun run() = runBlocking {
        LOG.info { "FRAME:START" }
        script.start()
        plotter.location = NormalVector2D(1.0, 0.0)
        plotter.location = NormalVector2D(1.0, 1.0)
        plotter.location = NormalVector2D(0.0, 1.0)
        plotter.location = NormalVector2D(0.0, 0.0)
        LOG.info { "FRAME:END" }
        plotter.fastDraw(script.await())
        Unit
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

