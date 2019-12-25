package bitspittle.lively

import bitspittle.lively.graph.LiveGraph

/**
 * A scope within which [Live] instances can be queried for their live value.
 */
class LiveScope internal constructor(private val graph: LiveGraph) {
    private val recordedDepsStack = mutableListOf<MutableSet<Live<*>>>()

    val isRecording
        get() = recordedDepsStack.isNotEmpty()

    /**
     * Gets the *live* value of this [Live].
     *
     * This method is only accessible inside a scoped block. See also: [Lively.create] and
     * [Lively.listen].
     *
     * This not only returns the value of the live instance, but it also updates the backing graph,
     * so that any future changes made to this instance will also notify any dependent live
     * instance.
     *
     * For example, in this block:
     *
     * ```
     * val firstName = lively.create("John")
     * val lastName = lively.create("Doe")
     * val fullName = lively.create { "${firstName.get()} ${lastName.get()}" }
     * ```
     *
     * we've both set up that `fullName` is set to `John Doe` and also that any time either the
     * first and/or last name values change, `fullName` will later be updated, because it is
     * dependent on them.
     *
     * Dependencies can even change dynamically:
     *
     * ```
     * val showNickname = lively.create(false)
     * val name = lively.create("Steve")
     * val nickname = lively.create("Sir Stevey")
     * val display = lively.create {
     *   if (showNickname.get()) nickname.get() else name.get()
     * }
     * ```
     *
     * Here, `display` depends on `showNickname` and `name`, if `showNickname` is false, or
     * `showNickname` and `nickname`, if `nickname` is true.
     */
    fun <T> Live<T>.get(): T {
        if (!this.frozen) {
            recordedDepsStack.last().add(this)
        }
        return getSnapshot()
    }

    internal fun <T> recordDependenciesAndReturn(live: Live<T>, block: LiveScope.() -> T): T {
        recordedDepsStack.add(mutableSetOf())
        try {
            val result = block()
            graph.setDependencies(live, recordedDepsStack.last())

            return result
        }
        finally {
            recordedDepsStack.removeAt(recordedDepsStack.lastIndex)
        }
    }

    internal fun <T> recordDependencies(live: Live<T>, block: LiveScope.() -> Unit) {
        recordDependenciesAndReturn(live) {
            block()
            // We don't care about the return value but we have to return something so we can delegate to
            // the other record method.
            live.getSnapshot()
        }
    }
}