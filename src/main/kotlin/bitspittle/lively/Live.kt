package bitspittle.lively

abstract class Live<T> {
    abstract fun getSnapshot(): T

    internal abstract fun update()

    override fun toString(): String {
        return "Live{${getSnapshot()}}"
    }
}

/**
 * Represents a value that can automatically be updated when a value it depends on also changes.
 *
 * Do not create directly; instead, use [Lively.create]
 */
class MutableLive<T> private constructor(
    private val lively: Lively,
    initialValue: T,
    private var observe: (LiveScope.() -> T)?
) : Live<T>() {

    internal constructor(lively: Lively, initialValue: T) : this(lively, initialValue, null)
    internal constructor(lively: Lively, observe: LiveScope.() -> T) : this(lively, lively.scope.observe(), observe)

    init {
        lively.graph.add(this)
    }

    private var value = initialValue

    override fun getSnapshot() = value

    fun set(value: T) {
        if (observe != null) {
            throw IllegalStateException("Can't set a Live value directly if it previously called `observe`")
        }
        handleSet(value)
    }

    fun observe(observe: (LiveScope.() -> T)?) {
        this.observe = observe
        runObserveIfNotNull()
    }

    override fun update() {
        runObserveIfNotNull()
    }

    private fun runObserveIfNotNull() {
        observe?.let { observe ->
            lively.scope.recordDependencies(this) {
                handleSet(observe())
            }
        }
    }

    private fun handleSet(value: T) {
        val valueChanged = this.value != value
        this.value = value
        lively.graph.notifyUpdated(this, valueChanged)
    }
}