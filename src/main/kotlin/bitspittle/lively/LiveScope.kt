package bitspittle.lively

import bitspittle.lively.internal.LiveGraph

/**
 * A scope within which [Live] instances can be queried for their live value.
 */
class LiveScope internal constructor(private val graph: LiveGraph) {
    private val recordedDepsStack = mutableListOf<MutableSet<Live<*>>>()

    fun <T> Live<T>.get(): T {
        recordedDepsStack.last().add(this)
        graph.update()
        return getSnapshot()
    }

    internal fun recordDependencies(live: Live<*>, block: LiveScope.() -> Unit) {
        recordedDepsStack.add(mutableSetOf())
        try {
            block()
        }
        finally {
            val newDeps = recordedDepsStack.removeAt(recordedDepsStack.lastIndex)
            graph.setDependencies(live, newDeps)
        }
    }
}