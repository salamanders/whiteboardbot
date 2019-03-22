package info.benjaminhill.wbb

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D

import kotlin.math.roundToInt

/** Extra check to make sure a sane normal vector */
class NormalizedVector2D(x: Double, y: Double) : Vector2D(x, y) {

    init {
        requireNormal(this)
    }

    companion object {

        fun asNormalized(v: Vector2D): NormalizedVector2D {
            requireNormal(v)
            return v as NormalizedVector2D
        }

        fun requireNormal(v: Vector2D) {
            v.apply {
                require(x.isFinite() && y.isFinite()) { "Bad NormalizedVector2D($x, $y)" }
                require(x > -0.1 &&
                        x < 1.1 &&
                        y > -0.1 &&
                        y < 1.1
                ) { "non normal point: $x x $y" }
            }
        }
    }
}


operator fun Vector2D.component1(): kotlin.Double = this.x
operator fun Vector2D.component2(): kotlin.Double = this.y

fun angleToVector2D(rad: Double) = Vector2D(Math.cos(rad), Math.sin(rad))

val Vector2D.ix: Int
    get() = x.roundToInt()

val Vector2D.iy: Int
    get() = y.roundToInt()

fun Vector2D.getPointsAlongLine(other: Vector2D): List<Vector2D> {
    val diff = other.subtract(this).normalize()!!
    val distance = this.distance(other)

    return (0..distance.toInt()).map {
        it / distance
    }.map { pct ->
        this.add(pct * distance, diff)
    }
}


fun Vector2D.toJSON(): String = "{\"x\":${x.str}, \"y\":${y.str}}"