import bitspittle.lively.Lively
import bitspittle.lively.swing.SwingExecutor
import bitspittle.lively.swing.wrapEnabled
import bitspittle.lively.swing.wrapSelected
import bitspittle.lively.swing.wrapText
import java.awt.BorderLayout
import javax.swing.*


fun main() {
    Lively.executorFactory = { SwingExecutor() }
    SwingUtilities.invokeLater {
        SyncFieldsDemo()
    }
}

class SyncFieldsDemo {
    private class Form : SwingForm {
        override val root = JPanel(BorderLayout())

        val appNameField = JTextField()
        val activityNameField = JTextField()
        val syncActivityNameCheckbox = JCheckBox("Sync names? ", true)
        val createActivityCheckbox = JCheckBox("Create activity? ", true)

        init {
            val vGroup = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(appNameField)
                add(activityNameField)
                add(syncActivityNameCheckbox)
                add(createActivityCheckbox)
            }

            root.add(vGroup)
        }
    }

    private val INITIAL_APP_NAME = "MyApp"
    private val ACTIVITY_SUFFIX = "Activity"
    private fun toActivityName(appName: String): String {
        val appName = appName.replace(" ", "")
        for (i in 1..ACTIVITY_SUFFIX.length) {
            if (appName.endsWith(ACTIVITY_SUFFIX.substring(0, i))) {
                return "$appName${ACTIVITY_SUFFIX.substring(i)}"
            }
        }
        return "$appName$ACTIVITY_SUFFIX"
    }

    init {
        val form = Form()

        // Declare UI relationships with lively
        val lively = Lively()

        val uiAppName = lively.wrapText(form.appNameField)
        uiAppName.set(INITIAL_APP_NAME)

        val generateUiActivityName = { isSync: Boolean ->
            if (isSync) {
                lively.wrapText(form.activityNameField) { toActivityName(uiAppName.get()) }
            } else {
                lively.wrapText(form.activityNameField)
            }
        }
        val uiSync = lively.wrapSelected(form.syncActivityNameCheckbox)
        var uiActivityName = generateUiActivityName(uiSync.getSnapshot())
        uiSync.onValueChanged += { isSync ->
            uiActivityName.freeze()
            uiActivityName = generateUiActivityName(isSync)
        }

        val uiCreate = lively.wrapSelected(form.createActivityCheckbox)
        lively.wrapEnabled(form.activityNameField) { uiCreate.get() && !uiSync.get() }

        // Show the demo
        val window = SwingWindow(SyncFieldsDemo::class.java.simpleName)
        window.show(form)
        window.onClosed += {
            lively.freeze()

            println(
                """
                    User chose:

                    App Name: ${uiAppName.getSnapshot()}
                    Activity Name: ${uiActivityName.getSnapshot()}
                    Create Activity? ${if (uiCreate.getSnapshot()) "Yes" else "No"}
                """.trimIndent()
            )
        }
    }
}