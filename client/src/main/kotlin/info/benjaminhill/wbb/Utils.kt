package info.benjaminhill.wbb

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.imgscalr.Scalr
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStreamReader
import java.net.*
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO

/** Shorter round for the logs */
val Double.str: String
    get() = "%.3f".format(this)

/** Simple REPL keys to commands that always have 'q' to quit */
fun keyboardCommands() = sequence {
    Scanner(System.`in`).useDelimiter("\\s*")!!.use { sc ->
        while (sc.hasNext()) {
            val ch = sc.next()!!
            if (ch == "q") {
                break
            }
            yield(ch[0])
        }
    }
}

/** No fun if just one thread */
val backgroundPool: CoroutineDispatcher by lazy {
    val numProcessors = Runtime.getRuntime().availableProcessors()
    when {
        numProcessors < 3 -> {
            LOG.info { "Using custom thread pool context with 3 threads." }
            Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        }
        else -> Dispatchers.Default
    }
}

/** Finds the first brick on the LAN */
fun getBrickIPAddress(): String? {
    val maxDiscoveryTimeMs = 2000
    val maxPacketSize = 32
    val discoveryPort = 3016

    DatagramSocket().use { socket ->
        socket.broadcast = true
        NetworkInterface.getNetworkInterfaces().toList().filterNotNull().filterNot { it.isLoopback }.filter { it.isUp }
                .flatMap { it.interfaceAddresses }.filterNotNull().forEach { interfaceAddress ->
                    interfaceAddress.broadcast?.let { broadcast ->
                        val message = "find * ${interfaceAddress.address.hostAddress} ${socket.localPort} 2"
                        val sendData = message.toByteArray()
                        val sendPacket = DatagramPacket(sendData, sendData.size, broadcast, discoveryPort)
                        socket.send(sendPacket)
                    }
                }

        socket.soTimeout = maxDiscoveryTimeMs / 4
        val packet = DatagramPacket(ByteArray(maxPacketSize), maxPacketSize)
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxDiscoveryTimeMs) {
            try {
                socket.receive(packet)
                // val message = String(packet.data, Charset.defaultCharset()).trim { it <= ' ' }
                return packet.address.hostAddress
            } catch (e: SocketTimeoutException) {
                LOG.warn { "No brick found." }
            }
        }
    }
    return null
}


fun URL.readTextSupportGZIP(): String {
    try {
        val con = openConnection() as HttpURLConnection
        con.setRequestProperty("Accept-Encoding", "gzip")
        if ("gzip" == con.contentEncoding) {
            LOG.debug { "Able to read GZIP content" }
            InputStreamReader(GZIPInputStream(con.inputStream))
        } else {
            LOG.debug { "Able to read plain content" }
            InputStreamReader(con.inputStream)
        }.use { reader ->
            return reader.readText()
        }
    } catch (e: Exception) {
        LOG.error { e.toString() }
        return readText()
    }
}


fun getImage(fileName: String, res: Int = 500): BufferedImage {
    val resource = fileName.let {
        object {}.javaClass::class.java.getResource(it)
                ?: File("./client/src/main/resources/$it").toURI().toURL()!!
    }
    return Scalr.resize(ImageIO.read(resource)!!, Scalr.Method.ULTRA_QUALITY, res, res)!!
}

fun BufferedImage.getLum(x: Int, y: Int): Float {
    require(x in 0 until width) { "x:$x outside of $width x $height" }
    require(y in 0 until height) { "y:$y outside of $width x $height" }
    val color = getRGB(x, y)
    val red = color.ushr(16) and 0xFF
    val green = color.ushr(8) and 0xFF
    val blue = color.ushr(0) and 0xFF
    return (red * 0.2126f + green * 0.7152f + blue * 0.0722f) / 255
}

fun BufferedImage.getLum(loc: Vector2D): Float = getLum(loc.ix, loc.iy)

fun isqrt(v: Int) = Math.sqrt(v.toDouble()).toInt()

fun Vector2D(x: Int, y: Int) = Vector2D(x.toDouble(), y.toDouble())
