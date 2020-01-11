package bitspittle.lively.extensions

import bitspittle.lively.Lively
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import org.junit.Test

/**
 * TODO: Header comment.
 */
class LiveTest {
    @Test
    fun twoLivesWithDifferentTypesCanBeTwoWayBound() {
        val lively = Lively(LiveGraph(RunImmediatelyExecutor()))

        val liveInt = lively.sourceInt(123)
        val liveStr = lively.sourceString()
        val inSync = lively.observing { liveInt.get().toString() == liveStr.get() }
        liveStr.bindTo(liveInt, { strVal -> strVal.toIntOrNull() }, { intVal -> intVal.toString() })

        assertThat(liveInt.getSnapshot()).isEqualTo(123)
        assertThat(liveStr.getSnapshot()).isEqualTo("123")
        assertThat(inSync.getSnapshot()).isTrue()

        liveInt.set(987)
        assertThat(liveStr.getSnapshot()).isEqualTo("987")
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.set("456")
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.set("Uh oh")
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
        assertThat(inSync.getSnapshot()).isFalse()

        liveStr.set("999")
        assertThat(liveInt.getSnapshot()).isEqualTo(999)
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.freeze()
        liveInt.set(0)

        assertThat(liveStr.getSnapshot()).isEqualTo("999")
        assertThat(liveInt.getSnapshot()).isEqualTo(0)
    }

    @Test
    fun twoLivesWithTheSameTypesCanBeTwoWayBound() {
        val lively = Lively(LiveGraph(RunImmediatelyExecutor()))

        val liveStr1 = lively.source("will get overwritten")
        val liveStr2 = lively.source("initial value")
        liveStr1.bindTo(liveStr2)

        assertThat(liveStr1.getSnapshot()).isEqualTo("initial value")

        liveStr1.set("new value")
        assertThat(liveStr2.getSnapshot()).isEqualTo("new value")

        liveStr2.set("updated value")
        assertThat(liveStr1.getSnapshot()).isEqualTo("updated value")

        liveStr2.freeze()
        liveStr1.set("final value")

        assertThat(liveStr2.getSnapshot()).isEqualTo("updated value")
    }
}