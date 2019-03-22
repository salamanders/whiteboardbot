import info.benjaminhill.wbb.getBrickIPAddress
import info.benjaminhill.wbb.readTextSupportGZIP
import java.net.URL


fun main() {
    println(URL("https://whiteboardbot.firebaseapp.com/config.json").readTextSupportGZIP())

    println(getBrickIPAddress())
}