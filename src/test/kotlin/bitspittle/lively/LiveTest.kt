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
    fun observeWorks() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        val liveStr = lively.create("")

        liveStr.observe { liveInt.get().toString() }
        assertThat(liveStr.getSnapshot()).isEqualTo("123")

        liveInt.set(456)
        assertThat(liveStr.getSnapshot()).isEqualTo("123")
        lively.graph.update()
        assertThat(liveStr.getSnapshot()).isEqualTo("456")
    }
}