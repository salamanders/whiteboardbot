package scriptgen

import info.benjaminhill.wbb.NormalVector2D

class TreeNode<T>(val value: T, private val parent: TreeNode<T>? = null, private val isClose: (T, T) -> Boolean) {
    private val children = mutableListOf<TreeNode<T>>()

    fun addChild(nodeValue: T) {
        children.add(TreeNode(nodeValue, this, isClose = isClose))
    }

    fun find(otherValue: T): TreeNode<T>? {
        if (isClose(value, otherValue)) {
            return this
        }
        children.forEach { child ->
            child.find(otherValue)?.let {
                return it
            }
        }
        return null
    }

    /** Edge children with no children.  Where growth happens */
    fun getLeaves(): List<TreeNode<T>> {
        if (children.isEmpty()) {
            return listOf(this)
        }
        val (leaves, notLeaves) = children.partition { it.children.isEmpty() }
        return leaves.plus(notLeaves.map { it.getLeaves() }.flatten())
    }

    fun pathFromRoot(): List<TreeNode<T>> {
        val path = mutableListOf<TreeNode<T>>()
        parent?.let { p ->
            path.addAll(p.pathFromRoot())
        }
        path.add(this)
        return path
    }
}

/**
 * Left-to-right pass
 */
class ImageToScan(fileName: String) : AbstractImageToScaleFree(fileName) {

    private val xStep = 5.0 / 1000
    private val yStep = 3.0 / 1000

    private fun hasInk(p: NormalVector2D): Boolean = getInk(p) > 0.0001

    /** Get from start to finish along an edge, if possible.  Empty if no path found */
    private fun findEdgePath(start: NormalVector2D, finish: NormalVector2D): List<NormalVector2D> {

        // Blow out with horribly inefficient flood fill
        val searchTree = TreeNode(start) { a, b ->
            a.distance(b) < Math.min(xStep, yStep) / 2
        }
        val stepSize = Math.min(xStep, yStep)
        var addedNewLocation = true
        var journey = 0
        while (searchTree.find(finish) == null && addedNewLocation && journey < 1000) {
            addedNewLocation = false
            searchTree.getLeaves().forEach { leaf ->
                // Diagonals are a bummer.  Need something better.
                (0 until 360 step 15).forEach { deg ->
                    journey++
                    val step = angleToVector2D(Math.toRadians(deg.toDouble())).scalarMultiply(stepSize)
                    val nextLoc = leaf.value.addN(NormalVector2D.toNormal(step))
                    if (hasInk(nextLoc) && searchTree.find(nextLoc) == null) {
                        leaf.addChild(nextLoc)
                        addedNewLocation = true
                    }
                }
            }
        }
        searchTree.find(finish)?.let { foundNode ->
            return foundNode.pathFromRoot().map { it.value }.also { path ->
                LOG.info { "Found a path around a gap: ${path.size}" }
            }
        }
        LOG.info { "Failed to find a path after ${searchTree.getLeaves().size} leaves, journey:$journey" }
        return listOf()
    }

    fun run() {
        var x = xStep
        var y = yStep
        var dir = 1

        var lastIsInky = false
        while (x < 1.0) {
            y += yStep * dir
            // Bounce off top/bottom
            when {
                y <= yStep && dir == -1 -> {
                    x += xStep
                    dir = 1
                }
                y >= 1.0 - yStep && dir == 1 -> {
                    x += xStep
                    dir = -1
                }
            }
            val loc = NormalVector2D(x, y)
            val isInky = hasInk(loc)

            val inkLevel = listOf(
                    getInk(NormalVector2D(loc.x, loc.y + yStep * dir * .5)),
                    getInk(NormalVector2D(loc.x + xStep / 3, loc.y + yStep * dir * .5)),
                    getInk(NormalVector2D(loc.x - xStep / 3, loc.y + yStep * dir * .5))
            ).average().toFloat()

            if (isInky) {
                if (!lastIsInky && script.isNotEmpty()) {
                    // Just transitioned to inside.  Need to find a "safe" path from last to here
                    script.addAll(findEdgePath(script.last(), loc))
                }
                script.add(loc)

                if (inkLevel > 0.1) {
                    val squiggleSize = (xStep * inkLevel) * .4
                    script.add(loc.addN(NormalVector2D(squiggleSize, 0.0)))
                    script.add(loc.addN(NormalVector2D(squiggleSize, yStep * dir)))
                    script.add(loc.addN(NormalVector2D(-squiggleSize, yStep * dir)))
                    script.add(loc.addN(NormalVector2D(-squiggleSize, yStep * dir * 2)))
                    script.add(loc.addN(NormalVector2D(0.0, yStep * dir * 2)))
                }
            }
            lastIsInky = isInky
        }
    }
}

fun main() = ImageToScan("shark.png").use { it.run() }
