@file:Suppress("LeakingThis") // "this" only leaked as hashcode

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
 * as a live factory instead. See also: [Lively.create]
 *
 * When a live instance is created, it is associated with a thread, and any attempt to access it
 * off-thread will throw an exception.
 */
interface Live<T> {
    val onValueChanged: Event<T>
    val onFroze: UnitEvent
    val frozen: Boolean

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
     * # Set only once...
     * liveSrc = lively.create("hello")
     * liveDst = lively.create(liveSrc.getSnapshot())
     * // liveDst equals "hello"
     * liveSrc.set("world")
     * // liveDst still equals "hello"
     *
     * # Kept in sync...
     * liveSrc = lively.create("hello")
     * liveDst = lively.create { liveSrc.get() }
     * // liveDst equals "hello"
     * liveSrc.set("world")
     * // liveDst updated to "world"
     * ```
     */
    fun getSnapshot(): T
}

/**
 * An interface to expose for source nodes you want to provide [set] access to for callers without
 * allowing them to freeze it.
 */
interface SettableLive<T> : Live<T> {
    fun set(value: T)
}

/**
 * A mutable access to a [Live] instance.
 *
 * These are values returned by [Lively], so owners of the values have extra permissions when
 * interacting with them. However, when exposing live instances to callers, you may wish to only
 * expose the [Live] or [SettableLive], so callers can't freeze them unexpectedly, for example.
 */
abstract class MutableLive<T>(private val lively: Lively) : Live<T> {
    init {
        lively.graph.add(this)
    }

    /**
     * See comment for [snapshot].
     */
    protected class WrappedValue<T>(var value: T)

    /**
     * An instance which wraps the last snapshotted value.
     *
     * Note: Ideally this would have just been `private lateinit var snapshot: T` but
     * lateinit does not support potentially nullable types, e.g. `T = Int?`
     * By creating a wrapper class, we can ensure that `WrappedValue<T>` is a non-nullable type,
     * even if `T` is itself nullable.
     *
     * This value MUST get set by subclass constructors.
     */
    protected lateinit var snapshot: WrappedValue<T>

    override val onValueChanged: Event<T>
        get() {
            checkValidStateFor("onValueChanged", true, frozenAware = true)
            return if (frozen) StubEvent.typed() else { lively.graph.onValueChanged(this) }
        }

    override val onFroze: UnitEvent
        get() {
            checkValidStateFor("onFroze", true, frozenAware = true)
            return if (frozen) StubUnitEvent else lively.graph.onFroze(this)
        }

    override var frozen = false

    override fun getSnapshot(): T {
        checkValidStateFor("getSnapshot", false)
        return snapshot.value
    }

    /**
     * Immediately lock this Live value to its snapshot, rendering it immutable.
     *
     * This will have the added effect of removing the value from the dependency graph, clearing
     * all events, and releasing some memory. Once called, only [getSnapshot] will work
     * -- any attempt to mutate the object will result in an exception.
     */
    fun freeze() {
        checkValidStateFor("freeze", true, frozenAware = true)
        if (!frozen) {
            frozen = true
            lively.graph.freeze(this)
        }
    }

    /**
     * An inner set method, not to be exposed to normal callers.
     */
    protected fun setDirectly(value: T) {
        assert (!frozen)
        val valueChanged = snapshot.value != value
        snapshot.value = value
        lively.graph.notifyUpdated(this, valueChanged)
    }

    protected fun checkValidStateFor(method: String, mutating: Boolean, frozenAware: Boolean = false) {
        if (mutating && !frozenAware && frozen) {
            throw IllegalStateException("Attempted to call `$method` on frozen live value: $this")
        }

        lively.graph.ownedThread.expectCurrent {
            "Attempting to call `$method` on $this using a thread it isn't associated with."
        }

        if (mutating && lively.scope.isRecording) {
            throw IllegalStateException("Attempted to call `$method` inside an observe block on: $this")
        }
    }

    override fun toString(): String {
        return "Live{${getSnapshot()}}"
    }
}

/**
 * A mutable [Live] whose value can not be set directly, but rather one that derives its value
 * listening to other live values.
 */
class ObservingLive<T> internal constructor(private val lively: Lively, private val observe: LiveScope.() -> T) : MutableLive<T>(lively) {
    init {
        lively.scope.recordDependencies(this) { snapshot = WrappedValue(observe()) }
    }

    internal fun update() {
        lively.scope.recordDependencies(this) {
            setDirectly(observe())
        }
    }
}

/**
 * A mutable [Live] that represents a source value which can be changed by calling [set].
 */
class SourceLive<T> internal constructor(lively: Lively, initialValue: T) : MutableLive<T>(lively), SettableLive<T> {
    init {
        snapshot = WrappedValue(initialValue)
    }

    override fun set(value: T) {
        checkValidStateFor("set", true)
        setDirectly(value)
    }
}