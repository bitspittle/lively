package bitspittle.lively.swing

import bitspittle.lively.exec.Executor
import javax.swing.SwingUtilities

/**
 * A useful executor for when you expect [Lively] to interact with Swing UI components.
 */
class SwingExecutor : Executor {
    override fun submit(runnable: () -> Unit) {
        SwingUtilities.invokeLater(runnable)
    }
}