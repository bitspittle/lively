package bitspittle.lively

import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test
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
        lateinit var liveInt: SettableLive<Int>
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

}