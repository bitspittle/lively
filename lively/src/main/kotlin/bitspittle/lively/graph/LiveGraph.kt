package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.SourceLive
import bitspittle.lively.event.Event
import bitspittle.lively.event.MutableEvent
import bitspittle.lively.event.MutableUnitEvent
import bitspittle.lively.event.UnitEvent
import bitspittle.lively.exec.Executor
import java.util.*
import kotlin.collections.LinkedHashSet

private val graphThreadLocal = ThreadLocal.withInitial { LiveGraph(Lively.executor) }

private typealias WeakSet<E> = MutableSet<E>
private typealias WeakMap<K, V> = MutableMap<K, V>

private fun <E> mutableWeakSetOf(): WeakSet<E> = Collections.newSetFromMap(WeakHashMap<E, Boolean>())

class LiveGraph(private val executor: Executor) {
    companion object {
        val instance: LiveGraph
            get() = graphThreadLocal.get()
    }

    internal val ownedThread = Thread.currentThread()

    // Exposed for testing only
    internal val nodeCount: Int
        get() {
            return lives.size.also {
                if (lives.isEmpty()) {
                    assert(
                        dependenciesMap.isEmpty()
                                && dependentsMap.isEmpty()
                                && updatesToProcess.isEmpty()
                                && onValueChanged.isEmpty()
                                && onFroze.isEmpty()
                    )
                }
            }
        }

    private val lives: WeakSet<Live<*>> = mutableWeakSetOf()
    private val dependenciesMap: WeakMap<Live<*>, WeakSet<Live<*>>> = WeakHashMap()
    private val dependentsMap: WeakMap<Live<*>, WeakSet<ObservingLive<*>>> = WeakHashMap()

    private val onValueChanged: MutableMap<Live<*>, MutableEvent<*>> = WeakHashMap()
    private val onFroze: MutableMap<Live<*>, MutableUnitEvent> = WeakHashMap()

    /**
     * Used to aggregate all updates across multiple calls to [handleUpdated].
     *
     * Update order matters! So we use a LinkedHashSet.
     *
     * Value will be cleared once updates are finished processing.
     */
    private val updatesToProcess: MutableSet<ObservingLive<*>> = LinkedHashSet()

    internal fun add(live: Live<*>) {
        if (lives.contains(live)) {
            throw IllegalArgumentException("Duplicate live value added to graph: $live")
        }
        lives.add(live)
    }

    internal fun setDependencies(live: ObservingLive<*>, dependencies: Collection<Live<*>>) {
        if (!lives.contains(live)) {
            throw IllegalArgumentException("Graph cannot add dependencies for unknown live value: $live")
        }

        if (dependencies.isEmpty()) {
            if (!dependenciesMap.contains(live)) {
                return // No-op
            }
        }
        else {
            if (dependencies.any { !lives.contains(it) }) {
                throw IllegalArgumentException(
                    """
                        Graph cannot add unknown live value as dependency.

                        Current Live: $live
                        Bad dependency: ${dependencies.first { !lives.contains(it) }}

                        Are you calling `get` on a value created from a different Lively?
                    """.trimIndent())
            }
            val oldDependencies = dependenciesMap[live]
            if (oldDependencies != null &&
                oldDependencies.containsAll(dependencies) &&
                dependencies.containsAll(oldDependencies)) {
                return // No-op
            }
        }

        dependenciesMap.getOrPut(live) { mutableWeakSetOf() }.apply {
            forEach { oldDependency ->
                if (!dependencies.contains(oldDependency)) {
                    dependentsMap.getValue(oldDependency).apply {
                        remove(live)
                        if (this.isEmpty()) {
                            dependentsMap.remove(oldDependency)
                        }
                    }
                }
            }
            clear()
            if (dependencies.isNotEmpty()) {
                addAll(dependencies)
            }
        }

        dependencies.forEach { dependency ->
            dependentsMap.getOrPut(dependency) { mutableWeakSetOf() }.apply {
                if (!contains(live)) {
                    add(live)
                }
            }
        }

        if (dependencies.isEmpty()) {
            // Once an observing live value has no more dependencies, it will never change, so
            // just freeze it. Calling `ObservingLive#freeze` instead of `freeze` directly will
            // prevent infinite recursion.
            live.freeze()
        }
    }

    internal fun freeze(live: Live<*>) {
        onValueChanged.remove(live)?.clear()
        onFroze.remove(live)?.apply {
            invoke()
            clear()
        }
        (live as? ObservingLive<*>)?.let { observingLive -> setDependencies(observingLive, emptyList()) }
        dependentsMap[live]?.forEach { dep ->
            dependenciesMap.getValue(dep).apply {
                remove(live)
                if (isEmpty()) {
                    // If our dependent no longer has any dependencies, then it will never change
                    // either!
                    dep.freeze()
                }
            }
        }
        dependenciesMap.remove(live)
        dependentsMap.remove(live)
        lives.remove(live)
    }

    @Suppress("UNCHECKED_CAST") // Live<T> always maps to MutableEvent<T>
    internal fun <T> onValueChanged(live: Live<T>): Event<T> =
        onValueChanged.computeIfAbsent(live) { MutableEvent<T>() } as Event<T>

    internal fun onFroze(live: Live<*>): UnitEvent =
        onFroze.computeIfAbsent(live) { MutableUnitEvent() }

    /**
     * This method should only be called by a [SourceLive] *after* a user changed its
     * value.
     */
    internal fun <T> handleUpdated(live: Live<T>) {
        fireOnValueChanged(live)
        val dependents = dependentsMap[live] ?: return

        val shouldKickstartProcessing = updatesToProcess.isEmpty()
        updatesToProcess.addAll(dependents)

        // Prevent unnecessary process request if this method was called while already processing.
        if (shouldKickstartProcessing) {
            processUpdates()
        }
    }

    private fun processUpdates() {
        executor.submit {
            while (updatesToProcess.isNotEmpty()) {
                // Process the first live that doesn't depend on any other in the list
                // This is useful for example if:
                // A <------------┐
                // B <- C <- D <- E
                // and you change A then B, the update order should be
                //  A, B, C, D, E
                // not
                //  A, E, B, C, D
                // More explicitly:
                // A'             -> to process: [E]
                // B'             -> to process: [E, C]
                // C is processed -> to process: [E, D]
                // D is processed -> to process: [E]
                // E is processed -> to process: []
                val nextToProcess =
                    updatesToProcess.reduce { live1, live2 -> if (live1.dependsOn(live2)) live2 else live1 }
                updatesToProcess.remove(nextToProcess)
                if (!nextToProcess.frozen && nextToProcess.update()) {
                    handleUpdated(nextToProcess) // Note: may add values to `updatesToProcess`
                }
            }
        }
    }

    private fun <T> fireOnValueChanged(live: Live<T>) {
        @Suppress("UNCHECKED_CAST") // Live<T> always mapped to MutableEvent<T>
        (onValueChanged[live] as? MutableEvent<T>)?.invoke(live.getSnapshot())
    }

    private fun ObservingLive<*>.dependsOn(other: Live<*>): Boolean {
        val allDependents = dependentsMap[other]?.toMutableList() ?: return false

        var i = 0
        while (i < allDependents.size) {
            val currLive = allDependents[i]
            if (currLive == this) {
                return true
            }
            dependentsMap[currLive]?.let { allDependents.addAll(it) }
            ++i
        }

        return false
    }
}