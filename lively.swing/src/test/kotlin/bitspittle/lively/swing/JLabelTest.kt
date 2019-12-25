package bitspittle.lively.swing

import bitspittle.lively.Lively
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.extensions.createString
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import org.junit.Test
import javax.swing.JLabel

class JLabelTest {
    @Test
    fun wrapLabelTextAsSourceLiveWorks() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively = Lively(testGraph)

        val label = JLabel("Initial text")
        assertThat(label.componentListeners.isEmpty())

        val liveText = lively.wrapText(label)
        assertThat(label.componentListeners.isNotEmpty())

        val allCaps = lively.create { liveText.get().toUpperCase() }
        assertThat(liveText.getSnapshot()).isEqualTo("Initial text")
        assertThat(allCaps.getSnapshot()).isEqualTo("INITIAL TEXT")

        label.text = "New text"
        assertThat(liveText.getSnapshot()).isEqualTo("New text")
        assertThat(allCaps.getSnapshot()).isEqualTo("NEW TEXT")

        liveText.set("Updated text")
        assertThat(label.text).isEqualTo("Updated text")
        assertThat(allCaps.getSnapshot()).isEqualTo("UPDATED TEXT")

        liveText.freeze()
        assertThat(label.componentListeners.isEmpty())
    }

    @Test
    fun wrapLabelTextAsObservingLiveWorks() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively = Lively(testGraph)

        val label = JLabel("Dummy text")
        assertThat(label.componentListeners.isEmpty())

        val liveStr = lively.createString("New text")

        val liveText = lively.wrapText(label) { liveStr.get() }
        assertThat(label.componentListeners.isEmpty())
        assertThat(label.text).isEqualTo("New text")

        liveStr.set("Updated text")
        assertThat(label.text).isEqualTo("Updated text")

        liveText.freeze()
        liveStr.set("")
        assertThat(label.text).isEqualTo("Updated text")
    }
}