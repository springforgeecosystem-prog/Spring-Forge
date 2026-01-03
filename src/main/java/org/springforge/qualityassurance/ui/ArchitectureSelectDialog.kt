package org.springforge.qualityassurance.ui

import com.intellij.openapi.ui.DialogWrapper
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ArchitectureSelectDialog : DialogWrapper(true) {

    private lateinit var combo: JComboBox<String>

    init {
        title = "Select Architecture Pattern"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        combo = JComboBox(arrayOf("Layered", "Hexagonal", "Clean", "MVC"))
        panel.add(JLabel("Architecture:"))
        panel.add(combo)
        return panel
    }

    fun getSelectedArchitecture(): String {
        return combo.selectedItem as String
    }
}
