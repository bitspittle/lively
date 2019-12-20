package bitspittle.lively.exec

import bitspittle.lively.extensions.expectCurrent

class BatchExecutor(private val delegate: Executor) : Executor {
    private val batch = mutableListOf<() -> Unit>()

    override fun submit(runnable: () -> Unit) {
        batch.add(runnable)
        if (batch.size == 1) {
            // Submit a runnable only after our first item is added. If the delegate executor runs
            // right away, then it will just be handled and cleared. But if the delegate executor
            // takes awhile, additional runnables will be batched up.
            val originThread = Thread.currentThread()
            delegate.submit {
                // Ensure we're on the same thread, so we don't have to synchronize accesss to `batch`
                originThread.expectCurrent { "Delegate executor cannot change the current thread." }

                batch.forEach { runnable -> runnable() }
                batch.clear()
            }
        }
    }
}