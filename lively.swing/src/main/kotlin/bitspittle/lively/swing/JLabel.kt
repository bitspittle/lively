package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.MutableLive
import bitspittle.lively.SettableLive
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JCheckBox
import javax.swing.JLabel

fun Lively.wrapSelected(label: JLabel): SettableLive<String> {
    val liveText = create(label.text)
    val textListener = PropertyChangeListener { liveText.set(label.text) }
    label.addPropertyChangeListener("text", textListener)
    liveText.onValueChanged += { label.text = it }
    liveText.onFroze += { label.removePropertyChangeListener("text", textListener) }

    return liveText
}

fun Lively.wrapText(label: JLabel, block: LiveScope.() -> String): MutableLive<String> {
    val liveText = create(block)
    listen { label.text = liveText.get() }

    return liveText
}