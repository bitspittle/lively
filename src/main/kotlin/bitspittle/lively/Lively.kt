package bitspittle.lively

import bitspittle.lively.graph.LiveGraph

class Lively(internal val graph: LiveGraph = LiveGraph.instance) {
    internal val scope = LiveScope(graph)

    private val ownedLiveValues = mutableSetOf<Live<*>>()

    fun freeze() {
        ownedLiveValues.filter { live -> !live.frozen }.forEach { live -> live.freeze() }
        ownedLiveValues.clear()
    }

    /**
     * Create a live instance from a fixed value.
     */
    fun <T> create(initialValue: T): MutableLive<T> {
        return MutableLive(this, initialValue).also { ownedLiveValues.add(it) }
    }

    /**
     * Create a live instance that depends on target live instances.
     */
    fun <T> create(block: LiveScope.() -> T): MutableLive<T> {
        return MutableLive(this, block).also { ownedLiveValues.add(it) }
    }

    /**
     * Track a general callback which depends on one or more live values, where all logic in the
     * callback is self contained. This is useful for performing some sort of side-effect based on
     * one or more of the live values, e.g. setting a UI label based on a name changing, or
     * kicking off some expensive action as the result of a live parameter changing.
     */
    fun observe(sideEffect: LiveScope.() -> Unit) {
        // Create an internal dummy node that only exists to run a side effect when any of its
        // dependencies change.
        ownedLiveValues.add(MutableLive(this) { sideEffect() })
    }
}

fun Lively.createByte(value: Byte = 0) = create(value)
fun Lively.createShort(value: Short = 0) = create(value)
fun Lively.createInt(value: Int = 0) = create(value)
fun Lively.createLong(value: Long = 0) = create(value)
fun Lively.createFloat(value: Float = 0f) = create(value)
fun Lively.createDouble(value: Double = 0.0) = create(value)
fun Lively.createString(value: String = "") = create(value)
