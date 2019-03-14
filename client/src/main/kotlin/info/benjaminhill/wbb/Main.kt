package info.benjaminhill.wbb

/**
 * @author Benjamin Hill benjaminhill@gmail.com
 */

import mu.KotlinLogging

val LOG = KotlinLogging.logger {}

fun main() {
    LOG.info { "main(): debug:${LOG.isDebugEnabled}" }
    println(getBrickIPAddress())

    RemoteControl().use {
        it.run()
        println("Finished Script")
    }

    LOG.info { "Exiting app normally." }
}


