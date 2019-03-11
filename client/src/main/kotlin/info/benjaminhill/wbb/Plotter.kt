package info.benjaminhill.wbb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import lejos.hardware.Battery
import lejos.hardware.Button
import lejos.hardware.lcd.LCD
import lejos.hardware.motor.EV3LargeRegulatedMotor
import lejos.hardware.port.MotorPort
import lejos.robotics.BaseMotor
import lejos.robotics.RegulatedMotor
import lejos.robotics.geometry.Point2D
import lejos.robotics.geometry.Rectangle2D
import mu.KotlinLogging

/**
 * All measurements in ms and cm.  Interface to the outside world "location" is in 0..1
 * "forwards" (positive tacho) unrolls and extends string, "backwards" retracts
 * State is stored in the actual tachoCounts of each motor
 *
 * @param spoolDistanceCm Distance between the two motors
 */
class Plotter(
        private val spoolDistanceCm: Double
) : AutoCloseable {
    private val spool0: RegulatedMotor = EV3LargeRegulatedMotor(MotorPort.A)
    private val spool1: RegulatedMotor = EV3LargeRegulatedMotor(MotorPort.D)

    /** Assume tacho 0 means the pen is in the middle of the drawing area.  May need to move and reset to make it correct. */
    private val initialLengthCm = Math.sqrt(2 * (spoolDistanceCm / 2) * (spoolDistanceCm / 2))

    private val safeDrawingArea = Rectangle2D.Double(spoolDistanceCm * BUFFER, spoolDistanceCm * BUFFER, spoolDistanceCm * BUFFER, spoolDistanceCm * BUFFER)

    /** Degrees/Second */
    private val maxSpeed = Math.min(spool0.maxSpeed.toDouble(), 720.0).toInt()

    init {
        LOG.info("Power remaining: ${Math.min(100, (Battery.getVoltageMilliVolt() - 7000) / 10)}%")
        LOG.info { "Max Speed:$maxSpeed (min of actual max and 720 deg/sec)" }
        LCD.clear()
        spool0.synchronizeWith(arrayOf(spool1))
    }

    val spool0Length: Double
        get() = tachoToLength(spool0.tachoCount.toDouble())

    val spool1Length: Double
        get() = tachoToLength(spool1.tachoCount.toDouble())

    /**
     * Required to be normalized 0..1 plotter location
     */
    var location: Point2D.Double
        get() {
            val realLoc = stringsToXY(spoolDistanceCm, spool0Length, spool1Length)
            return Point2D.Double((realLoc.x - safeDrawingArea.x) / safeDrawingArea.width, (realLoc.y - safeDrawingArea.y) / safeDrawingArea.height)
        }
        set(normalLoc) {
            if (!Rectangle2D.Double(0.0, 0.0, 1.0, 1.0).contains(normalLoc)) {
                LOG.warn { "Rejecting non-normalized location: $normalLoc" }
                return
            }
            val realLoc = Point2D.Double(normalLoc.x * safeDrawingArea.width + safeDrawingArea.x, normalLoc.y * safeDrawingArea.height + safeDrawingArea.y)
            if (!safeDrawingArea.contains(realLoc)) {
                LOG.warn { "SHOULD NEVER HAPPEN: Rejecting attempt to move outside of safe drawing area: $realLoc" }
                return
            }

            LCD.setPixel((normalLoc.x * LCD.SCREEN_WIDTH).toInt(), (normalLoc.y * LCD.SCREEN_HEIGHT).toInt(), 1)

            val (targetLength0, targetLength1) = xyToStrings(Point2D.Double(0.0, 0.0), Point2D.Double(spoolDistanceCm, 0.0), realLoc)
            LOG.info("Moving from ${location.str} to ${normalLoc.str} (real:${realLoc.str}) l0:${targetLength0.str} l1:${targetLength1.str}")

            val delta0 = targetLength0 - spool0Length
            val targetTacho0 = lengthToTacho(targetLength0)
            LOG.debug(" spool0:${spool0Length.str}->${targetLength0.str}, tachoD:$delta0 -> $targetTacho0")

            val delta1 = targetLength1 - spool1Length
            val targetTacho1 = lengthToTacho(targetLength1)
            LOG.debug(" spool1:${spool1Length.str}->${targetLength1.str}, tachoD:$delta1 -> $targetTacho1")


            // Reduce the speed of the SMALLER delta.  Don't do an else to avoid divide by zero
            spool0.speed = maxSpeed
            spool1.speed = maxSpeed
            if (delta1 < delta0) {
                spool1.speed = (maxSpeed * (delta1 / delta0)).toInt()
            }
            if (delta0 < delta1) {
                spool0.speed = (maxSpeed * (delta0 / delta1)).toInt()
            }

            spool0.startSynchronization()
            spool0.rotateTo(targetTacho0.toInt(), true)
            spool1.rotateTo(targetTacho1.toInt(), true)
            spool0.endSynchronization()
            waitForMoveToEnd(spool0, spool1)
        }


    private fun lengthToTacho(len: Double): Double = 360.0 * (len - initialLengthCm) / TECHNIC_AXLE_CIRCUMFERENCE_CM
    private fun tachoToLength(tacho: Double): Double = initialLengthCm + (tacho / 360.0) * TECHNIC_AXLE_CIRCUMFERENCE_CM // TODO: Thread thickness matters

    /**
     * @return OK to continue
     */
    fun calibrate(): Boolean {
        val adjustDegrees = 360 * 2
        LOG.info { "Starting calibration: enter to finish, escape to quit." }
        while (true) {
            when (Button.waitForAnyPress()) {
                Button.ID_ENTER -> {
                    LOG.info { "Calibrating:ENTER" }
                    return true
                }
                Button.ID_UP -> {
                    LOG.info { "Calibrating:UP" }
                    spool0.rotate(-adjustDegrees, true)
                    spool1.rotate(-adjustDegrees, true)
                }
                Button.ID_LEFT -> {
                    LOG.info { "Calibrating:LEFT" }
                    spool0.rotate(-adjustDegrees, true)
                    spool1.rotate(adjustDegrees, true)
                }
                Button.ID_RIGHT -> {
                    LOG.info { "Calibrating:RIGHT" }
                    spool0.rotate(adjustDegrees, true)
                    spool1.rotate(-adjustDegrees, true)
                }
                Button.ID_DOWN -> {
                    LOG.info { "Calibrating:DOWN" }
                    spool0.rotate(adjustDegrees, true)
                    spool1.rotate(adjustDegrees, true)
                }
                Button.ID_ESCAPE -> {
                    LOG.warn { "Calibrating:ESCAPE" }
                    return false
                }
            }
            spool0.resetTachoCount()
            spool1.resetTachoCount()
        }
    }

    override fun close() {
        LOG.info { "Closing normally." }
        setOf(spool0, spool1).forEach {
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

    override fun toString(): String = "Plotter state: {m0:${spool0.tachoCount}, m1:${spool1.tachoCount}} "

    companion object {
        private val LOG = KotlinLogging.logger {}
        private const val TECHNIC_AXLE_CIRCUMFERENCE_CM = (2 * Math.PI * 2.5) / 10
        private const val BUFFER = 0.15
        /**
         * @link https://www.marginallyclever.com/2012/02/drawbot-overview/ for diagram
         */
        fun xyToStrings(m0: Point2D, m1: Point2D, target: Point2D): Pair<Double, Double> {
            val xa = target.x - m0.x  // same as V-M1 in the picture
            val ya = target.y - m1.y  // same as target-V in the picture
            val hypotenuseA = Math.sqrt(xa * xa + ya * ya)
            val xb = target.x - m1.x  // same as V-M2 in the picture
            val hypotenuseB = Math.sqrt(xb * xb + ya * ya)
            return hypotenuseA to hypotenuseB
        }

        /**
         * Heron's formula https://www.wikihow.com/Find-the-Height-of-a-Triangle
         */
        fun stringsToXY(sideA: Double, sideB: Double, sideC: Double): Point2D {
            val s = (sideA + sideB + sideC) / 2
            val y = Math.sqrt(s * (s - sideA) * (s - sideB) * (s - sideC)) / (0.5 * sideA)
            val x = Math.sqrt(sideB * sideB - y * y)
            return Point2D.Double(x, y)
        }

        fun waitForMoveToEnd(vararg motors: BaseMotor) = runBlocking(Dispatchers.Default) {
            while (motors.any { it.isMoving }) {
                delay(5) // or better to try { Thread.sleep(5) } catch (ie: InterruptedException) { }
            }
        }
    }

}