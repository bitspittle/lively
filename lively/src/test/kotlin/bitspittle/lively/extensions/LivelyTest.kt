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
        val liveBool: Live<Boolean> = lively.sourceBool()
        val liveByte: Live<Byte> = lively.sourceByte()
        val liveShort: Live<Short> = lively.sourceShort()
        val liveInt: Live<Int> = lively.sourceInt()
        val liveLong: Live<Long> = lively.sourceLong()
        val liveFloat: Live<Float> = lively.sourceFloat()
        val liveDouble: Live<Double> = lively.sourceDouble()
        val liveString: Live<String> = lively.sourceString()
        val liveNullInt: Live<Int?> = lively.sourceNullable()

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