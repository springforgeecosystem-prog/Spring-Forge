package org.springforge.qualityassurance.toolwindow

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import javax.swing.JPanel

class QualityToolWindowPanel : JPanel() {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        layout = BorderLayout()
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    fun showMessage(message: String) {
        textArea.text = message
    }
}
