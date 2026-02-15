package org.springforge.runtimeanalysis.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

class AnalysisResultDialog(
        project: Project,
        private val content: String
) : DialogWrapper(project) {

    init {
        title = "SpringForge Analysis Result"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val textArea = JTextArea(content)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.font = Font("JetBrains Mono", Font.PLAIN, 13)

        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = Dimension(800, 500)

        return scrollPane
    }
}
