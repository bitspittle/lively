package bitspittle.lively

import bitspittle.lively.exec.Executor
import bitspittle.lively.exec.ThrowingExecutor
import bitspittle.lively.graph.LiveGraph
import bitspittle.lively.extensions.expectCurrent

class Lively(internal val graph: LiveGraph = LiveGraph.instance) {
    companion object {
        var executorFactory: () -> Executor = {
            ThrowingExecutor(
                """
                    To use Lively, you must first initialize `Lively.executorFactory` in your codebase.
                    For example: `Lively.executorFactory = { RunImmediatelyExecutor() }`
                """.trimIndent())
        }
    }
    internal val scope = LiveScope(graph)

    private val ownedLives = mutableSetOf<MutableLive<*>>()

    fun freeze() {
        checkValidStateFor("freeze")

        ownedLives.filter { live -> !live.frozen }.forEach { live -> live.freeze() }
        ownedLives.clear()
    }

    /**
     * Create a live instance from a fixed value.
     */
    fun <T> create(initialValue: T): SettableLive<T> {
        checkValidStateFor("create")

        return SettableLive(this, initialValue).also { ownedLives.add(it) }
    }

    /**
     * Create a live instance that depends on target live instances.
     */
    fun <T> create(block: LiveScope.() -> T): MutableLive<T> {
        checkValidStateFor("create")

        return SettableLive(this, block).also { ownedLives.add(it) }
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
        ownedLives.add(SettableLive(this) { sideEffect() })
    }

    private fun checkValidStateFor(method: String) {
        graph.ownedThread.expectCurrent {
            "Attempting to call `Lively#$method` using a thread it isn't associated with."
        }
    }

}

