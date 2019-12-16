package bitspittle.lively

import bitspittle.lively.internal.LiveGraph
import bitspittle.truthish.assertThat
import org.junit.Test


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
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")

        lively.graph.update(liveStr1)
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")
        lively.graph.update(liveStr2)
        assertThat(liveStr2.getSnapshot()).isEqualTo("456")

        liveInt.set(789)
        lively.graph.updateAll()
        assertThat(liveStr1.getSnapshot()).isEqualTo("789")
        assertThat(liveStr2.getSnapshot()).isEqualTo("789")
    }
}