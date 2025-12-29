package org.springforge.qualityassurance.toolwindow

import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class QualityResultsPanel : JPanel() {

    private val textArea = JTextArea(20, 60)

    init {
        textArea.isEditable = false
        add(JScrollPane(textArea))
    }

    fun showMessage(msg: String) {
        textArea.text = msg
    }
}
