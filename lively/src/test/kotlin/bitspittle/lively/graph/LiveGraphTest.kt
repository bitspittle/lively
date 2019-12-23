package bitspittle.lively.graph

import bitspittle.lively.Lively
import bitspittle.lively.exec.Executor
import bitspittle.truthish.assertThat
import org.junit.Test
import kotlin.IllegalStateException

class LiveGraphTest {
    @Test
    fun eachThreadGetsItsOwnLiveGraph() {
        var liveGraph1: LiveGraph? = null
        var liveGraph2: LiveGraph? = null

        val threads = listOf(
            Thread { liveGraph1 = LiveGraph.instance },
            Thread { liveGraph2 = LiveGraph.instance }
        )

        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertThat(liveGraph1!!).isNotSameAs(liveGraph2!!)
    }

    @Test
    fun executorCannotSneakilyRunTasksOnADifferentThread() {
        var exceptionThrownByExecutorThread = false
        val sneakyExecutor = object : Executor {
            override fun submit(runnable: () -> Unit) {
                Thread {
                    try {
                        runnable()
                    }
                    catch (e: IllegalStateException) {
                        exceptionThrownByExecutorThread = true
                    }
                }.apply {
                    start()
                    join()
                }
            }
        }
        val liveGraph = LiveGraph(sneakyExecutor)
        val lively = Lively(liveGraph)

        val liveInt1 = lively.create(123)
        val liveInt2 = lively.create { liveInt1.get() * 2 }

        assertThat(liveInt2.getSnapshot()).isEqualTo(246)

        assertThat(exceptionThrownByExecutorThread).isFalse()
        liveInt1.set(200)
        assertThat(exceptionThrownByExecutorThread).isTrue()
        assertThat(liveInt2.getSnapshot()).isEqualTo(246) // Invalid executor didn't affect state
    }
}