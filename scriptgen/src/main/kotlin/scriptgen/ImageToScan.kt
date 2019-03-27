package scriptgen

import info.benjaminhill.wbb.NormalVector2D

class ImageToScan(fileName: String) : ImageToX(fileName) {

    fun run() {
        val xStep = 5.0 / 1000
        val yStep = 3.0 / 1000

        var x = xStep
        var y = yStep
        var dir = 1

        while (x < 1.0) {
            y += yStep * dir
            // Bounce off top/bottom
            when {
                y <= yStep && dir == -1 -> {
                    x += xStep
                    dir = 1
                }
                y >= 1.0 - yStep && dir == 1 -> {
                    x += xStep
                    dir = -1
                }
            }
            val loc = NormalVector2D(x, y)
            val inkLevel = listOf(
                    getInk(loc),
                    getInk(NormalVector2D(loc.x + xStep / 3, loc.y)),
                    getInk(NormalVector2D(loc.x - xStep / 3, loc.y))
            ).average().toFloat()
            val isInside = inkLevel > 0.00001f

            if (isInside) {
                script.add(loc)
                val squiggleSize = (xStep * inkLevel) * .4

                script.add(loc.addN(NormalVector2D(squiggleSize, 0.0)))
                script.add(loc.addN(NormalVector2D(squiggleSize, yStep * dir)))
                script.add(loc.addN(NormalVector2D(-squiggleSize, yStep * dir)))
                script.add(loc.addN(NormalVector2D(-squiggleSize, yStep * dir * 2)))
                script.add(loc.addN(NormalVector2D(0.0, yStep * dir * 2)))

            }
        }
    }
}


fun main() = ImageToScan("shark2.png").use { it.run() }
