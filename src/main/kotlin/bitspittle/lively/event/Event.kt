package bitspittle.lively.event

interface Event<T> {
    operator fun plusAssign(listener: (arg: T) -> Unit)
    operator fun minusAssign(listener: (T) -> Unit)
}

class MutableEvent<T>: Event<T> {
    override fun plusAssign(listener: (arg: T) -> Unit) {
        listeners.add(listener)
    }

    override fun minusAssign(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    fun fire(arg: T) {
        listeners.forEach { listener -> listener(arg) }
    }

    fun clear() {
        listeners.clear()
    }

    private val listeners = mutableListOf<(T) -> Unit>()
}
