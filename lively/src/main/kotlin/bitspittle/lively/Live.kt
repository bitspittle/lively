package bitspittle.lively

import bitspittle.lively.event.Event
import bitspittle.lively.event.StubEvent
import bitspittle.lively.event.StubUnitEvent
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
    abstract val onValueChanged: Event<T>
    abstract val onFroze: UnitEvent
    abstract val frozen: Boolean

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

    internal abstract fun update()

    override fun toString(): String {
        return "Live{${getSnapshot()}}"
    }
}

/**
 * An interface to a [Live] with more permissions - this is useful for the part of the project that
 * created it, whereas it might expose the non-mutable part via getters.
 */
abstract class MutableLive<T> : Live<T>() {
    /**
     * Immediately lock this Live value to its snapshot, rendering it immutable.
     *
     * This will have the added effect of removing the value from the dependency graph and
     * releasing some memory. Once called, only [getSnapshot] will work -- any attempt to
     * mutate the object will result in an exception.
     */
    abstract fun freeze()
}

/**
 * A [MutableLive] that provides a [set] method.
 *
 * As an API, this is designed to be exposed for [Live] values that are leaf nodes - that is, they
 * are standalone values that don't depend on anything, but others may depend on them. Live values
 * which depend on other live values should be exposed to callers via the [MutableLive] interface.
 *
 * As an implementation detail, this class currently implements both leaf nodes and intermediate
 * nodes (i.e. it manages the logic around observing target nodes), but as far as users of the
 * class are concerned, this detail is concealed from them.
 */
class SettableLive<T> private constructor(private val lively: Lively) : MutableLive<T>() {
    /**
     * See comment for [snapshot].
     */
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

    override val onValueChanged: Event<T>
        get() {
            if (frozen) {
                return StubEvent.typed()
            }
            checkValidStateFor("onValueChanged", true)
            return lively.graph.onValueChanged(this)
        }

    override val onFroze: UnitEvent
        get() {
            if (frozen) {
                return StubUnitEvent
            }
            checkValidStateFor("onFroze", true)
            return lively.graph.onFroze(this)
        }

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
     * If set, this represents a live value that may depends on other live values.
     *
     * Calling [set] on a live value with an observe block will throw an exception, although if we
     * designed the API right, this should be impossible to do.
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
                "Can't set a Live value directly if it was created to observe other Live values."
            )
        }
        handleSet(value)
    }

    override fun freeze() {
        checkValidStateFor("freeze", true)
        if (this.observe != null) {
            this.observe = null
            lively.graph.setDependencies(this, emptyList())
        }
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