package info.benjaminhill.wbb

import lejos.hardware.Battery
import lejos.hardware.Button
import lejos.hardware.lcd.LCD
import lejos.hardware.port.MotorPort
import lejos.robotics.geometry.Point2D
import lejos.robotics.geometry.Rectangle2D
import mu.KotlinLogging

/**
 * All measurements in ms and cm.  Interface to the outside world "location" is in 0..1
 * "forwards" (positive tacho) unrolls and extends string, "backwards" retracts
 * State is stored in the actual tachoCounts of each motor
 * Start location assumed to be center.
 */
class Plotter : AutoCloseable {
    // Starts uncalibrated
    val spool0 = Spool(MotorPort.A, TECHNIC_AXLE_CIRCUMFERENCE_CM)
    val spool1 = Spool(MotorPort.D, TECHNIC_AXLE_CIRCUMFERENCE_CM)

    private val spoolDistanceCm: Double

    private val realCanvas: Rectangle2D.Double

    init {
        LOG.info { "Power: ${Battery.getVoltageMilliVolt()}" }
        LCD.clear()

        // Calibration.  TODO: Skip if in JSON
        println("Find Upper Left")
        if (!manualMove()) {
            throw IllegalStateException("Bailed on UL calibration.")
        }
        val tachoUL = Pair(spool0.tachoCount, spool1.tachoCount)
        LOG.info { "tachoUL(0,1): ${tachoUL.first},${tachoUL.second}" }
        println("Find Upper Right")
        if (!manualMove()) {
            throw IllegalStateException("Bailed on UR calibration.")
        }
        val tachoUR = Pair(spool0.tachoCount, spool1.tachoCount)
        LOG.info { "tachoUR(0,1): ${tachoUR.first},${tachoUR.second}" }
        val spool0TachoDelta = Math.abs(tachoUR.first - tachoUL.first)
        val spool1TachoDelta = Math.abs(tachoUL.second - tachoUR.second)
        val squareEdgeTacho = Math.min(spool0TachoDelta, spool1TachoDelta)
        LOG.info { "Smallest Tacho Delta $squareEdgeTacho (from $spool0TachoDelta, $spool1TachoDelta)" }
        spoolDistanceCm = (squareEdgeTacho / 360.0) * TECHNIC_AXLE_CIRCUMFERENCE_CM + 2 * 4 // Some wiggle
        realCanvas = Rectangle2D.Double(0.0, 0.0, spoolDistanceCm, spoolDistanceCm).also {
            LOG.info { "Real Canvas: ${it.width.toInt()}x${it.height.toInt()} cm, $squareEdgeTacho tacho" }
        }
        val halfAnEdgeTacho = squareEdgeTacho / 2.0
        val centerOfCanvasTacho = Math.sqrt(2 * halfAnEdgeTacho * halfAnEdgeTacho).toInt()

        // Move to center of drawing and reset
        spool1.rotate(centerOfCanvasTacho) // Careful!  Blocking to extend first
        spool0.rotate(-centerOfCanvasTacho)

        spool0.calibrate((centerOfCanvasTacho / 360.0) * TECHNIC_AXLE_CIRCUMFERENCE_CM, IntRange(-centerOfCanvasTacho, centerOfCanvasTacho))
        spool1.calibrate((centerOfCanvasTacho / 360.0) * TECHNIC_AXLE_CIRCUMFERENCE_CM, IntRange(-centerOfCanvasTacho, centerOfCanvasTacho))
        LOG.info { "Start location:${location.str} (should = {.5,.5})" }

        spool0.synchronizeWith(arrayOf(spool1))
        // In theory, location = Point2D.Double(0.5, 0.5)
    }

    /**
     * Required to be normalized 0..1 plotter location
     */
    var location: Point2D.Double
        get() {
            val realLoc = stringsToXY(spoolDistanceCm, spool0.length, spool1.length)
            return Point2D.Double((realLoc.x - realCanvas.x) / realCanvas.width, (realLoc.y - realCanvas.y) / realCanvas.height)
        }
        set(normalLoc) {
            if (!Rectangle2D.Double(-0.001, -0.001, 1.002, 1.002).contains(normalLoc)) {
                LOG.warn { "Rejecting attempt to move outside of normalized range: ${normalLoc.str}" }
                return
            }

            val realLoc = Point2D.Double(normalLoc.x * realCanvas.width + realCanvas.x, normalLoc.y * realCanvas.height + realCanvas.y)
            val tmpRealCanvas = Rectangle2D.Double(realCanvas.x - 0.001, realCanvas.y - 0.001, realCanvas.width + 0.002, realCanvas.width + 0.002)
            if (!tmpRealCanvas.contains(realLoc)) {
                LOG.warn { "Rejecting attempt to move outside of actual range: ${realLoc.str}" }
                return
            }

            LCD.setPixel((normalLoc.x * LCD.SCREEN_WIDTH).toInt(), (normalLoc.y * LCD.SCREEN_HEIGHT).toInt(), 1)
            val (targetLength0, targetLength1) = xyToStrings(Point2D.Double(0.0, 0.0), Point2D.Double(spoolDistanceCm, 0.0), realLoc)

            LOG.debug { "Moving from ${location.str} to ${normalLoc.str} (real:${realLoc.str}) l0:${targetLength0.str} l1:${targetLength1.str}" }

            val slowestTimeMs = Math.max(spool0.minTimeToReachLengthMs(targetLength0), spool1.minTimeToReachLengthMs(targetLength1))
            spool0.takeExactTimeToMove(slowestTimeMs, targetLength0)
            spool1.takeExactTimeToMove(slowestTimeMs, targetLength1)

            spool0.startSynchronization()
            spool0.length = targetLength0
            spool1.length = targetLength1
            spool0.endSynchronization()
            spool0.waitComplete()
            spool1.waitComplete()
        }

    /** Ignores tacho limits.
     * @return true if OK to continue
     */
    private fun manualMove(): Boolean {
        val adjustDegrees = 360 * 2
        while (true) {
            when (Button.waitForAnyPress()) {
                Button.ID_ENTER -> {
                    spool0.waitComplete()
                    spool1.waitComplete()
                    return true
                }
                Button.ID_UP -> {
                    spool0.rotate(-adjustDegrees, true)
                    spool1.rotate(-adjustDegrees, true)
                }
                Button.ID_LEFT -> {
                    spool0.rotate(-adjustDegrees, true)
                    spool1.rotate(adjustDegrees, true)
                }
                Button.ID_RIGHT -> {
                    spool0.rotate(adjustDegrees, true)
                    spool1.rotate(-adjustDegrees, true)
                }
                Button.ID_DOWN -> {
                    spool0.rotate(adjustDegrees, true)
                    spool1.rotate(adjustDegrees, true)
                }
                Button.ID_ESCAPE -> {
                    // Allows emergency bail
                    spool0.flt(true)
                    spool1.flt(true)
                    return false
                }
            }
        }
    }

    override fun close() {
        LOG.debug { "Plotter closing normally." }
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

    override fun toString(): String = "Plotter state: {loc:${location.str}, l0:${spool0.tachoCount}, l1:${spool1.tachoCount}} "

    companion object {
        private val LOG = KotlinLogging.logger {}
        const val TECHNIC_AXLE_CIRCUMFERENCE_CM = (2 * Math.PI * 2.5) / 10
        /**
         * @link https://www.marginallyclever.com/2012/02/drawbot-overview/ for diagram
         */
        private fun xyToStrings(m0: Point2D, m1: Point2D, target: Point2D): Pair<Double, Double> {
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
        private fun stringsToXY(sideA: Double, sideB: Double, sideC: Double): Point2D {
            val s = (sideA + sideB + sideC) / 2
            val y = Math.sqrt(s * (s - sideA) * (s - sideB) * (s - sideC)) / (0.5 * sideA)
            val x = Math.sqrt(sideB * sideB - y * y)
            return Point2D.Double(x, y)
        }
    }

}