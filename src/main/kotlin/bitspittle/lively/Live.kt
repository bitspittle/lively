package bitspittle.lively

import bitspittle.lively.event.Event
import bitspittle.lively.event.UnitEvent
import bitspittle.lively.extensions.expectCurrent

/**
 * Class which represents a live value (that is, expected to change over time, possibly with other
 * values depending on it).
 *
 * Users do not create instances directly; instead, they should instantiate a [Lively] and use that
 * as a live factory instead.
 *
 * When a live instance is created, it is associated with a thread, and any attempt to access it
 * off-thread will throw an exception.
 */
abstract class Live<T> internal constructor() {
    /**
     * Grab the latest snapshot taken for this live instance.
     *
     * Note that the value returned here is *not* live. That is, even if this live instance depends
     * on another one, the snapshot may be stale. (It will get updated in the future whenever the
     * backing graph finishes its next update pass).
     *
     * Users of this class often actually want the live value (otherwise, why even using a `Live`
     * in the first place?). Therefore, in most cases, users should access this instance's value
     * via its `get` method, which is only made available inside an `observe` block, e.g. within
     * [Lively.create] and [Lively.listen].
     *
     * To summarize:
     *
     * ```
     * // Set only once...
     * liveSrc.set("hello")
     * liveDst.set(liveSrc.getSnapshot()) // dst -> "hello"
     * liveSrc.set("world")               // dst -> "hello"
     *
     * // Kept in sync...
     * liveSrc.set("hello")
     * liveDst.observe { liveSrc.get() } // dst -> "hello"
     * liveSrc.set("world")              // dst -> "world"
     * ```
     */
    abstract fun getSnapshot(): T

    abstract val frozen: Boolean
    abstract fun freeze()

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
class MutableLive<T> private constructor(private val lively: Lively) : Live<T>() {

    private class WrappedValue<T>(var value: T)

    internal constructor(lively: Lively, initialValue: T) : this(lively) {
        snapshot = WrappedValue(initialValue)
    }
    internal constructor(lively: Lively, observe: LiveScope.() -> T) : this(lively) {
        lively.scope.recordDependencies(this) { snapshot = WrappedValue(observe()) }
        this.observe = observe
    }

    init {
        lively.graph.add(this)
    }

    val onValueChanged: Event<T>
        get() = lively.graph.onValueChanged(this)

    val onFroze: UnitEvent
        get() = lively.graph.onFroze(this)

    /**
     * An instance which wraps the last snapshotted value.
     *
     * Note: Ideally this would have just been `private lateinit var snapshot: T` but
     * lateinit does not support potentially nullable types, e.g. `T = Int?`
     * By creating a wrapper class, we can ensure that `WrappedValue<T>` is a non-nullable type,
     * even if `T` is itself nullable.
     */
    private lateinit var snapshot: WrappedValue<T>

    /**
     * See [observe].
     */
    private var observe: (LiveScope.() -> T)? = null

    override var frozen = false
        private set

    override fun getSnapshot(): T {
        checkValidStateFor("getSnapshot", false)
        return snapshot.value
    }

    fun set(value: T) {
        checkValidStateFor("set", true)
        if (observe != null) {
            throw IllegalStateException(
                "Can't set a Live value directly if it previously called `observe`. Call `clearObserve` maybe?")
        }
        handleSet(value)
    }


    fun observe(observe: LiveScope.() -> T) {
        checkValidStateFor("observe", true)
        this.observe = observe
        runObserveIfNotNull()
    }

    fun clearObserve() {
        checkValidStateFor("clearObserve", true)
        if (this.observe != null) {
            this.observe = null
            lively.graph.setDependencies(this, emptyList())
        }
    }

    override fun freeze() {
        checkValidStateFor("freeze", true)
        clearObserve()
        frozen = true
        lively.graph.freeze(this)
    }

    override fun update() = runObserveIfNotNull()

    private fun checkValidStateFor(method: String, mutating: Boolean) {
        if (mutating && frozen) {
            throw IllegalStateException("Attempted to call `$method` on frozen live value: $this")
        }

        lively.graph.ownedThread.expectCurrent {
            "Attempting to call `$method` on $this using a thread it isn't associated with."
        }

        if (mutating && lively.scope.isRecording) {
            throw IllegalStateException("Attempted to call `$method` inside an observe block on: $this")
        }
    }

    private fun runObserveIfNotNull() {
        observe?.let { observe ->
            lively.scope.recordDependencies(this) {
                handleSet(observe())
            }
        }
    }

    private fun handleSet(value: T) {
        val valueChanged = snapshot.value != value
        snapshot.value = value
        lively.graph.notifyUpdated(this, valueChanged)
    }
}