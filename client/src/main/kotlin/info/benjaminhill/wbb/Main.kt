package info.benjaminhill.wbb

/**
 * @author Benjamin Hill benjaminhill@gmail.com
 */

import lejos.hardware.Button
import mu.KotlinLogging

private val LOG = KotlinLogging.logger {}

fun main() {
    LOG.info("main(): debug:${LOG.isDebugEnabled}")
    RemoteControl().use {
        it.run()
        LOG.info { "Done with RemoteControl scripts, press any button to exit" }
        Button.waitForAnyPress()
    }
    LOG.warn { "Exiting app normally." }
}



