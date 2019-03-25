package info.benjaminhill.wbb

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D

/** Extra check to make sure a sane normal vector */
class NormalVector2D(x: Double, y: Double) : Vector2D(x, y) {

    init {
        checkNormal(this)
    }

    override fun toString(): String = "{\"x\":${x.str}, \"y\":${y.str}}"

    companion object {

        /** A bit more wiggle room because diagonals can extend longer, but shouldn't go shorter */
        fun checkDiagonalsNormal(hypotenuseLeft: Double, hypotenuseRight: Double) {
            check(hypotenuseLeft >= -0.05) { "normalized string unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseLeft < 1.5) { "normalized string unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseRight >= -0.05) { "normalized string unexpected hypotenuseRight:${hypotenuseLeft.str}" }
            check(hypotenuseRight < 1.5) { "normalized string unexpected hypotenuseRight:${hypotenuseRight.str}" }
        }


        fun checkNormal(p: Vector2D) {
            require(p.x.isFinite() && p.y.isFinite()) { "Bad NormalVector2D($p.x, $p.y)" }
            require(p.x > -0.1 &&
                    p.x < 1.1 &&
                    p.y > -0.1 &&
                    p.y < 1.1
            ) { "non normal point: ${p.x} x ${p.y}" }
        }
    }
}