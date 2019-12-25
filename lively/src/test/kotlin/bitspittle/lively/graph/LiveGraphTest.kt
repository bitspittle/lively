package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.exec.Executor
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.extensions.createInt
import bitspittle.truthish.assertThat
import org.junit.Test
import java.lang.ref.WeakReference
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

    @Test
    fun graphCleanedUpIfLiveValuesCanBeGarbageCollected() {
        lateinit var weakLively: WeakReference<Lively>
        lateinit var testGraph: LiveGraph

        // While not-null, this reference keeps live liveDst alive which keeps liveSrc alive too
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var liveDstReference: Live<Int>? = null

        Thread {
            testGraph = LiveGraph(RunImmediatelyExecutor())
            val lively = Lively(testGraph)
            @Suppress("UNUSED_VARIABLE") // Var created for readability
            val liveOrphan = lively.createInt()
            val liveSrc = lively.createInt()
            val liveDst = lively.create { liveSrc.get() }

            assertThat(testGraph.nodeCount).isEqualTo(3)
            weakLively = WeakReference(lively)
            liveDstReference = liveDst
        }.apply {
            start()
            join()
        }

        assertThat(testGraph.nodeCount).isEqualTo(3)
        while (weakLively.get() != null) {
            System.gc()
            Thread.sleep(10)
        }
        assertThat(testGraph.nodeCount).isEqualTo(2)

        liveDstReference = null // Removing this allows both liveSrc and liveDst to be collected
        while (testGraph.nodeCount > 0) {
            System.gc()
            Thread.sleep(10)
        }
    }
}