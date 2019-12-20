package bitspittle.lively

import bitspittle.lively.graph.LiveGraph
import bitspittle.lively.thread.expectCurrent

class Lively(internal val graph: LiveGraph = LiveGraph.instance) {
    internal val scope = LiveScope(graph)

    private val ownedLives = mutableSetOf<Live<*>>()

    fun freeze() {
        checkValidStateFor("freeze")

        ownedLives.filter { live -> !live.frozen }.forEach { live -> live.freeze() }
        ownedLives.clear()
    }

    /**
     * Create a live instance from a fixed value.
     */
    fun <T> create(initialValue: T): MutableLive<T> {
        checkValidStateFor("create")

        return MutableLive(this, initialValue).also { ownedLives.add(it) }
    }

    /**
     * Create a live instance that depends on target live instances.
     */
    fun <T> create(block: LiveScope.() -> T): MutableLive<T> {
        checkValidStateFor("create")

        return MutableLive(this, block).also { ownedLives.add(it) }
    }

    /**
     * Track a general callback which depends on one or more live values, where all logic in the
     * callback is self contained. This is useful for performing some sort of side-effect based on
     * one or more of the live values, e.g. setting a UI label based on a name changing, or
     * kicking off some expensive action as the result of a live parameter changing.
     */
    fun listen(sideEffect: LiveScope.() -> Unit) {
        checkValidStateFor("listen")

        // Create an internal dummy node that only exists to run a side effect when any of its
        // dependencies change.
        ownedLives.add(MutableLive(this) { sideEffect() })
    }

    private fun checkValidStateFor(method: String) {
        graph.ownedThread.expectCurrent {
            "Attempting to call `Lively#$method` using a thread it isn't associated with."
        }
    }

}

fun Lively.createByte(value: Byte = 0) = create(value)
fun Lively.createShort(value: Short = 0) = create(value)
fun Lively.createInt(value: Int = 0) = create(value)
fun Lively.createLong(value: Long = 0) = create(value)
fun Lively.createFloat(value: Float = 0f) = create(value)
fun Lively.createDouble(value: Double = 0.0) = create(value)
fun Lively.createString(value: String = "") = create(value)
