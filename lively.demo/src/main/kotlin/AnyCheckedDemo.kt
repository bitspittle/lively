import bitspittle.lively.Lively
import bitspittle.lively.swing.SwingExecutor
import bitspittle.lively.swing.wrapSelected
import bitspittle.lively.swing.wrapText
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

fun main() {
    Lively.executor = SwingExecutor()
    SwingUtilities.invokeLater {
        AnyCheckedDemo()
    }
}

class AnyCheckedDemo {
    private class Form : SwingForm {
        override val root = JPanel(BorderLayout())
        val checkBox1 = JCheckBox()
        val checkBox2 = JCheckBox()
        val checkBox3 = JCheckBox()
        val checkBox4 = JCheckBox()
        val yesNoLabel = JLabel()

        init {
            val checkBoxGroup = JPanel(FlowLayout()).apply {
                add(checkBox1)
                add(checkBox2)
                add(checkBox3)
                add(checkBox4)
            }
            val questionLabel = JLabel("Any checked? ")
            val labelGroup = JPanel(FlowLayout()).apply {
                add(questionLabel)
                add(yesNoLabel)
            }

            val vGroup = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(checkBoxGroup)
                add(labelGroup)
            }

            root.add(vGroup)
        }
    }

    init {
        val form = Form()

        // Declare UI relationships with lively
        val lively = Lively()
        val liveSelectedValues = listOf(
            lively.wrapSelected(form.checkBox1),
            lively.wrapSelected(form.checkBox2),
            lively.wrapSelected(form.checkBox3),
            lively.wrapSelected(form.checkBox4)
        )

        lively.wrapText(form.yesNoLabel) {
            if (liveSelectedValues.any { it.get() }) "Yes" else "No"
        }

        // Show the demo
        val window = SwingWindow(AnyCheckedDemo::class.java.simpleName)
        window.show(form)
        window.onClosed += { lively.freeze() }
    }
}