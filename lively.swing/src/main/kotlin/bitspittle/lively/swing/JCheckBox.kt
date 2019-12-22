package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.SourceLive
import java.awt.event.ActionListener
import javax.swing.JCheckBox

fun Lively.wrapSelected(checkBox: JCheckBox): SourceLive<Boolean> {
    val liveSelected = create(checkBox.isSelected)
    val selectedListener = ActionListener { liveSelected.set(checkBox.isSelected) }
    checkBox.addActionListener(selectedListener)
    liveSelected.onValueChanged += { checkBox.isSelected = it }
    liveSelected.onFroze += { checkBox.removeActionListener(selectedListener) }

    return liveSelected
}

fun Lively.wrapSelected(checkBox: JCheckBox, observe: LiveScope.() -> Boolean): ObservingLive<Boolean> {
    val liveSelected = create(observe)
    listen { checkBox.isSelected = liveSelected.get() }

    return liveSelected
}