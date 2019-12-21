package bitspittle.lively.extensions

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import org.junit.Test

class LivelyTest {
    private val testGraph = LiveGraph(RunImmediatelyExecutor())

    @Test
    fun primitiveCreateMethodsWork() {
        val lively = Lively(testGraph)
        val liveBool: Live<Boolean> = lively.createBool()
        val liveByte: Live<Byte> = lively.createByte()
        val liveShort: Live<Short> = lively.createShort()
        val liveInt: Live<Int> = lively.createInt()
        val liveLong: Live<Long> = lively.createLong()
        val liveFloat: Live<Float> = lively.createFloat()
        val liveDouble: Live<Double> = lively.createDouble()
        val liveString: Live<String> = lively.createString()
        val liveNullInt: Live<Int?> = lively.createNullable()

        assertThat(liveBool.getSnapshot()).isFalse()
        assertThat(liveByte.getSnapshot()).isEqualTo(0.toByte())
        assertThat(liveShort.getSnapshot()).isEqualTo(0.toShort())
        assertThat(liveInt.getSnapshot()).isEqualTo(0)
        assertThat(liveLong.getSnapshot()).isEqualTo(0.toLong())
        assertThat(liveFloat.getSnapshot()).isEqualTo(0.0f)
        assertThat(liveDouble.getSnapshot()).isEqualTo(0.0)
        assertThat(liveString.getSnapshot()).isEqualTo("")
        assertThat(liveNullInt.getSnapshot()).isNull()
    }

}