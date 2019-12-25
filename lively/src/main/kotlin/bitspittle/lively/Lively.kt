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

    private val scope = LiveScope(graph)
    private val lives = mutableSetOf<FreezableLive<*>>()

    fun freeze() {
        checkValidStateFor("freeze")

        lives.forEach { live -> live.freeze() }
        lives.clear()
    }

    /**
     * Create a live instance from a fixed value.
     */
    fun <T> create(initialValue: T): SourceLive<T> {
        checkValidStateFor("create")

        return SourceLive(graph, scope, initialValue).also { lives.add(it) }
    }

    /**
     * Create a live instance that depends on target live instances.
     */
    fun <T> create(observe: LiveScope.() -> T): ObservingLive<T> {
        checkValidStateFor("create")

        return ObservingLive(graph, scope, observe).also { lives.add(it) }
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
        lives.add(ObservingLive(graph, scope) { sideEffect() })
    }

    private fun checkValidStateFor(method: String) {
        graph.ownedThread.expectCurrent {
            "Attempting to call `Lively#$method` using a thread it isn't associated with."
        }
    }

}

