package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.event.Event
import bitspittle.lively.event.MutableEvent
import bitspittle.lively.event.MutableUnitEvent
import bitspittle.lively.event.UnitEvent
import bitspittle.lively.exec.Executor

private val graphThreadLocal = ThreadLocal.withInitial { LiveGraph(Lively.executorFactory()) }

class LiveGraph(private val graphExecutor: Executor) {
    companion object {
        val instance: LiveGraph
            get() = graphThreadLocal.get()
    }

    internal val ownedThread = Thread.currentThread()

    // Exposed for testing only
    internal fun isEmpty(): Boolean {
        return lives.isEmpty()
                && dependencies.isEmpty()
                && dependents.isEmpty()
                && pendingUpdate.isEmpty()
                && onValueChanged.isEmpty()
                && onFroze.isEmpty()
    }

    private val lives = mutableSetOf<Live<*>>()
    private val dependencies = mutableMapOf<Live<*>, MutableList<Live<*>>>()
    private val dependents = mutableMapOf<Live<*>, MutableList<Live<*>>>()
    private val pendingUpdate = mutableSetOf<Live<*>>()

    private val onValueChanged = mutableMapOf<Live<*>, MutableEvent<*>>()
    private val onFroze = mutableMapOf<Live<*>, MutableUnitEvent>()

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
                        Graph cannot add unknown live value as dependency: $live

                        Are you calling `get` on a value created from a different Lively?
                    """.trimIndent())
            }
            val oldDeps = dependencies[live]
            if (oldDeps != null && oldDeps.containsAll(deps) && deps.containsAll(oldDeps)) {
                return // No-op
            }
        }

        dependencies.getOrPut(live) { mutableListOf() }.apply {
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
            else {
                dependencies.remove(live)
            }
        }

        deps.forEach { dep ->
            dependents.getOrPut(dep) { mutableListOf() }.apply {
                if (!contains(live)) {
                    add(live)
                }
            }
        }
    }

    internal fun freeze(live: Live<*>) {
        onValueChanged.remove(live)?.clear()
        onFroze.remove(live)?.apply {
            invoke()
            clear()
        }
        // If we ever had dependencies, they were already cleared by Live#freeze, which calls clearObserve
        dependents[live]?.forEach { dep ->
            dependencies.getValue(dep).apply {
                remove(live)
                if (isEmpty()) {
                    dependencies.remove(dep)
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
                        dependent.update()
                        pendingUpdate.remove(dependent)
                    }
                }
            }
        }
    }
}