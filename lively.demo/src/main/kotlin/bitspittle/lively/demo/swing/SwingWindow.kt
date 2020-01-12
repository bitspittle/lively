package bitspittle.lively.demo.swing

import bitspittle.lively.event.MutableUnitEvent
import bitspittle.lively.event.UnitEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.WindowConstants

interface SwingForm {
    val root: JPanel
}

class SwingWindow(private val title: String) {
    private val _onClosed = MutableUnitEvent()
    val onClosed: UnitEvent = _onClosed
    fun show(form: SwingForm) {
        val frame = JFrame(title)
        frame.contentPane = form.root
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isResizable = false
        frame.isVisible = true
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                _onClosed()
            }
        })
    }
}
