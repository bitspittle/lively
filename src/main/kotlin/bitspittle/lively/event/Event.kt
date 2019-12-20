package bitspittle.lively.event

/**
 * An event, which notifies listeners of some parameter.
 *
 * The interface can safely be exposed to all callers, as it only supports adding and removing
 * listeners. Internally, a class should use a [MutableEvent].
 *
 * If you have an event that doesn't take any parameters, you can use a [UnitEvent] instead
 * of `Event<Unit>`.
 */
interface Event<P> {
    operator fun plusAssign(listener: (params: P) -> Unit)
    operator fun minusAssign(listener: (P) -> Unit)
}

interface UnitEvent {
    operator fun plusAssign(listener: () -> Unit)
    operator fun minusAssign(listener: () -> Unit)
}

class MutableEvent<P>: Event<P> {
    override fun plusAssign(listener: (params: P) -> Unit) {
        listeners.add(listener)
    }

    override fun minusAssign(listener: (P) -> Unit) {
        listeners.remove(listener)
    }

    operator fun invoke(params: P) {
        listeners.forEach { listener -> listener(params) }
    }

    fun clear() {
        listeners.clear()
    }

    private val listeners = mutableListOf<(P) -> Unit>()
}

class MutableUnitEvent: UnitEvent {
    override fun plusAssign(listener: () -> Unit) {
        listeners.add(listener)
    }

    override fun minusAssign(listener: () -> Unit) {
        listeners.remove(listener)
    }

    operator fun invoke() {
        listeners.forEach { listener -> listener() }
    }

    fun clear() {
        listeners.clear()
    }

    private val listeners = mutableListOf<() -> Unit>()
}
