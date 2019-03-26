package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D


class ImageToSpiral(fileName: String, private val spaceBetweenSpins: Double = 5.0) : ImageToX(fileName) {

    override fun run() {
        val hopSize = 2
        val maxRadius = Math.sqrt(center.x * center.x + center.y * center.y)
        val spiralPoints = spiral(maxRadius, spaceBetweenSpins)
        LOG.info { "Plotting ${spiralPoints.size} points" }

        spiralPoints
                .map { it.add(center) }
                .distinctBy { it.ix / hopSize to it.iy / hopSize }
                .filter { it.ix in 0 until inputImage.width && it.iy in 0 until inputImage.height }
                .zipWithNext { a, b ->
                    //outputG2d.drawLine(a.ix, a.iy, b.ix, b.iy)
                    // "Real" would be to average pixel lum in the little pie slice.  Meh.
                    // Jog to center
                    val inputDark = 1 - inputImage.getLum(a).toDouble()
                    val squigToCenter = a.subtract(center).normalize().negate().scalarMultiply(inputDark * spaceBetweenSpins)
                    val squigged = a.add(squigToCenter)
                    script.add(a)
                    script.add(squigged)
                }
    }

    companion object {
        /**
         * https://stackoverflow.com/questions/48492980/drawing-an-archimedean-spiral-with-lines-and-tightness-parameters-using-java
         * Centered at 0,0
         * "Compresses" by distinct integer pixel locations
         */
        private fun spiral(maxRadius: Double, spaceBetweenSpins: Double): List<Vector2D> {
            val numberOfSpins = maxRadius / spaceBetweenSpins
            val numSegments = (2 * Math.PI * maxRadius * numberOfSpins).toInt() // Excessive
            val radiusDelta = maxRadius / numSegments

            var radius = 0.0
            return (0 until numSegments).map { segmentNum ->
                radius += radiusDelta
                Vector2D(
                        radius * Math.cos(2.0 * Math.PI * segmentNum.toDouble() / numSegments * numberOfSpins),
                        radius * Math.sin(2.0 * Math.PI * segmentNum.toDouble() / numSegments * numberOfSpins))
            }
        }
    }

}


fun main() {
    ImageToSpiral("whale.png").use {
        it.run()
    }
}