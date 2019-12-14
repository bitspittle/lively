package bitspittle.lively

import bitspittle.lively.internal.LiveGraph

class Lively(internal val graph: LiveGraph = LiveGraph.instance) {
    internal val scope = LiveScope(graph)

    private val ownedLiveValues = mutableSetOf<Live<*>>()

    fun freeze() {
        TODO("Implement freeze, which should lock all live values and clean up the graph")
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
