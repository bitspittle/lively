package bitspittle.lively.exec

/**
 * A simple interface that abstracts exactly how we run some logic.
 */
interface Executor {
    fun submit(runnable: () -> Unit)
}

/**
 * An executor that throws an exception immediately. This can be useful as a default value if you
 * want to require users to overwrite it with an executor specialized to their codebase.
 */
class ThrowingExecutor(private val message: String) : Executor {
    override fun submit(runnable: () -> Unit) = throw IllegalStateException(message)
}

/**
 * A simple executor which runs the runnable right away.
 *
 * Probably production code would benefit from a more robust strategy, but this can be useful for
 * simple tests at the very least.
 */
class RunImmediatelyExecutor : Executor {
    override fun submit(runnable: () -> Unit) = runnable()
}

/**
 * An executor that requires manual intervention to move it forward.
 *
 * This is particularly useful for fine-grained unit testing.
 *
 * Note: Since the assumption is this class is for tests, and tests should fail fast, trying to
 * call any of the run methods on an empty executor throws an exception. That is, it's assumed you
 * are controlling an executor where you already know when it should be empty and should be filled.
 * (We may revisit this decision if it ends up being problematic in practice).
 */
class ManualExecutor : Executor {
    private val enqueued = mutableListOf<() -> Unit>()
    override fun submit(runnable: () -> Unit) {
        enqueued.add(runnable)
    }

    val count
        get() = enqueued.size

    fun runNext() {
        checkNonEmpty()

        val next = enqueued.removeAt(0)
        next()
    }

    /**
     * Run until the target [condition] is met.
     *
     * It is considered an error if all runnables run and the condition is never met.
     */
    fun runUntil(condition: () -> Boolean) {
        checkNonEmpty()

        var conditionMet = false
        while (!conditionMet && enqueued.isEmpty()) {
            runNext()
            conditionMet = condition()
        }

        if (!conditionMet) {
            throw IllegalArgumentException("Condition never met.")
        }
    }

    fun runRemaining() {
        checkNonEmpty()

        while (enqueued.isNotEmpty()) {
            runNext()
        }
    }

    private fun checkNonEmpty() {
        if (enqueued.isEmpty()) {
            throw IllegalStateException("Executor is empty")
        }
    }
}