package bitspittle.lively

import bitspittle.lively.exec.Executor
import bitspittle.lively.exec.ThrowingExecutor
import bitspittle.lively.extensions.expectCurrent
import bitspittle.lively.graph.LiveGraph

class Lively(internal val graph: LiveGraph = LiveGraph.instance) {
    companion object {
        // A map of one executor per thread
        private var executorMap = mutableMapOf<Thread, Executor>()
        var executor: Executor
            get() {
                return executorMap.getOrDefault(
                    Thread.currentThread(),
                    ThrowingExecutor(
                        """
                            To use Lively, you must first initialize `Lively.executor` in your codebase.
                            For example: `Lively.executor = RunImmediatelyExecutor()`
                            Please see the "Lively Executor" section in the README for more information.
                        """.trimIndent()
                    ))
            }

        set(value) {
            executorMap[Thread.currentThread()] = value
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
    fun <T> source(initialValue: T): SourceLive<T> {
        checkValidStateFor("create")

        return SourceLive(graph, scope, initialValue).also { lives.add(it) }
    }

    /**
     * Create a live instance that depends on target live instances.
     */
    fun <T> observing(observe: LiveScope.() -> T): ObservingLive<T> {
        checkValidStateFor("create")

        return ObservingLive(graph, scope, observe).also { lives.add(it) }
    }

    /**
     * Track a general callback which depends on one or more live values, where all logic in the
     * callback is self contained. This is useful for performing some sort of side-effect based on
     * one or more of the live values, e.g. setting a UI label based on a name changing, or
     * kicking off some expensive action as the result of a live parameter changing.
     */
    fun sideEffect(observe: LiveScope.() -> Unit): SideEffect {
        checkValidStateFor("observe")

        // As an implementation detail, we actually create a live value for this under the hood,
        // but the public API hides this fact.
        return SideEffect(observing(observe))
    }

    private fun checkValidStateFor(method: String) {
        graph.ownedThread.expectCurrent {
            "Attempting to call `Lively#$method` using a thread it isn't associated with."
        }
    }

}

