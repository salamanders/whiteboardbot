package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D

/** Gravity based on remaining points to be drawn */
class ImageToSquiggle(fileName: String) : ImageToX(fileName) {


    override fun run() {
        val physics = sequence {
            var currentPoint = center
            var acceleration = Vector2D.ZERO!!
            var velocity = Vector2D.ZERO!!

            (0..1000).map {

            }

            yield(currentPoint)
        }


    }

}

fun main() {
    ImageToSquiggle("xwing.png").use {
        it.run()
    }

}