package scriptgen

import info.benjaminhill.wbb.NormalVector2D

fun main() {
    Hilbert("xwing6.png").use { it.run() }
}

class Hilbert(fileName: String) : ImageToX(fileName) {
    fun run() {
        val maxDepth = 7
        recurse(maxDepth, 90)
    }

    private var heading = 0
    private val stepSize = 2.0
    private var location = NormalVector2D(.5, .5)

    private fun rotate(deg: Int) {
        heading += deg
    }

    private fun moveForward() {
        val rad = Math.toRadians(heading.toDouble())
        val offset = angleToVector2D(rad).scalarMultiply(stepSize)
        val nextLocation = location.add(offset)
        LOG.info { "$location -> $nextLocation" }
        script.add(location)
        location = NormalVector2D.toNormal(nextLocation)
    }

    // https://stackoverflow.com/questions/43230399/draw-a-hilbert-curve-by-recursion
    private fun recurse(depth: Int, angle: Int) {
        if (depth <= 0) {
            return
        }
        rotate(-angle)
        recurse(depth - 1, -angle)
        moveForward()
        rotate(angle)
        recurse(depth - 1, angle)
        moveForward()
        recurse(depth - 1, angle)
        rotate(angle)
        moveForward()
        recurse(depth - 1, -angle)
        rotate(-angle)
    }
}
