package info.benjaminhill.wbb

import lejos.hardware.motor.EV3LargeRegulatedMotor
import lejos.hardware.port.Port
import kotlin.properties.Delegates

/** Motor attached to a string */
class Spool(port: Port, private val axleCircumferenceCm: Double) : EV3LargeRegulatedMotor(port) {

    /** Max of 4 revolutions/second. */
    private val maxSafeSpeed = Math.min(maxSpeed.toDouble(), 360.0 * 4).toInt()

    /** Start at exact center of drawing area */
    private var stringLengthAtTachoZero: Double by Delegates.notNull()

    /** Don't strip the gears, also, the upper edge of the drawing */
    private lateinit var safeTacho: IntRange

    init {
        acceleration = maxSafeSpeed // Spin up to full speed in 1 second
    }

    fun calibrate(stringLengthAtTachoZero: Double, safeTacho: IntRange) {
        resetTachoCount()
        this.stringLengthAtTachoZero = stringLengthAtTachoZero
        // Buffer of a few revolutions (should be unneeded aside from rounding errors)
        this.safeTacho = safeTacho.first - 360 * 2..safeTacho.last + 360 * 2
    }

    /** Only works after calibrate */
    var length: Double
        get() = stringLengthAtTachoZero + (tachoCount / 360.0) * axleCircumferenceCm // TODO: Thread thickness matters
        set(value) {
            val targetTacho = (360.0 * (value - stringLengthAtTachoZero) / axleCircumferenceCm).toInt()
            if (!safeTacho.contains(targetTacho)) {
                LOG.warn { "Rejecting unsafe targetTacho:$targetTacho !in (${safeTacho.first}..${safeTacho.last}" }
                return
            }
            rotateTo(targetTacho, true)
        }

    fun takeExactTimeToMove(timeMs: Int, targetLength: Double) {
        val myTime = minTimeToReachLengthMs(targetLength)
        if (myTime == 0) {
            return
        }
        if (myTime > timeMs) {
            LOG.warn { "Can't speed up fast enough to make target time $timeMs" }
        }
        val slowdownRatio = myTime.toDouble() / timeMs
        setSpeed((maxSafeSpeed * slowdownRatio).toInt())
    }

    fun minTimeToReachLengthMs(value: Double): Int {
        val targetTacho = (360.0 * (value - stringLengthAtTachoZero) / axleCircumferenceCm).toInt()
        return ((1_000.0 * Math.abs(targetTacho - tachoCount)) / maxSafeSpeed).toInt()
    }
}