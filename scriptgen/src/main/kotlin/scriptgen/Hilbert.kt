package scriptgen

import info.benjaminhill.wbb.NormalVector2D
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D

/**
 *
 * https://en.wikipedia.org/wiki/Moore_curve
 *
 * Alphabet: L, R, Constants: F, +, −
 * Axiom: LFL+F+LFL
 * Production rules:
 * L → −RF+LFL+FR−
 * R → +LF−RFR−FL+
 * Here, F means "draw forward", − means "turn left 90°", and + means "turn right 90°"
 */
class Hilbert(fileName: String, private val maxDepth: Int) : AbstractImageToScaleFree(fileName) {

    private var headingDeg = 0
    private var location = Vector2D(0.0, 0.0)
    private val locationHistory = mutableListOf<Vector2D>()

    private var hopSize = 1.0

    /** Axiom: LFL+F+LFL */
    fun run() {
        doA(maxDepth)
        val normalizedLocationHistory = NormalVector2D.normalizePoints(locationHistory)
        hopSize = normalizedLocationHistory[0].distance(normalizedLocationHistory[1])

        val normalScriptWithX = mutableListOf<NormalVector2D>()
        normalizedLocationHistory.forEach { loc ->
            normalScriptWithX.add(loc)
            normalScriptWithX.addAll(drawX(loc))
            normalScriptWithX.add(loc)
        }

        LOG.info { "With all squiggles: ${normalScriptWithX.size}" }
        val noDupes = removeDuplicates(normalScriptWithX)
        LOG.info { "noDupes: ${noDupes.size}" }
        script.addAll(NormalVector2D.normalizePoints(noDupes))
    }

    private fun drawX(loc: NormalVector2D): MutableList<NormalVector2D> {
        val result = mutableListOf<NormalVector2D>()
        val ink = getInk(loc)
        val xLegSize = Math.sqrt(hopSize * hopSize + hopSize * hopSize)
        listOf(-1, 1).forEach { x ->
            listOf(-1, 1).forEach { y ->
                val squiggled = loc.add(Vector2D(x * ink * xLegSize, y * ink * xLegSize))
                if (NormalVector2D.isNormal(squiggled)) {
                    result.add(NormalVector2D.toNormal(squiggled))
                }
            }
        }
        return result
    }

    private fun execute(c: Char, depth: Int) {
        if (depth <= 0) {
            return
        }
        when (c) {
            'L' -> doL(depth)
            'R' -> doR(depth)
            'F' -> doF()
            '-' -> headingDeg -= 90
            '+' -> headingDeg += 90
            else -> throw IllegalArgumentException("$c")
        }
    }

    /** Axiom */
    private fun doA(depth: Int) {
        "LFL+F+LFL".forEach { c ->
            execute(c, depth)
        }
    }


    private fun doL(depth: Int) {
        "-RF+LFL+FR-".forEach { c ->
            execute(c, depth - 1)
        }
    }

    private fun doR(depth: Int) {
        "+LF-RFR-FL+".forEach { c ->
            execute(c, depth - 1)
        }
    }

    private fun doF() {
        val rad = Math.toRadians(headingDeg.toDouble())
        location = location.add(Vector2D(Math.cos(rad), Math.sin(rad)))
        //LOG.info { "$location -> $nextLocation" }
        locationHistory.add(location)
    }
}

fun main() = Hilbert("sundar5.png", 7).use { it.run() }
