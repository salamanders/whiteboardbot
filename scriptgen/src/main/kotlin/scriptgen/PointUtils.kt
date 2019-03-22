package scriptgen

import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import org.imgscalr.Scalr
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

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

fun getImage(fileName: String, res: Int = 500): BufferedImage {
    val resource = fileName.let {
        object {}.javaClass::class.java.getResource(it)
                ?: File("src/main/resources/$it").toURI().toURL()!!
    }
    return Scalr.resize(ImageIO.read(resource)!!, Scalr.Method.ULTRA_QUALITY, res, res)!!
}

fun BufferedImage.getLum(loc: Vector2D): Float = getLum(loc.ix, loc.iy)

fun BufferedImage.getLum(x: Int, y: Int): Float {
    require(x in 0 until width) { "x:$x outside of $width x $height" }
    require(y in 0 until height) { "y:$y outside of $width x $height" }
    val color = getRGB(x, y)
    val red = color.ushr(16) and 0xFF
    val green = color.ushr(8) and 0xFF
    val blue = color.ushr(0) and 0xFF
    return (red * 0.2126f + green * 0.7152f + blue * 0.0722f) / 255
}

/** Shorter round for the logs */
val Double.str: String
    get() = "%.3f".format(this)



fun Vector2D.toJSON(): String = "{\"x\":${x.str}, \"y\":${y.str}}"