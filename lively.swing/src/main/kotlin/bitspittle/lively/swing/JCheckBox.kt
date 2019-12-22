package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.MutableLive
import bitspittle.lively.SettableLive
import java.awt.event.ActionListener
import javax.swing.JCheckBox

fun Lively.wrapSelected(checkBox: JCheckBox): SettableLive<Boolean> {
    val liveSelected = create(checkBox.isSelected)
    val selectedListener = ActionListener { liveSelected.set(checkBox.isSelected) }
    checkBox.addActionListener(selectedListener)
    liveSelected.onValueChanged += { checkBox.isSelected = it }
    liveSelected.onFroze += { checkBox.removeActionListener(selectedListener) }

    return liveSelected
}

fun Lively.wrapSelected(checkBox: JCheckBox, block: LiveScope.() -> Boolean): MutableLive<Boolean> {
    val liveSelected = create(block)
    listen { checkBox.isSelected = liveSelected.get() }

    return liveSelected
}