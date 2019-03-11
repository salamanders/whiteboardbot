package info.benjaminhill.wbb

/**
 * @author Benjamin Hill benjaminhill@gmail.com
 */

import lejos.hardware.BrickFinder
import lejos.hardware.Button
import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}

fun main() {
    LOG.info("main(): debug:${LOG.isDebugEnabled}")
    BrickFinder.discover()?.filterNotNull()?.forEach {
        LOG.info { "BrickInfo: ${it.name} ${it.type} ${it.ipAddress}" }
    }


    RemoteControl().use {
        it.run()
        LOG.info { "Done with RemoteControl scripts, press any button to exit" }
        Button.waitForAnyPress()
    }
    LOG.warn { "Exiting app normally." }
}



