package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D


class ImageToScan(fileName: String) : ImageToX(fileName, inputRes = 1000) {

    override fun run() {
        val tmpScript = mutableListOf<Vector2D>()

        val xStep = 5
        val yStep = 1

        var x = 0
        var y = 0
        var dir = 1
        var lastLum = 1.0f
        var isInside = false
        while (x in 0 until imageDimension.width) {
            y += yStep * dir
            val nextLum = inputImage.getLum(x, y)

            // Bounce off top/bottom
            when {
                y in 0..yStep && dir == -1 -> {
                    x += xStep
                    dir = 1
                }
                y in imageDimension.height - yStep..imageDimension.height && dir == 1 -> {
                    x += xStep
                    dir = -1
                }
            }
            if (!isInside && lastLum == 1f && nextLum < 1f) {
                isInside = true
            }
            if (isInside && lastLum < 1f && nextLum == 1f) {
                isInside = false
            }

            if (isInside) {
                val loc = Vector2D(x.toDouble(), y.toDouble())
                tmpScript.add(loc)

                val squiggleSize = (xStep - xStep * nextLum) / 2.0
                if (squiggleSize >= 1) {
                    tmpScript.add(loc.add(Vector2D(squiggleSize, 0.0)))
                    tmpScript.add(loc.add(Vector2D(squiggleSize, dir.toDouble())))
                    tmpScript.add(loc.add(Vector2D(-squiggleSize, dir.toDouble())))
                    tmpScript.add(loc.add(Vector2D(-squiggleSize, dir.toDouble() * 2)))
                    tmpScript.add(loc.add(Vector2D(0.0, dir.toDouble() * 2)))
                }
            }
            lastLum = nextLum
        }

        script.addAll(tmpScript)
    }
}


fun main() = ImageToScan("shark2.png").use { it.run() }
