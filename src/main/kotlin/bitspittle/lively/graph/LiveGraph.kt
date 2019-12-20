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

    private class LiveInfo {
        // TODO: Optimize this by only allocating sets on demand. Excessive allocation is fine
        //  to get things up and running though.
        var dependencies = mutableSetOf<Live<*>>()
        var dependents = mutableSetOf<Live<*>>()
    }

    internal val ownedThread = Thread.currentThread()

    private val liveInfo = mutableMapOf<Live<*>, LiveInfo>()
    private val pendingUpdate = mutableSetOf<Live<*>>()

    private val onValueChanged = mutableMapOf<Live<*>, MutableEvent<*>>()
    private val onFroze = mutableMapOf<Live<*>, MutableUnitEvent>()

    internal fun add(live: Live<*>) {
        if (liveInfo.contains(live)) {
            throw IllegalArgumentException("Duplicate live value added to graph: $live")
        }
        liveInfo[live] = LiveInfo()
    }

    internal fun setDependencies(live: Live<*>, deps: Collection<Live<*>>) {
        if (!liveInfo.contains(live)) {
            throw IllegalArgumentException("Graph cannot add dependencies for unknown live value: $live")
        }
        if (deps.any { !liveInfo.contains(it) }) {
            throw IllegalArgumentException("Graph cannot add unknown live value as dependency: $live")
        }
        if (deps.any { it.dependsOn(live) }) {
            throw IllegalArgumentException("Attempting to add a cyclical dependency to: $live")
        }
        liveInfo.getValue(live).apply {
            dependencies.forEach { dep ->
                liveInfo.getValue(dep).dependents.remove(live)
            }
            dependencies.clear()
            dependencies.addAll(deps)
        }
        deps.forEach { dep ->
            liveInfo.getValue(dep).dependents.add(live)
        }
    }

    internal fun freeze(live: Live<*>) {
        onValueChanged.remove(live)
        onFroze[live]?.invoke()
        onFroze.remove(live)
        liveInfo.getValue(live).apply {
            dependencies.forEach { dep -> liveInfo.getValue(dep).dependents.remove(live) }
            dependents.forEach { dep -> liveInfo.getValue(dep).dependencies.remove(live) }
        }
        liveInfo.remove(live)
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

            liveInfo.getValue(live).dependents.toMutableList().forEach { dependent ->
                if (pendingUpdate.add(dependent)) {
                    graphExecutor.submit {
                        dependent.update()
                        pendingUpdate.remove(dependent)
                    }
                }
            }
        }
    }

    private fun Live<*>.dependsOn(other: Live<*>): Boolean {
        val allDependencies = mutableListOf<Live<*>>()
        allDependencies.addAll(liveInfo.getValue(this).dependencies)

        var i = 0
        while (i < allDependencies.size) {
            val currDep = allDependencies[i]
            if (currDep === other) return true
            allDependencies.addAll(liveInfo.getValue(currDep).dependencies)
            i++
        }

        return false
    }
}