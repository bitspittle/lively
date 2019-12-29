@file:Suppress("LeakingThis") // "this" only leaked as hashcode

package bitspittle.lively

import bitspittle.lively.event.Event
import bitspittle.lively.event.StubEvent
import bitspittle.lively.event.StubUnitEvent
import bitspittle.lively.event.UnitEvent
import bitspittle.lively.extensions.expectCurrent
import bitspittle.lively.graph.LiveGraph

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
     * [Lively.create] and [Lively.observe].
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
 * An interface for a [Live] instance that can be frozen.
 */
interface FreezableLive<T> : Live<T> {
    fun freeze()
}

/**
 * An interface to expose for source nodes you want to provide [set] access to for callers without
 * allowing them to freeze it.
 */
interface SettableLive<T> : Live<T> {
    fun set(value: T)
}

/**
 * Shared implementation logic for all [Live] instances.
 */
private class LiveImpl<T>(
    private val graph: LiveGraph,
    private val scope: LiveScope,
    private val target: Live<T>,
    private var value: T) {

    init {
        graph.add(target)
    }

    val onValueChanged: Event<T>
        get() {
            checkValidStateFor("onValueChanged", frozenAware = true)
            return if (frozen) StubEvent.typed() else { graph.onValueChanged(target) }
        }

    val onFroze: UnitEvent
        get() {
            checkValidStateFor("onFroze", frozenAware = true)
            return if (frozen) StubUnitEvent else graph.onFroze(target)
        }

    var frozen = false

    fun getSnapshot(): T {
        return value
    }

    /**
     * Immediately lock this Live value to its snapshot, rendering it immutable.
     *
     * This will have the added effect of removing the value from the dependency graph, clearing
     * all events, and releasing some memory. Once called, only [getSnapshot] will work
     * -- any attempt to mutate the object will result in an exception.
     */
    fun freeze() {
        checkValidStateFor("freeze", frozenAware = true)
        if (!frozen) {
            frozen = true
            graph.freeze(target)
        }
    }

    /**
     * An inner set method, not to be exposed to normal callers.
     */
    fun setDirectly(value: T): Boolean {
        assert (!frozen)
        val valueChanged = this.value != value
        this.value = value
        return valueChanged
    }

    /**
     * Verify that the current method is in an acceptable state to be called.
     *
     * This should be a method that mutates the Live value somehow, meaning it shouldn't be called
     * inside an observe block.
     *
     * @param frozenAware If true, this method can safely be called even if the live value has
     *   already been frozen.
     */
    fun checkValidStateFor(method: String, frozenAware: Boolean = false) {
        if (!frozenAware && frozen) {
            throw IllegalStateException("Attempted to call `$method` on frozen live value: $target")
        }

        graph.ownedThread.expectCurrent {
            "Attempting to call `$method` on $this using a thread it isn't associated with."
        }

        if (scope.isRecording) {
            throw IllegalStateException("Attempted to call `$method` inside an observe block on: $target")
        }
    }

    override fun toString(): String {
        return "Live{$value}"
    }
}

/**
 * A mutable [Live] whose value can not be set directly, but rather one that derives its value
 * listening to other live values.
 */
class ObservingLive<T> internal constructor(
    graph: LiveGraph,
    private val scope: LiveScope,
    private val observe: LiveScope.() -> T)
    : FreezableLive<T> {

    private lateinit var impl: LiveImpl<T>
    init {
        scope.recordDependencies(this) {
            impl = LiveImpl(graph, scope, this@ObservingLive, observe())
        }
    }

    internal fun update(): Boolean {
        impl.checkValidStateFor("update")
        return impl.setDirectly(scope.recordDependenciesAndReturn(this) { observe() })
    }

    override val onValueChanged get() = impl.onValueChanged
    override val onFroze get() = impl.onFroze

    override val frozen get() = impl.frozen

    override fun getSnapshot() = impl.getSnapshot()
    override fun freeze() = impl.freeze()

    override fun toString() = impl.toString()
}

/**
 * A mutable [Live] that represents a source value which can be changed by calling [set].
 */
class SourceLive<T> internal constructor(
    private val graph: LiveGraph,
    scope: LiveScope,
    initialValue: T)
    : FreezableLive<T>, SettableLive<T> {
    private var impl: LiveImpl<T> = LiveImpl(graph, scope, this, initialValue)

    override val onValueChanged get() = impl.onValueChanged
    override val onFroze get() = impl.onFroze
    override val frozen get() = impl.frozen

    override fun getSnapshot() = impl.getSnapshot()
    override fun freeze() = impl.freeze()

    override fun set(value: T) {
        impl.checkValidStateFor("set")
        if (impl.setDirectly(value)) {
            graph.notifyUpdated(this)
        }
    }

    override fun toString() = impl.toString()
}