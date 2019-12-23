package bitspittle.lively.swing

import bitspittle.lively.LiveScope
import bitspittle.lively.Lively
import bitspittle.lively.ObservingLive
import bitspittle.lively.SourceLive
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

private fun JTextComponent.setTextIfDifferent(newText: String) {
    if (text != newText) {
        text = newText
    }
}

class DocumentAdapter(private val onChanged: () -> Unit) : DocumentListener {
    override fun changedUpdate(p0: DocumentEvent?) {
        onChanged()
    }

    override fun insertUpdate(p0: DocumentEvent?) {
        onChanged()
    }

    override fun removeUpdate(p0: DocumentEvent?) {
        onChanged()
    }
}

fun Lively.wrapText(textComponent: JTextComponent): SourceLive<String> {
    val liveText = create(textComponent.text)
    val docListener = DocumentAdapter { liveText.set(textComponent.text) }
    textComponent.document.addDocumentListener(docListener)
    liveText.onValueChanged += { textComponent.setTextIfDifferent(it) }
    liveText.onFroze += { textComponent.document.removeDocumentListener(docListener) }

    return liveText
}

fun Lively.wrapText(textComponent: JTextComponent, observe: LiveScope.() -> String): ObservingLive<String> {
    val liveText = create(observe)
    listen { textComponent.setTextIfDifferent(liveText.get()) }
    return liveText
}