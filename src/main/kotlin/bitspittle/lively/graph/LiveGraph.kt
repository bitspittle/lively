package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.event.Event
import bitspittle.lively.event.MutableEvent
import bitspittle.lively.event.MutableUnitEvent
import bitspittle.lively.event.UnitEvent

private val graphThreadLocal = ThreadLocal.withInitial { LiveGraph() }

class LiveGraph {
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
    private val dirtyLives = mutableSetOf<Live<*>>()

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
        // TODO: Ensure no cycles!
    }

    internal fun freeze(live: Live<*>) {
        onValueChanged.remove(live)
        onFroze[live]?.invoke()
        onFroze.remove(live)

        // TODO - enqueue request to remove liveInfo and dirtyLives
    }

    @Suppress("UNCHECKED_CAST") // Live<T> always maps to MutableEvent<T>
    internal fun <T> onValueChanged(live: Live<T>): Event<T> =
        onValueChanged.computeIfAbsent(live) { MutableEvent<T>() } as Event<T>

    internal fun onFroze(live: Live<*>): UnitEvent =
        onFroze.computeIfAbsent(live) { MutableUnitEvent() }

    internal fun update(live: Live<*>) {
        if (!dirtyLives.contains(live)) {
            return
        }

        val dirtyDependencies = mutableListOf(live)
        var i = 0
        while (i < dirtyDependencies.size) {
            val currLive = dirtyDependencies[i]
            val dirtyDeps = liveInfo.getValue(currLive).dependencies.filter { dep -> dirtyLives.contains(dep) }
            dirtyDependencies.addAll(dirtyDeps)
            ++i
        }

        // Insert live values to update in reverse order, meaning we update from source nodes first
        // to destination nodes last
        val livesToUpdate = LinkedHashSet<Live<*>>()
        while (i > 0) {
            i--
            livesToUpdate.add(dirtyDependencies[i])
        }

        for (liveToUpdate in livesToUpdate) {
            liveToUpdate.update()
            dirtyLives.remove(liveToUpdate)
        }
    }

    /**
     * Update all dirty nodes.
     *
     * This operation may create new dirty nodes as an intermediate step, but even those will be
     * updated.
     */
    internal fun updateAll() {
        var i = 0
        val dirtyLives = this.dirtyLives.toList()
        this.dirtyLives.clear()
        while (i < dirtyLives.size) {
            dirtyLives[i].update()
            ++i
        }
    }

    /**
     * This method should only be called by a [Live] *after* it has updated its snapshot.
     */
    internal fun <T> notifyUpdated(live: Live<T>, valueChanged: Boolean) {
        dirtyLives.remove(live)

        if (valueChanged) {
            val affectedLives = liveInfo.getValue(live).dependents.toMutableList()

            var i = 0
            while (i < affectedLives.size) {
                val affectedLive = affectedLives[i]
                if (dirtyLives.add(affectedLive)) {
                    affectedLives.addAll(liveInfo.getValue(affectedLive).dependents)
                }
                ++i
            }

            @Suppress("UNCHECKED_CAST") // Map only pairs Live<T> with LiveListener<T>
            (onValueChanged[live] as? MutableEvent<T>)?.invoke(live.getSnapshot())
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