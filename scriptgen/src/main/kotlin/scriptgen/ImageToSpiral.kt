package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D


class ImageToSpiral(fileName: String, private val numberOfSpins: Int) : ImageToX(fileName) {

    fun run() {
        val res = 500 // for distinct points
        val center = NormalVector2D(.5, .5)

        val spiralPoints = unitSpiral(numberOfSpins).map {
            it.add(center)
        }.filter {
            NormalVector2D.isNormal(it)
        }.map {
            NormalVector2D.toNormal(it)
        }.distinctBy { (it.x * res).toInt() to (it.y * res).toInt() }

        val spaceBetweenSpins = center.norm / numberOfSpins
        LOG.info { "Plotting ${spiralPoints.size} points, with gap $spaceBetweenSpins" }

        // Do the spiral, jog to center when you find darkness
        spiralPoints.forEach { a ->
            // "Real" would be to average pixel lum in the little pie slice.  Meh.
            script.add(a)
            val ink = getInk(a)
            if (ink > .01) {
                val squiggle = a.subtract(NormalVector2D(.5, .5)).normalize().scalarMultiply(spaceBetweenSpins * ink).add(a)
                if (NormalVector2D.isNormal(squiggle)) {
                    script.add(NormalVector2D.toNormal(squiggle))
                }
            }

        }
        /*



                    val inputDark =
                    if (inputDark > 0.0001) {
                        val squigToCenter = a.subtract(center).normalize().negate().scalarMultiply(inputDark * spaceBetweenSpins)
                        val squigged = NormalVector2D.toNormal(a.add(squigToCenter))
                        script.add(squigged)
                    }
                }

         */
    }

    companion object {
        /**
         * -1..1
         */
        private fun unitSpiral(numberOfSpins: Int): List<Vector2D> {
            val radiusIncreasePerSpin = 1.0 / numberOfSpins
            return (1 until numberOfSpins * 360).map { deg ->
                val spinPct = deg / 360.0
                val radius = spinPct * radiusIncreasePerSpin
                val rad = Math.toRadians(deg.toDouble())
                Vector2D(Math.cos(rad), Math.sin(rad)).scalarMultiply(radius)
            }
        }
    }

}


fun main() {
    ImageToSpiral("whale.png", 200).use { it.run() }
}