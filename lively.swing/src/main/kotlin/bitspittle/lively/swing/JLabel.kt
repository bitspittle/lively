package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.SourceLive
import java.beans.PropertyChangeListener
import javax.swing.JLabel

private fun JLabel.setTextIfDifferent(newText: String) {
    if (text != newText) {
        text = newText
    }
}

fun Lively.wrapSelected(label: JLabel): SourceLive<String> {
    val liveText = create(label.text)
    val textListener = PropertyChangeListener { liveText.set(label.text) }
    label.addPropertyChangeListener("text", textListener)
    liveText.onValueChanged += { label.setTextIfDifferent(it) }
    liveText.onFroze += { label.removePropertyChangeListener("text", textListener) }

    return liveText
}

fun Lively.wrapText(label: JLabel, observe: LiveScope.() -> String): ObservingLive<String> {
    val liveText = create(observe)
    listen { label.setTextIfDifferent(liveText.get()) }

    return liveText
}