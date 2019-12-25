package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.event.Event
import bitspittle.lively.event.MutableEvent
import bitspittle.lively.event.MutableUnitEvent
import bitspittle.lively.event.UnitEvent
import bitspittle.lively.exec.Executor
import java.util.*

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
                        dependencies.isEmpty()
                                && dependents.isEmpty()
                                && pendingUpdate.isEmpty()
                                && onValueChanged.isEmpty()
                                && onFroze.isEmpty()
                    )
                }
            }
        }

    private val lives: WeakSet<Live<*>> = mutableWeakSetOf()
    private val dependencies: WeakMap<Live<*>, WeakSet<Live<*>>> = WeakHashMap()
    private val dependents: WeakMap<Live<*>, WeakSet<Live<*>>> = WeakHashMap()
    private val pendingUpdate = mutableSetOf<Live<*>>()

    private val onValueChanged: MutableMap<Live<*>, MutableEvent<*>> = WeakHashMap()
    private val onFroze: MutableMap<Live<*>, MutableUnitEvent> = WeakHashMap()

    internal fun add(live: Live<*>) {
        if (lives.contains(live)) {
            throw IllegalArgumentException("Duplicate live value added to graph: $live")
        }
        lives.add(live)
    }

    internal fun setDependencies(live: Live<*>, deps: Collection<Live<*>>) {
        if (!lives.contains(live)) {
            throw IllegalArgumentException("Graph cannot add dependencies for unknown live value: $live")
        }

        if (deps.isEmpty()) {
            if (!dependencies.contains(live)) {
                return // No-op
            }
        }
        else {
            if (deps.any { !lives.contains(it) }) {
                throw IllegalArgumentException(
                    """
                        Graph cannot add unknown live value as dependency.

                        Current Live: $live
                        Bad dependency: ${deps.first { !lives.contains(it) }}

                        Are you calling `get` on a value created from a different Lively?
                    """.trimIndent())
            }
            val oldDeps = dependencies[live]
            if (oldDeps != null && oldDeps.containsAll(deps) && deps.containsAll(oldDeps)) {
                return // No-op
            }
        }

        dependencies.getOrPut(live) { mutableWeakSetOf() }.apply {
            forEach { oldDep ->
                if (!deps.contains(oldDep)) {
                    dependents.getValue(oldDep).apply {
                        remove(live)
                        if (this.isEmpty()) {
                            dependents.remove(oldDep)
                        }
                    }
                }
            }
            clear()
            if (deps.isNotEmpty()) {
                addAll(deps)
            }
        }

        deps.forEach { dep ->
            dependents.getOrPut(dep) { mutableWeakSetOf() }.apply {
                if (!contains(live)) {
                    add(live)
                }
            }
        }

        if (deps.isEmpty()) {
            // Once an observing live value has no more dependencies, it will never change, so
            // just freeze it. Calling `ObservingLive#freeze` instead of `freeze` directly will
            // prevent infinite recursion.
            live.asObservingLive().freeze()
        }
    }

    internal fun freeze(live: Live<*>) {
        onValueChanged.remove(live)?.clear()
        onFroze.remove(live)?.apply {
            invoke()
            clear()
        }
        setDependencies(live, emptyList())
        dependents[live]?.forEach { dep ->
            dependencies.getValue(dep).apply {
                remove(live)
                if (isEmpty()) {
                    // If our dependent no longer has any dependencies, then it will never change
                    // either!
                    dep.asObservingLive().freeze()
                }
            }
        }
        dependencies.remove(live)
        dependents.remove(live)
        lives.remove(live)
        pendingUpdate.remove(live)
    }

    @Suppress("UNCHECKED_CAST") // Live<T> always maps to MutableEvent<T>
    internal fun <T> onValueChanged(live: Live<T>): Event<T> =
        onValueChanged.computeIfAbsent(live) { MutableEvent<T>() } as Event<T>

    internal fun onFroze(live: Live<*>): UnitEvent =
        onFroze.computeIfAbsent(live) { MutableUnitEvent() }

    /**
     * This method should only be called by a [Live] *after* it has updated its snapshot.
     */
    internal fun <T> notifyUpdated(live: Live<T>, valueChanged: Boolean) {
        pendingUpdate.remove(live)

        if (valueChanged) {
            @Suppress("UNCHECKED_CAST") // Map only pairs Live<T> with LiveListener<T>
            (onValueChanged[live] as? MutableEvent<T>)?.invoke(live.getSnapshot())

            // To avoid re-entry issues, duplicate the collection before iterating over it
            dependents[live]?.toMutableList()?.forEach { dependent ->
                if (pendingUpdate.add(dependent)) {
                    graphExecutor.submit {
                        dependent.asObservingLive().update()
                        pendingUpdate.remove(dependent)
                    }
                }
            }
        }
    }

    /**
     * Helper function for casting [Live] instances we KNOW depend on other [Live]s.
     */
    private fun <T> Live<T>.asObservingLive(): ObservingLive<T> {
        assert (dependencies.contains(this))
        return this as ObservingLive<T>
    }
}