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

private val graphThreadLocal = ThreadLocal.withInitial { LiveGraph(Lively.executorFactory()) }

private typealias WeakSet<E> = MutableSet<E>
private typealias WeakMap<K, V> = MutableMap<K, V>

private fun <E> mutableWeakSetOf(): WeakSet<E> = Collections.newSetFromMap(WeakHashMap<E, Boolean>())

class LiveGraph(private val graphExecutor: Executor) {
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
     * Used to aggregate all updates across multiple calls to [notifyUpdated].
     *
     * Update order matters! So we use a LinkedHashSet.
     *
     * Value will be cleared once updates are finished processing.
     */
    private val updatesToProcess: MutableSet<SourceLive<*>> = LinkedHashSet()

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
    internal fun <T> notifyUpdated(live: SourceLive<T>) {
        fireOnValueChanged(live)
        if (dependentsMap[live]?.isEmpty() != false) return

        val shouldSubmit = updatesToProcess.isEmpty()
        updatesToProcess.add(live)

        if (shouldSubmit) {
            graphExecutor.submit {
                assert(updatesToProcess.isNotEmpty())

                // Create a list of all affected lives, expanded to include all their dependencies
                // This may produce some duplicates but we'll deal with that in the next step.
                val allAffectedLives = updatesToProcess.asSequence()
                    .filter { sourceLive -> !sourceLive.frozen }
                    .mapNotNull { sourceLive -> dependentsMap[sourceLive] }
                    .flatMap { dependents -> dependents.asSequence() }
                    .toMutableList()
                updatesToProcess.clear()

                var i = 0
                while (i < allAffectedLives.size) {
                    val currLive = allAffectedLives[i]
                    dependentsMap[currLive]?.let { deps -> allAffectedLives.addAll(deps) }
                    ++i
                }

                val pendingUpdates = LinkedHashSet<ObservingLive<*>>()
                allAffectedLives.forEach { currLive ->
                    // If we encounter a node already in the pending updates list, move it to the
                    // back. This is useful for example if:
                    // A <------------┐
                    // B <- C <- D <- E
                    // and you change A then B, the update order should be
                    //  A, B, C, D, E
                    // not
                    //  A, E, B, C, D
                    pendingUpdates.remove(currLive)
                    pendingUpdates.add(currLive)
                }

                pendingUpdates.asSequence()
                    .filter { observingLive -> !observingLive.frozen }
                    .forEach { observingLive ->
                        if (observingLive.update()) {
                            fireOnValueChanged(observingLive)
                        }
                    }
            }
        }
    }

    private fun <T> fireOnValueChanged(live: Live<T>) {
        @Suppress("UNCHECKED_CAST") // Live<T> always mapped to MutableEvent<T>
        (onValueChanged[live] as? MutableEvent<T>)?.invoke(live.getSnapshot())
    }
}