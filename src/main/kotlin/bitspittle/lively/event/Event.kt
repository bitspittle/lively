package bitspittle.lively.event

typealias EventListener<P> = EventScope<P>.(params: P) -> Unit
typealias UnitEventListener = UnitEventScope.() -> Unit

class EventScope<P> {
    internal var toRemove = mutableListOf<EventListener<P>>()
    internal lateinit var context: EventListener<P>
    fun removeThisListener() {
        toRemove.add(context)
    }
}

class UnitEventScope {
    internal var toRemove = mutableListOf<UnitEventListener>()
    internal lateinit var context: UnitEventListener
    fun removeThisListener() {
        toRemove.add(context)
    }
}

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
    operator fun plusAssign(listener: EventListener<P>)
    operator fun minusAssign(listener: EventListener<P>)
}

interface UnitEvent {
    operator fun plusAssign(listener: UnitEventListener)
    operator fun minusAssign(listener: UnitEventListener)
}

class MutableEvent<P>: Event<P> {
    override fun plusAssign(listener: EventListener<P>) {
        listeners.add(listener)
    }

    override fun minusAssign(listener: EventListener<P>) {
        listeners.remove(listener)
    }

    operator fun invoke(params: P) {
        if (listeners.isNotEmpty()){
            val eventScope = EventScope<P>()
            listeners.forEach { listener ->
                eventScope.context = listener
                eventScope.listener(params)
            }
            listeners.removeAll(eventScope.toRemove)
        }
    }

    fun clear() {
        listeners.clear()
    }

    private val listeners = mutableListOf<EventListener<P>>()
}

class MutableUnitEvent: UnitEvent {
    override fun plusAssign(listener: UnitEventListener) {
        listeners.add(listener)
    }

    override fun minusAssign(listener: UnitEventListener) {
        listeners.remove(listener)
    }

    operator fun invoke() {
        if (listeners.isNotEmpty()) {
            val unitEventScope = UnitEventScope()
            listeners.forEach { listener ->
                unitEventScope.context = listener
                unitEventScope.listener()
            }
            listeners.removeAll(unitEventScope.toRemove)
        }
    }

    fun clear() {
        listeners.clear()
    }

    private val listeners = mutableListOf<UnitEventListener>()
}

/**
 * Stub events are useful to return when an object gets into a state where it no longer accepts
 * events.
 */
object StubEvent : Event<Any> {
    override fun plusAssign(listener: EventListener<Any>) {}
    override fun minusAssign(listener: EventListener<Any>) {}

    /**
     * Convenience function for safely casting the [StubEvent] object into a typed [Event].
     */
    fun <T> typed(): Event<T> {
        @Suppress("UNCHECKED_CAST")
        return this as Event<T>
    }
}

/**
 * Stub events are useful to return when an object gets into a state where it no longer accepts
 * events.
 */
object StubUnitEvent : UnitEvent {
    override fun plusAssign(listener: UnitEventListener) {}
    override fun minusAssign(listener: UnitEventListener) {}
}
