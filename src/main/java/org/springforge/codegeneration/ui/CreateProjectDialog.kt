package org.springforge.codegeneration.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import org.springforge.codegeneration.service.ArchitectureTemplate
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CreateProjectDialog : DialogWrapper(true) {

    // Fields
    private val projectNameField = JBTextField("demo")
    private val packageRootField = JBTextField("com.example.demo")

    // Dropdowns
    private val archCombo = ComboBox(arrayOf("Layered", "MVC", "Clean"))
    private val javaVersionCombo = ComboBox(arrayOf("17", "21"))
    private val buildToolCombo = ComboBox(arrayOf("Maven", "Gradle - Groovy", "Gradle - Kotlin"))

    // REMOVED: private val bootVersionCombo...

    // Dependency Selector
    private val depSelector = DependencySelector()

    init {
        title = "Create New Spring Boot Project"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        val formPanel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = Insets(5, 5, 5, 5)
        c.weightx = 0.3
        c.gridx = 0; c.gridy = 0

        // Row 1: Name & Package
        formPanel.add(JLabel("Name:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(projectNameField, c)

        c.gridx = 0; c.gridy = 1; c.weightx = 0.3
        formPanel.add(JLabel("Package:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(packageRootField, c)

        // Row 2: Architecture & Build Tool
        c.gridx = 0; c.gridy = 2; c.weightx = 0.3
        formPanel.add(JLabel("Architecture:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(archCombo, c)

        c.gridx = 0; c.gridy = 3; c.weightx = 0.3
        formPanel.add(JLabel("Build Tool:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(buildToolCombo, c)

        // Row 3: Java Version (Boot version removed)
        c.gridx = 0; c.gridy = 4; c.weightx = 0.3
        formPanel.add(JLabel("Java Version:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(javaVersionCombo, c)

        depSelector.border = BorderFactory.createTitledBorder("Dependencies")

        mainPanel.add(formPanel, BorderLayout.NORTH)
        mainPanel.add(depSelector, BorderLayout.CENTER)

        return mainPanel
    }

    override fun doValidate(): ValidationInfo? {
        if (projectNameField.text.isBlank()) return ValidationInfo("Project name is required", projectNameField)
        if (packageRootField.text.isBlank()) return ValidationInfo("Package is required", packageRootField)
        return null
    }

    fun getArtifactId() = projectNameField.text.trim()
    fun getPackageRoot() = packageRootField.text.trim()
    fun getJavaVersion() = javaVersionCombo.selectedItem.toString()

    // REMOVED: fun getBootVersion()

    fun getBuildTool(): String {
        return when (buildToolCombo.selectedItem.toString()) {
            "Gradle - Groovy" -> "gradle-project"
            "Gradle - Kotlin" -> "gradle-project-kotlin"
            else -> "maven-project"
        }
    }

    fun getArchitecture(): ArchitectureTemplate =
        when (archCombo.selectedItem?.toString()?.lowercase()) {
            "mvc" -> ArchitectureTemplate.MVC
            "clean" -> ArchitectureTemplate.CLEAN
            else -> ArchitectureTemplate.LAYERED
        }

    fun getDependencies(): List<String> = depSelector.getSelectedDependencies()
}