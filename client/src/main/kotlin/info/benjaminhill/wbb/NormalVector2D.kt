package info.benjaminhill.wbb

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D

/** Extra check to make sure a sane normal vector */
class NormalVector2D(x: Double, y: Double) : Vector2D(x, y) {

    init {

            require(x.isFinite() && y.isFinite()) { "Bad NormalVector2D($x, $y)" }
            require(x > -0.1 &&
                    x < 1.1 &&
                    y > -0.1 &&
                    y < 1.1
            ) { "non normal point: $x x $y" }

    }

    fun toJSON(): String = "{\"x\":${x.str}, \"y\":${y.str}}"
}