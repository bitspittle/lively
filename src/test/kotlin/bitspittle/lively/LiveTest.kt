package bitspittle.lively

import bitspittle.lively.internal.LiveGraph
import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test
import java.lang.IllegalArgumentException


/**
 * TODO: Header comment.
 */
class LiveTest {
    private val testGraph = LiveGraph()

    @Test
    fun setAndGetSnapshotWork() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        assertThat(liveInt.getSnapshot()).isEqualTo(123)

        liveInt.set(456)
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
    }

    @Test
    fun primitiveCreateMethodsWork() {
        val lively = Lively(testGraph)
        val liveByte: Live<Byte> = lively.createByte()
        val liveShort: Live<Short> = lively.createShort()
        val liveInt: Live<Int> = lively.createInt()
        val liveLong: Live<Long> = lively.createLong()
        val liveFloat: Live<Float> = lively.createFloat()
        val liveDouble: Live<Double> = lively.createDouble()
        val liveString: Live<String> = lively.createString()

        assertThat(liveByte.getSnapshot()).isEqualTo(0.toByte())
        assertThat(liveShort.getSnapshot()).isEqualTo(0.toShort())
        assertThat(liveInt.getSnapshot()).isEqualTo(0)
        assertThat(liveLong.getSnapshot()).isEqualTo(0.toLong())
        assertThat(liveFloat.getSnapshot()).isEqualTo(0.0f)
        assertThat(liveDouble.getSnapshot()).isEqualTo(0.0)
        assertThat(liveString.getSnapshot()).isEqualTo("")
    }

    @Test
    fun observeWorks() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        val liveStr1 = lively.createString()
        assertThat(liveStr1.getSnapshot()).isEmpty()

        val liveStr2 = lively.create { liveInt.get().toString() }
        liveStr1.observe { liveInt.get().toString() }

        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")

        liveInt.set(456)
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("456")
    }

    @Test
    fun cyclesNotAllowed() {
        val lively = Lively(testGraph)

        val liveInt1 = lively.createInt()
        val liveInt2 = lively.createInt()
        val liveInt3 = lively.createInt()

        liveInt3.observe { liveInt2.get() }
        liveInt2.observe { liveInt1.get() }

        assertThrows<IllegalArgumentException> {
            liveInt1.observe { liveInt3.get() }
        }

        // Clearing the observe callback clears the dependencies
        liveInt3.clearObserve()
        liveInt1.observe { liveInt3.get() }
    }
}