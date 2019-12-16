package bitspittle.lively.internal

import bitspittle.lively.Live

private val graphThreadLocal = ThreadLocal.withInitial { LiveGraph() }

class LiveGraph {
    companion object {
        val instance: LiveGraph
            get() = graphThreadLocal.get()
    }

    private class Node {
        // TODO: Optimize this by only allocating sets on demand. Excessive allocation is fine
        //  to get things up and running though.
        var dependencies = mutableSetOf<Live<*>>()
        var dependents = mutableSetOf<Live<*>>()
    }
    private val nodes = mutableMapOf<Live<*>, Node>()
    private val dirtyLives = mutableSetOf<Live<*>>()

    // TODO: Add freeze concept

    internal fun add(live: Live<*>) {
        if (nodes.contains(live)) {
            throw IllegalArgumentException("Duplicate live value added to graph: $live")
        }
        nodes[live] = Node()
    }

    internal fun setDependencies(live: Live<*>, deps: Collection<Live<*>>) {
        if (!nodes.contains(live)) {
            throw IllegalArgumentException("Graph cannot add dependencies for unknown live value: $live")
        }
        if (deps.any { !nodes.contains(it) }) {
            throw IllegalArgumentException("Graph cannot add unknown live value as dependency: $live")
        }
        nodes.getValue(live).apply {
            dependencies.forEach { dep ->
                nodes.getValue(dep).dependents.remove(live)
            }
            dependencies.clear()
            dependencies.addAll(deps)
        }
        deps.forEach { dep ->
            nodes.getValue(dep).dependents.add(live)
        }
        // TODO: Ensure no cycles!
    }

    internal fun update(live: Live<*>) {
        if (!dirtyLives.contains(live)) {
            return
        }

        val dirtyDependencies = mutableListOf(live)
        // Optimization: Loop through nodes instead of repeatedly popping from index 0
        var i = 0
        while (i < dirtyDependencies.size) {
            val currLive = dirtyDependencies[i]
            val dirtyDeps = nodes.getValue(currLive).dependencies.filter { dep -> dirtyLives.contains(dep) }
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

    internal fun updateAll() {
        // TODO: Ensure loop order is from non-dependent nodes to dependent
        // Optimization: Loop through nodes instead of repeatedly popping from index 0
        var i = 0
        val dirtyLives = this.dirtyLives.toList()
        this.dirtyLives.clear()
        while (i < dirtyLives.size) {
            dirtyLives[i].update()
            ++i
        }
    }

    internal fun notifyUpdated(live: Live<*>, valueChanged: Boolean) {
        dirtyLives.remove(live)

        if (valueChanged) {
            val affectedLives = nodes.getValue(live).dependents.toMutableList()

            // Optimization: See comment in `update`
            // We can do this without worrying about an infinite loop because we know our graph
            // doesn't have any cycles in it.
            var i = 0
            while (i < affectedLives.size) {
                val affectedLive = affectedLives[i]
                if (dirtyLives.add(affectedLive)) {
                    affectedLives.addAll(nodes.getValue(affectedLive).dependents)
                }
                ++i
            }
        }
    }
}