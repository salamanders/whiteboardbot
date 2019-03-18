package info.benjaminhill.wbb

import lejos.hardware.Battery
import lejos.hardware.Button
import lejos.hardware.lcd.LCD
import lejos.hardware.motor.EV3LargeRegulatedMotor
import lejos.hardware.port.MotorPort
import lejos.robotics.geometry.Point2D
import mu.KotlinLogging

/**
 * The drawing area is a 1x1 square
 * "forwards" (positive tacho) unrolls and extends string, "backwards" retracts
 * Position state is stored in the actual tachoCounts of each motor
 * Calibration is number of tachos along top edge, and fully retracted is the upper-left or right corner
 */
class Plotter : AutoCloseable {
    // Starts uncalibrated
    private val spoolLeft = EV3LargeRegulatedMotor(MotorPort.A)
    private val spoolRight = EV3LargeRegulatedMotor(MotorPort.D)

    /**
     * Get the distance (Also the scale factor of the drawing)
     * Resets the tachos so location is accurate.
     */
    private val edgeTachoCount: Double

    init {
        LOG.info { "Power: ${Battery.getVoltageMilliVolt()}" }
        LCD.clear()

        println("Mark Upper Left")
        check(manualMove()) { "Bailed on UL calibration." }
        spoolLeft.resetTachoCount()

        println("Find Upper Right")
        check(manualMove()) { "Bailed on UR calibration." }
        spoolRight.resetTachoCount()

        edgeTachoCount = spoolLeft.tachoCount.toDouble()
        spoolLeft.synchronizeWith(arrayOf(spoolRight))

        LOG.info { "Drawing scale: edge:$edgeTachoCount" }
        LOG.info { "Start len: ${spoolLeft.tachoCount}, ${spoolRight.tachoCount}" }
        LOG.info { "Start location:${location.str} (should be UR {1.0,0.0})" }
    }

    /**
     * Required to be normalized 0.0..1.0 plotter location
     */
    var location: Point2D.Double
        get() {
            val normalStrLenLeft = spoolLeft.tachoCount / edgeTachoCount
            val normalStrLenRight = spoolRight.tachoCount / edgeTachoCount
            return normalHypotToXY(normalStrLenLeft, normalStrLenRight)
        }
        set(normalLoc) {
            val (currentNormalLenLeft, currentNormalLenRight) = normalXYToHypot(location)
            val (targetNormalLenLeft, targetNormalLenRight) = normalXYToHypot(normalLoc)

            val deltaNormalLeft = Math.abs(targetNormalLenLeft - currentNormalLenLeft)
            val deltaNormalRight = Math.abs(targetNormalLenRight - currentNormalLenRight)

            LCD.setPixel((normalLoc.x * LCD.SCREEN_WIDTH).toInt(), (normalLoc.y * LCD.SCREEN_HEIGHT).toInt(), 1)

            // Diagonals should arrive at the same time.
            // 1. max out the speeds
            spoolLeft.speed = MAX_SPEED
            spoolRight.speed = MAX_SPEED

            // Slow down the one that moves a shorter distance
            if (deltaNormalLeft < deltaNormalRight) {
                spoolLeft.speed = (MAX_SPEED * (deltaNormalLeft / deltaNormalRight)).toInt()
            }
            if (deltaNormalLeft > deltaNormalRight) {
                spoolRight.speed = (MAX_SPEED * (deltaNormalRight / deltaNormalLeft)).toInt()
            }

            val targetTachoLeft = (targetNormalLenLeft * edgeTachoCount).toInt()
            val targetTachoRight = (targetNormalLenRight * edgeTachoCount).toInt()

            check(targetTachoLeft in 0..(edgeTachoCount * 2).toInt())
            check(targetTachoRight in 0..(edgeTachoCount * 2).toInt())

            LOG.debug { "MOVE ${location.str} to ${normalLoc.str}; left: ${spoolLeft.tachoCount} to $targetTachoLeft; right: ${spoolRight.tachoCount} to $targetTachoRight" }
            spoolLeft.startSynchronization()

            spoolLeft.rotateTo(targetTachoLeft)
            spoolRight.rotateTo(targetTachoRight)

            spoolLeft.endSynchronization()

            spoolLeft.waitComplete()
            spoolRight.waitComplete() // TODO: Account for different acceleration.  OR maybe wait for the first?
        }

    /** Ignores tacho limits.
     * @return true if OK to continue
     */
    private fun manualMove(): Boolean {
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

    override fun toString(): String = "Plotter state: {loc:${location.str}, leftT:${spoolLeft.tachoCount}, rightT:${spoolRight.tachoCount}} "

    companion object {
        private val LOG = KotlinLogging.logger {}

        private const val MAX_SPEED = 360

        /**
         * Normalized
         * @link https://www.marginallyclever.com/2012/02/drawbot-overview/ for diagram
         */
        fun normalXYToHypot(target: Point2D.Double): Pair<Double, Double> {

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
        fun normalHypotToXY(hypotenuseLeft: Double, hypotenuseRight: Double): Point2D.Double {
            require(hypotenuseLeft >= 0) { "normalizedStringsToXY !normal hypotenuseLeft: ${hypotenuseLeft.str}" }
            require(hypotenuseLeft < 1.5) { "normalizedStringsToXY !normal hypotenuseLeft: ${hypotenuseLeft.str}" }
            require(hypotenuseRight >= 0) { "normalizedStringsToXY !normal hypotenuseRight: ${hypotenuseRight.str}" }
            require(hypotenuseRight < 1.5) { "normalizedStringsToXY !normal hypotenuseRight: ${hypotenuseRight.str}" }

            val topEdge = 1.0
            require(topEdge <= hypotenuseLeft + hypotenuseRight) { "Not a happy triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
            require(hypotenuseLeft <= topEdge + hypotenuseRight) { "Not a happy triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }
            require(hypotenuseRight <= topEdge + hypotenuseLeft) { "Not a happy triangle: 1.0, ${hypotenuseLeft.str}, ${hypotenuseRight.str}" }

            val s = (topEdge + hypotenuseLeft + hypotenuseRight) / 2
            val y = Math.sqrt(s * (s - topEdge) * (s - hypotenuseLeft) * (s - hypotenuseRight)) / (0.5 * topEdge)
            val x = Math.sqrt(hypotenuseLeft * hypotenuseLeft - y * y)

            return Point2D.Double(x, y).also { it.checkNormal() }
        }
    }
}