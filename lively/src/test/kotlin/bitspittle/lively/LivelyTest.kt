package bitspittle.lively

import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.extensions.createInt
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test
import java.lang.IllegalArgumentException
import java.util.concurrent.CountDownLatch

class LivelyTest {
    @Test
    fun defaultExecutorThrowsException() {
        val lively = Lively()
        val int1 = lively.create(123)
        lively.create { int1.get() }

        assertThrows<IllegalStateException> {
            int1.set(200)
        }
    }

    @Test
    fun liveValuesCannotCrossThreadBoundaries() {
        lateinit var lively: Lively
        lateinit var liveInt: SourceLive<Int>
        val latchValuesSet = CountDownLatch(1)
        val latchThread2Finished = CountDownLatch(1)

        val threads = listOf(
            Thread {
                lively = Lively()
                liveInt = lively.create(123)
                latchValuesSet.countDown()
                latchThread2Finished.await()
                assertThat(liveInt.frozen).isFalse()
                assertThat(liveInt.getSnapshot()).isEqualTo(123)
            },
            Thread {
                latchValuesSet.await()
                assertThrows<IllegalStateException> { liveInt.getSnapshot() }
                assertThrows<IllegalStateException> { liveInt.set(999) }
                assertThrows<IllegalStateException> { liveInt.freeze() }
                assertThrows<IllegalStateException> { liveInt.onValueChanged += {} }
                assertThrows<IllegalStateException> { liveInt.onFroze += {} }

                assertThrows<IllegalStateException> { lively.create("Never created") }
                assertThrows<IllegalStateException> { lively.create { false } }
                assertThrows<IllegalStateException> { lively.listen { } }
                assertThrows<IllegalStateException> { lively.freeze() }

                latchThread2Finished.countDown()
            }
        )

        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test
    fun livelyInstancesOnTheSameThreadHaveTheSameBackingGraph() {
        val lively1 = Lively()
        val lively2 = Lively()
        assertThat(lively1.graph).isSameAs(lively2.graph)

    }

    @Test
    fun multipleLivelyInstancesAreBackedByTheSameGraph() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively1 = Lively(testGraph)
        val lively2 = Lively(testGraph)
        val lively3 = Lively(testGraph)

        val int1 = lively1.createInt(123)
        val dbl2 = lively2.create { int1.get().toDouble() / 10.0 }
        val str3 = lively3.create { dbl2.get().toString() }

        assertThat(str3.getSnapshot()).isEqualTo("12.3")

        assertThat(testGraph.nodeCount).isEqualTo(3)
        lively2.freeze() // Severs connection between str3 and int1

        int1.set(9000)
        assertThat(str3.getSnapshot()).isEqualTo("12.3")
        assertThat(testGraph.nodeCount).isEqualTo(1)

        lively1.freeze()
        assertThat(testGraph.nodeCount).isEqualTo(0)
    }

    @Test
    fun livelyInstancesWithDifferentGraphsCannotInteract() {
        val testGraph1 = LiveGraph(RunImmediatelyExecutor())
        val testGraph2 = LiveGraph(RunImmediatelyExecutor())
        val lively1 = Lively(testGraph1)
        val lively2 = Lively(testGraph2)

        val int1 = lively1.createInt()

        assertThrows<IllegalArgumentException> { lively2.create { int1.get() } }
    }
}