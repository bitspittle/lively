import bitspittle.lively.Lively
import bitspittle.lively.swing.SwingExecutor
import bitspittle.lively.swing.wrapSelected
import bitspittle.lively.swing.wrapText
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

fun main() {
    Lively.executorFactory = { SwingExecutor() }
    SwingUtilities.invokeLater {
        AnyCheckedDemo()
    }
}

class AnyCheckedDemo {
    private val lively = Lively()

    init {
        // Initialize form
        val checkBox1 = JCheckBox()
        val checkBox2 = JCheckBox()
        val checkBox3 = JCheckBox()
        val checkBox4 = JCheckBox()
        val checkBoxGroup = JPanel(FlowLayout()).apply {
            add(checkBox1)
            add(checkBox2)
            add(checkBox3)
            add(checkBox4)
        }

        val questionLabel = JLabel("Any checked? ")
        val yesNoLabel = JLabel()
        val labelGroup = JPanel(FlowLayout()).apply {
            add(questionLabel)
            add(yesNoLabel)
        }

        val vGroup = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(checkBoxGroup)
            add(labelGroup)
        }

        val root = JPanel(BorderLayout()).apply {
            add(vGroup)
        }

        // Hook up lively
        val liveSelectedValues = listOf(
            lively.wrapSelected(checkBox1),
            lively.wrapSelected(checkBox2),
            lively.wrapSelected(checkBox3),
            lively.wrapSelected(checkBox4)
        )

        lively.wrapText(yesNoLabel) {
            if (liveSelectedValues.any { it.get() }) "Yes" else "No"
        }

        // Show window
        val frame = JFrame(AnyCheckedDemo::class.java.simpleName)
        frame.contentPane = root
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                lively.freeze()
            }
        })
    }
}