package info.benjaminhill.wbb

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import scriptgen.str

/** Extra check to make sure a sane normal vector */
class NormalVector2D(x: Double, y: Double) : Vector2D(x, y) {

    init {
        checkNormal(this)
    }

    override fun toString(): String = "{\"x\":${x.str}, \"y\":${y.str}}"

    // When the result should also be within normal range
    fun addN(v: NormalVector2D): NormalVector2D = NormalVector2D.toNormal(super.add(v))

    // When the result should also be within normal range
    fun subtractN(v: NormalVector2D): NormalVector2D = NormalVector2D.toNormal(super.subtract(v))

    // When the result should also be within normal range
    fun scalarMultiplyN(a: Double): NormalVector2D = NormalVector2D.toNormal(super.scalarMultiply(a))

    fun normalizeN() = NormalVector2D.toNormal(normalize())

    /** Don't know how many points to get.  How about 20 */
    fun getPointsAlongLine(other: NormalVector2D): List<NormalVector2D> {
        val diff = other.subtract(this).normalize()
        val distance = this.distance(other)
        return (0..20).map {
            it / 20.0
        }.map { pct ->
            NormalVector2D.toNormal(add(pct * distance, diff))
        }
    }


    companion object {

        /** A bit more wiggle room because diagonals can extend longer, but shouldn't go shorter */
        fun checkDiagonalsNormal(hypotenuseLeft: Double, hypotenuseRight: Double) {
            check(hypotenuseLeft >= -0.05) { "normalized string unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseLeft < 1.5) { "normalized string unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseRight >= -0.05) { "normalized string unexpected hypotenuseRight:${hypotenuseLeft.str}" }
            check(hypotenuseRight < 1.5) { "normalized string unexpected hypotenuseRight:${hypotenuseRight.str}" }
        }

        fun toNormal(p: Vector2D) = NormalVector2D(p.x, p.y)

        /** Scale proportionally to fit inside a unit sq, trimming to shape */
        fun normalizePoints(points: List<Vector2D>): List<NormalVector2D> {
            val globalMin = Vector2D(points.minBy { it.x }!!.x, points.minBy { it.y }!!.y)
            val globalMax = Vector2D(points.maxBy { it.x }!!.x, points.maxBy { it.y }!!.y)

            val xScale = 1 / (globalMax.x - globalMin.x)
            val yScale = 1 / (globalMax.y - globalMin.y)
            val scaleFactor = Math.min(xScale, yScale)

            val scaled = points.map {
                NormalVector2D.toNormal(it.subtract(globalMin).scalarMultiply(scaleFactor))
            }

            val scaledMax = NormalVector2D(scaled.maxBy { it.x }!!.x, scaled.maxBy { it.y }!!.y)
            val centeringOffset = NormalVector2D((1 - scaledMax.x) / 2, (1 - scaledMax.y) / 2)

            return scaled.map {
                it.addN(centeringOffset)
            }
        }

        private fun checkNormal(p: Vector2D) {
            require(p.x.isFinite() && p.y.isFinite()) { "Bad NormalVector2D($p.x, $p.y)" }
            require(p.x > -0.1 &&
                    p.x < 1.1 &&
                    p.y > -0.1 &&
                    p.y < 1.1
            ) { "non normal point: ${p.x} x ${p.y}" }
        }

        fun isNormal(p: Vector2D) = p.x.isFinite() &&
                p.y.isFinite() &&
                p.x >= 0 &&
                p.x < 1 &&
                p.y >= 0 &&
                p.y < 1
    }
}