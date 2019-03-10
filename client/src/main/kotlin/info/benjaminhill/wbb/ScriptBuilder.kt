package info.benjaminhill.wbb

fun main() {
    var pctScale = 1.0


    for (circle in 1..20) {
        for (deg in 1..360 step 5) {
            pctScale -= (1.0 / (20 * (360 / 5)))
            val y = (pctScale * Math.sin(Math.toRadians(deg.toDouble()))) / 2 + 0.5
            val x = (pctScale * Math.cos(Math.toRadians(deg.toDouble()))) / 2 + 0.5
            println("${x.str},${y.str}")
        }
    }


}