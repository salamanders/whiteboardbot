package info.benjaminhill.wbb

import lejos.hardware.Battery
import lejos.hardware.Button
import lejos.hardware.lcd.LCD
import lejos.hardware.motor.EV3LargeRegulatedMotor
import lejos.hardware.port.MotorPort
import lejos.robotics.RegulatedMotor
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging

/** How many MS to reach a target at max speed */
fun RegulatedMotor.eta(targetTachoCount: Int) = (Math.abs(targetTachoCount - tachoCount) / maxSpeed)

/** To aim for the target goalMs, may move slower than max speed */
fun RegulatedMotor.rotateSlowlyTo(targetTachoCount: Int, goalMs: Float) {
    val eta = eta(targetTachoCount)
    speed = when {
        eta > goalMs + 500 -> {
            LOG.warn { "Asking motor to go faster than it can (eta:${eta.toInt()}, goalMs:${goalMs.toInt()})" }
            maxSpeed
        }
        eta < 10 || goalMs < 100 -> {
            90f
        }
        else -> {
            maxSpeed * (eta / goalMs)
        }
    }.toInt()
    rotateTo(targetTachoCount, true)
}

/**
 * The drawing area is a 1x1 square
 * "forwards" (positive tacho) unrolls and extends string, "backwards" retracts
 * Position state is stored in the actual tachoCounts of each motor
 * Calibration is number of tachos along top edge
 * Start location assumed to be center, 0 tacho count
 */
class Plotter : AutoCloseable {
    // Starts uncalibrated
    private val spoolLeft = EV3LargeRegulatedMotor(MotorPort.A)
    private val spoolRight = EV3LargeRegulatedMotor(MotorPort.D)

    /**
     * Get the distance, leaving the plotter in center
     * Also the scale factor of the drawing
     */
    private val spoolTachoDistance: Double by lazy {
        println("Mark Upper Left")
        check(manualMove()) { "Bailed on UL calibration." }
        val tachoUL = Pair(spoolLeft.tachoCount, spoolRight.tachoCount)
        LOG.info { "tachoUL(0,1): ${tachoUL.first},${tachoUL.second}" }
        println("Find Upper Right")
        check(manualMove()) { "Bailed on UR calibration." }
        val tachoUR = Pair(spoolLeft.tachoCount, spoolRight.tachoCount)
        LOG.info { "tachoUR(0,1): ${tachoUR.first},${tachoUR.second}" }
        val spool0TachoDelta = Math.abs(tachoUR.first - tachoUL.first)
        val spool1TachoDelta = Math.abs(tachoUL.second - tachoUR.second)
        val squareEdgeTacho = Math.min(spool0TachoDelta, spool1TachoDelta).toDouble()
        val halfDiagonalTacho = Math.sqrt(2 * squareEdgeTacho * squareEdgeTacho) / 2

        assert(halfDiagonalTacho > squareEdgeTacho)

        // Move to center of drawing and reset
        spoolRight.rotate(halfDiagonalTacho.toInt()) // Careful!  Blocking to extend first
        spoolLeft.rotate(-(squareEdgeTacho - halfDiagonalTacho).toInt())

        spoolLeft.resetTachoCount()
        spoolRight.resetTachoCount()

        squareEdgeTacho
    }


    init {
        LOG.info { "Power: ${Battery.getVoltageMilliVolt()}" }
        LCD.clear()

        LOG.info { "Drawing scale: edge:${spoolTachoDistance.toInt()}" }
        LOG.info { "Start location:${location.str} (should = {.5,.5})" }

        spoolLeft.synchronizeWith(arrayOf(spoolRight))
    }

    /**
     * Required to be normalized 0..1 plotter location
     */
    var location: Point2D.Double
        get() {
            val normalStrLenLeft = spoolLeft.tachoCount / spoolTachoDistance + 0.5
            val normalStrLenRight = spoolRight.tachoCount / spoolTachoDistance + 0.5
            return normalizedStringsToXY(normalStrLenLeft, normalStrLenRight)
        }
        set(normalLoc) {
            val (targetNormalLengthLeft, targetNormalLengthRight) = normalizedXYToStrings(normalLoc)
            LCD.setPixel((normalLoc.x * LCD.SCREEN_WIDTH).toInt(), (normalLoc.y * LCD.SCREEN_HEIGHT).toInt(), 1)

            val targetTachoLengthLeft = ((targetNormalLengthLeft - 0.5) * spoolTachoDistance).toInt()
            val targetTachoLengthRight = ((targetNormalLengthRight - 0.5) * spoolTachoDistance).toInt()

            LOG.debug { "Moving from ${location.str} to ${normalLoc.str} l0:$targetTachoLengthLeft l1:$targetTachoLengthRight" }

            val longestTimeToMove = Math.max(
                    spoolLeft.eta(targetTachoLengthLeft),
                    spoolRight.eta(targetTachoLengthRight)
            )
            spoolLeft.startSynchronization()
            spoolLeft.rotateSlowlyTo(targetTachoLengthLeft, longestTimeToMove)
            spoolRight.rotateSlowlyTo(targetTachoLengthRight, longestTimeToMove)
            spoolLeft.endSynchronization()

            spoolLeft.waitComplete()
            spoolRight.waitComplete() // TODO: Account for different acceleration.  OR maybe wait for the first?
        }

    /** Ignores tacho limits.
     * @return true if OK to continue
     */
    private fun manualMove(): Boolean {
        val adjustDegrees = 360 * 2
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

    override fun toString(): String = "Plotter state: {loc:${location.str}, l0:${spoolLeft.tachoCount}, l1:${spoolRight.tachoCount}} "

    companion object {
        private val LOG = KotlinLogging.logger {}
        /**
         * Normalized
         * @link https://www.marginallyclever.com/2012/02/drawbot-overview/ for diagram
         */
        fun normalizedXYToStrings(target: Point2D.Double): Pair<Double, Double> {
            target.checkNormal()

            val hypotenuseLeft = Math.sqrt(target.x * target.x + target.y * target.y)
            val xb = 1.0 - target.x  // same as V-M2 in the picture
            val hypotenuseRight = Math.sqrt(xb * xb + target.y * target.y)

            check(hypotenuseLeft >= 0) { "normalizedXYToStrings unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseLeft < 1.5) { "normalizedXYToStrings unexpected hypotenuseLeft:${hypotenuseLeft.str}" }
            check(hypotenuseRight >= 0) { "normalizedXYToStrings unexpected hypotenuseRight:${hypotenuseLeft.str}" }
            check(hypotenuseRight < 1.5) { "normalizedXYToStrings unexpected hypotenuseRight:${hypotenuseRight.str}" }

            return hypotenuseLeft to hypotenuseRight
        }

        /**
         * Heron's formula https://www.wikihow.com/Find-the-Height-of-a-Triangle
         */
        fun normalizedStringsToXY(hypotenuseLeft: Double, hypotenuseRight: Double): Point2D.Double {
            require(hypotenuseLeft >= 0) { "normalizedStringsToXY !normal hypotenuseLeft: ${hypotenuseLeft.str}" }
            require(hypotenuseLeft < 1.5) { "normalizedStringsToXY !normal hypotenuseLeft: ${hypotenuseLeft.str}" }
            require(hypotenuseRight >= 0) { "normalizedStringsToXY !normal hypotenuseRight: ${hypotenuseRight.str}" }
            require(hypotenuseRight < 1.5) { "normalizedStringsToXY !normal hypotenuseRight: ${hypotenuseRight.str}" }

            val topEdge = 1.0
            require(topEdge <= hypotenuseLeft + hypotenuseRight)
            require(hypotenuseLeft <= topEdge + hypotenuseRight)
            require(hypotenuseRight <= topEdge + hypotenuseLeft)

            val s = (topEdge + hypotenuseLeft + hypotenuseRight) / 2
            val y = Math.sqrt(s * (s - topEdge) * (s - hypotenuseLeft) * (s - hypotenuseRight)) / (0.5 * topEdge)
            val x = Math.sqrt(hypotenuseLeft * hypotenuseLeft - y * y)

            return Point2D.Double(x, y).also { it.checkNormal() }
        }
    }
}