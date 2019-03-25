package info.benjaminhill.wbb

import info.benjaminhill.wbb.NormalVector2D.Companion.checkDiagonalsNormal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import lejos.hardware.Battery
import lejos.hardware.Button
import lejos.hardware.lcd.LCD
import lejos.hardware.motor.EV3LargeRegulatedMotor
import lejos.hardware.port.MotorPort
import mu.KotlinLogging
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D
import kotlin.coroutines.CoroutineContext

/**
 * The drawing area is a 1.0 x 1.0 square
 * "forwards" (positive tacho) unrolls and extends string, "backwards" retracts
 * Position state is stored in the actual tachoCounts of each motor (degrees rotated)
 * Calibration is number of tachos along top edge, and fully retracted is the upper-left or right corner
 * Starts uncalibrated, only able to uncalibratedMove turn spools
 */
class Plotter : AutoCloseable, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = job + backgroundPool

    private val spoolLeft = EV3LargeRegulatedMotor(MotorPort.A).apply {
        speed = MAX_SPEED
    }
    private val spoolRight = EV3LargeRegulatedMotor(MotorPort.D).apply {
        speed = MAX_SPEED
    }

    /**
     * Get the distance (Also the scale factor of the drawing)
     * Resets the tachos so location is accurate.
     */
    private val edgeTachoCount: Double

    init {
        LOG.info { "Power: ${Battery.getVoltageMilliVolt()}" }
        LCD.clear()

        println("Mark Upper Left")
        check(uncalibratedMove()) { "Bailed on UL calibration." }
        spoolLeft.resetTachoCount()

        println("Find Upper Right")
        check(uncalibratedMove()) { "Bailed on UR calibration." }
        spoolRight.resetTachoCount()

        edgeTachoCount = spoolLeft.tachoCount.toDouble()
        // spoolLeft.synchronizeWith(arrayOf(spoolRight))

        LOG.info { "Drawing scale: edge:$edgeTachoCount" }
        LOG.info { "Start len: ${spoolLeft.tachoCount}, ${spoolRight.tachoCount}" }
        LOG.info { "Start location:$location (UR:{1,0})" }
    }

    /**
     * Required to be normalized 0.0..1.0 plotter location
     */
    var location: NormalVector2D
        get() {
            val normalStrLenLeft = spoolLeft.tachoCount / edgeTachoCount
            val normalStrLenRight = spoolRight.tachoCount / edgeTachoCount
            return hypotToXY(normalStrLenLeft, normalStrLenRight)
        }
        set(normalLoc) {
            LCD.setPixel((normalLoc.x * LCD.SCREEN_WIDTH).toInt(), (normalLoc.y * LCD.SCREEN_HEIGHT).toInt(), 1)
            LOG.debug { "location:($location to $normalLoc)" }
            asyncMoveToLocation(normalLoc)
            spoolLeft.waitComplete()
            spoolRight.waitComplete() // TODO: Account for different acceleration.  OR maybe wait for the first?
        }

    /**
     * Race around the points, never stopping.
     * Short hops reduce curve issues.
     * Better hope you don't overshoot and wobble forever
     * TODO: Slow down if very close
     */
    fun fastDraw(points: List<NormalVector2D>) = runBlocking {

        points.forEachIndexed { idx, targetLocation ->
            var timeOfLastLog: Long = 0
            var moveLoops = 0
            do {
                moveLoops++

                val remainingMove = targetLocation.subtract(location)
                val remainingDist = remainingMove.norm

                // Look max of 1/10th of the board away to help with bad curves
                val shortMove = if (remainingDist > 10 * CLOSE_TO_TARGET) {
                    remainingMove.normalize().scalarMultiply(10 * CLOSE_TO_TARGET)
                } else {
                    remainingMove
                }

                // Early check to avoid the last delay
                if (remainingDist < CLOSE_TO_TARGET) {
                    break
                }

                val shortTarget = location.add(shortMove)
                asyncMoveToLocation(shortTarget)
                System.currentTimeMillis().let { now ->

                    // First time and every 3 seconds thereafter
                    if (now - timeOfLastLog > 3_000) {
                        timeOfLastLog = now

                        LOG.debug {
                            "FMOVE $idx (loop:$moveLoops)," +
                                    " from:$location to:$targetLocation," +
                                    " dist:${remainingDist.str}," +
                                    " lspeed:${spoolLeft.speed} rspeed:${spoolRight.speed}," +
                                    " ltacho:${spoolLeft.tachoCount} to ${spoolLeft.limitAngle} " +
                                    " rtacho:${spoolRight.tachoCount} to ${spoolRight.limitAngle}"
                        }
                    }
                }
                delay(50)

                // TODO: Better way of sensing overshoots
            } while (remainingDist > CLOSE_TO_TARGET)
        }
        LOG.info { "FMOVE completed all ${points.size} steps" }
        spoolLeft.flt()
        spoolRight.flt()
    }

    /**
     * Doesn't wait for the move to end
     * @param target will be normalized
     */
    private fun asyncMoveToLocation(target: Vector2D) {
        // Guaranteed to be normalized

        val (currentNormalLenLeft, currentNormalLenRight) = xyToHypot(location)
        val (targetNormalLenLeft, targetNormalLenRight) = xyToHypot(target)
        val deltaNormalLeft = Math.abs(targetNormalLenLeft - currentNormalLenLeft)
        val deltaNormalRight = Math.abs(targetNormalLenRight - currentNormalLenRight)

        // Slow down the one closer to the goal so diagonals arrive at the same time.
        when {
            deltaNormalLeft < deltaNormalRight -> {
                spoolLeft.speed = (MAX_SPEED * (deltaNormalLeft / deltaNormalRight)).toInt()
                spoolRight.speed = MAX_SPEED
            }
            deltaNormalLeft > deltaNormalRight -> {
                spoolLeft.speed = MAX_SPEED
                spoolRight.speed = (MAX_SPEED * (deltaNormalRight / deltaNormalLeft)).toInt()
            }
            else -> {
                spoolLeft.speed = MAX_SPEED
                spoolRight.speed = MAX_SPEED
            }
        }
        val targetTachoLeft = (targetNormalLenLeft * edgeTachoCount).toInt()
        val targetTachoRight = (targetNormalLenRight * edgeTachoCount).toInt()

        // Hyperextension (running out of string) is bad.  So is grinding gears.
        check(targetTachoLeft in 0..(edgeTachoCount * 1.5).toInt())
        check(targetTachoRight in 0..(edgeTachoCount * 1.5).toInt())

        //spoolLeft.startSynchronization()
        spoolLeft.rotateTo(targetTachoLeft, true)
        spoolRight.rotateTo(targetTachoRight, true)
        //spoolLeft.endSynchronization()
    }


    /**
     * Ignores tacho limits.
     * @return true if OK to continue
     */
    private fun uncalibratedMove(): Boolean {
        val adjustDegrees = 360
        while (true) {
            when (Button.waitForAnyPress()) {
                Button.ID_ENTER -> {
                    spoolLeft.waitComplete()
                    spoolRight.waitComplete()
                    return true
                }
                Button.ID_UP -> {
                    spoolLeft.rotate(-adjustDegrees, true)
                    spoolRight.rotate(-adjustDegrees, true)
                }
                Button.ID_LEFT -> {
                    spoolLeft.rotate(-adjustDegrees, true)
                    spoolRight.rotate(adjustDegrees, true)
                }
                Button.ID_RIGHT -> {
                    spoolLeft.rotate(adjustDegrees, true)
                    spoolRight.rotate(-adjustDegrees, true)
                }
                Button.ID_DOWN -> {
                    spoolLeft.rotate(adjustDegrees, true)
                    spoolRight.rotate(adjustDegrees, true)
                }
                Button.ID_ESCAPE -> {
                    // Allows emergency bail
                    spoolLeft.flt(true)
                    spoolRight.flt(true)
                    return false
                }
            }
        }
    }

    override fun close() {
        LOG.debug { "Plotter closing normally." }
        setOf(spoolLeft, spoolRight).forEach {
            try {
                it.stop()
            } catch (e: Throwable) {
                LOG.error { "Error stopping motor:$e" }
            }
            try {
                it.close()
            } catch (e: Throwable) {
                LOG.error { "Error closing motor:$e" }
            }
        }
    }

    override fun toString(): String = "Plotter state: {loc:$location, leftT:${spoolLeft.tachoCount}, rightT:${spoolRight.tachoCount}} "

    companion object {
        private val LOG = KotlinLogging.logger {}

        private const val MAX_SPEED = 180 // Starts getting loud > 180.
        private const val CLOSE_TO_TARGET = 0.005

        /**
         * Normalized
         * @param target could be considered Normalised
         * @link https://www.marginallyclever.com/2012/02/drawbot-overview/ for diagram
         */
        fun xyToHypot(target: Vector2D): Pair<Double, Double> {
            NormalVector2D.checkNormal(target)
            val hypotenuseLeft = Math.sqrt(target.x * target.x + target.y * target.y)
            val xb = 1.0 - target.x  // same as V-M2 in the picture
            val hypotenuseRight = Math.sqrt(xb * xb + target.y * target.y)

            checkDiagonalsNormal(hypotenuseLeft, hypotenuseRight)

            return hypotenuseLeft to hypotenuseRight
        }

        /**
         * Heron's formula https://www.wikihow.com/Find-the-Height-of-a-Triangle
         */
        fun hypotToXY(hypotenuseLeft: Double, hypotenuseRight: Double): NormalVector2D {
            checkDiagonalsNormal(hypotenuseLeft, hypotenuseRight)

            val topEdge = 1.0
            require(topEdge <= hypotenuseLeft + hypotenuseRight + 0.05) { "Not a happy T triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
            require(hypotenuseLeft <= topEdge + hypotenuseRight + 0.05) { "Not a happy L triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
            require(hypotenuseRight <= topEdge + hypotenuseLeft + 0.05) { "Not a happy R triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }

            if (hypotenuseLeft + hypotenuseRight <= topEdge) {
                LOG.warn { "hypotToXY skirting the top: ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
                return NormalVector2D(hypotenuseLeft, 0.0)
            }

            if (hypotenuseRight > Math.sqrt(hypotenuseLeft * hypotenuseLeft + 1)) {
                LOG.warn { "hypotToXY skirting the left: ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
                return NormalVector2D(0.0, hypotenuseLeft)
            }

            if (hypotenuseLeft > Math.sqrt(hypotenuseRight * hypotenuseRight + 1)) {
                LOG.warn { "hypotToXY skirting the right: ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
                return NormalVector2D(1.0, hypotenuseRight)
            }

            val s = (topEdge + hypotenuseLeft + hypotenuseRight) / 2
            val y = Math.sqrt(s * (s - topEdge) * (s - hypotenuseLeft) * (s - hypotenuseRight)) / (0.5 * topEdge)
            val x = Math.sqrt(hypotenuseLeft * hypotenuseLeft - y * y)

            check(x.isFinite() && y.isFinite()) { "Non Finite hypotToXY($hypotenuseLeft,$hypotenuseRight) output ($x, $y)" }

            return NormalVector2D(x, y)
        }
    }
}