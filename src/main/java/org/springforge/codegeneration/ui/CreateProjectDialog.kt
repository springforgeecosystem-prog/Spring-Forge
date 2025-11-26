package org.springforge.codegeneration.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import org.springforge.codegeneration.service.ArchitectureTemplate
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CreateProjectDialog : DialogWrapper(true) {

    private val projectNameField = JBTextField("demo")
    private val packageRootField = JBTextField("com.example.demo")
    private val archCombo = ComboBox(arrayOf("Layered", "MVC", "Clean"))
    private val addJpa = JCheckBox("Add JPA", true)
    private val addLombok = JCheckBox("Add Lombok", true)
    private val addWeb = JCheckBox("Add Web", true)

    init {
        title = "Create New Spring Boot Project (SpringForge)"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val form = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.gridx = 0
        c.gridy = 0
        c.weightx = 0.2
        form.add(JLabel("Project Artifact Id:"), c)
        c.gridx = 1
        c.weightx = 0.8
        form.add(projectNameField, c)

        c.gridx = 0
        c.gridy = 1
        c.weightx = 0.2
        form.add(JLabel("Package Root:"), c)
        c.gridx = 1
        c.weightx = 0.8
        form.add(packageRootField, c)

        c.gridx = 0
        c.gridy = 2
        c.weightx = 0.2
        form.add(JLabel("Architecture:"), c)
        c.gridx = 1
        c.weightx = 0.8
        form.add(archCombo, c)

        c.gridx = 0
        c.gridy = 3
        c.weightx = 0.2
        form.add(JLabel("Dependencies:"), c)
        c.gridx = 1
        val depsPanel = JPanel()
        depsPanel.add(addWeb)
        depsPanel.add(addJpa)
        depsPanel.add(addLombok)
        form.add(depsPanel, c)

        panel.add(form, BorderLayout.NORTH)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val pkg = packageRootField.text.trim()
        if (pkg.isEmpty()) return ValidationInfo("Package root is required", packageRootField)
        if (projectNameField.text.trim().isEmpty()) return ValidationInfo("Project name is required", projectNameField)
        return null
    }

    fun getArtifactId(): String = projectNameField.text.trim()
    fun getPackageRoot(): String = packageRootField.text.trim()
    fun getArchitecture(): ArchitectureTemplate =
        when (archCombo.selectedItem?.toString()?.lowercase()) {
            "mvc" -> ArchitectureTemplate.MVC
            "clean" -> ArchitectureTemplate.CLEAN
            else -> ArchitectureTemplate.LAYERED
        }

    fun getDependencies(): List<String> {
        val deps = mutableListOf<String>()
        if (addWeb.isSelected) deps.add("web")
        if (addJpa.isSelected) deps.add("data-jpa")
        if (addLombok.isSelected) deps.add("lombok")
        return deps
    }
}