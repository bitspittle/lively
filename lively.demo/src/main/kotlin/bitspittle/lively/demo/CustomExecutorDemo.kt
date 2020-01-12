package bitspittle.lively.demo

import bitspittle.lively.Lively
import bitspittle.lively.SourceLive
import bitspittle.lively.exec.Executor
import bitspittle.truthish.assertThat

/**
 * Demo which demonstrates how one might write a simple custom executor from scratch
 *
 * In this demo, we create an executor that postponed running actions until later. This allows
 * 100 individual live value changes to only cause a single update instead of 100 separate updates.
 */
fun main() {
    CustomExecutorDemo().run()
}

class CustomExecutorDemo {
    private val actionsToRun = mutableListOf<() -> Unit>()
    inner class EnqueueingExecutor : Executor {
        override fun submit(runnable: () -> Unit) {
            actionsToRun.add(runnable)
        }
    }

    fun run() {
        Lively.executor = EnqueueingExecutor()

        val lively = Lively()
        val liveInts = mutableListOf<SourceLive<Int>>()
        for (i in 1..100) {
            liveInts.add(lively.source(i))
        }
        val sum = lively.observing {
            liveInts.sumBy { liveInt -> liveInt.get() }
        }

        var sumUpdatedCount = 0
        sum.onValueChanged += { sumUpdatedCount++ }

        assertThat(sum.getSnapshot()).isEqualTo(5050)
        assertThat(sumUpdatedCount).isEqualTo(0)

        liveInts.forEach { liveInt -> liveInt.set(0) }
        exhaustAllActions()

        assertThat(sum.getSnapshot()).isEqualTo(0)
        assertThat(sumUpdatedCount).isEqualTo(1)

        println("If you see this message, the demo completed successfully.")
    }

    private fun exhaustAllActions() {
        while (actionsToRun.isNotEmpty()) {
            val actionsToRunCopy = actionsToRun.toList()
            actionsToRun.clear()
            actionsToRunCopy.forEach { action -> action() }
        }
    }
}
