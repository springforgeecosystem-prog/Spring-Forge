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
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CreateProjectDialog : DialogWrapper(true) {

    companion object {
        private val JAVA_KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null"
        )

        fun isValidIdentifier(name: String): Boolean =
            name.isNotEmpty() && name[0].isLetter() &&
            name.all { it.isLetterOrDigit() || it == '_' || it == '-' } &&
            name !in JAVA_KEYWORDS
    }

    // Fields
    private val projectNameField = JBTextField("demo")
    private val packageRootField = JBTextField("com.example.demo")

    // True when the package field is being updated programmatically (suppress re-entry)
    private var syncingPackage = false
    // True once the user has manually typed in the package field
    private var packageManuallyEdited = false

    // Dropdowns
    private val archCombo = ComboBox(arrayOf("Layered", "MVC", "Clean"))
    private val javaVersionCombo = ComboBox(arrayOf("17", "21"))
    private val buildToolCombo = ComboBox(arrayOf("Maven", "Gradle - Groovy", "Gradle - Kotlin"))
    private val bootVersionCombo = ComboBox(arrayOf("3.5.10 (Stable)", "4.0.2 (Latest)"))

    // Dependency Selector
    private val depSelector = DependencySelector()

    init {
        title = "Create New Spring Boot Project"

        // Track manual edits to the package field (ignore programmatic updates)
        packageRootField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { if (!syncingPackage) packageManuallyEdited = true }
            override fun removeUpdate(e: DocumentEvent) { if (!syncingPackage) packageManuallyEdited = true }
            override fun changedUpdate(e: DocumentEvent) { if (!syncingPackage) packageManuallyEdited = true }
        })

        // Auto-sync last package segment with project name
        projectNameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = syncPackage()
            override fun removeUpdate(e: DocumentEvent) = syncPackage()
            override fun changedUpdate(e: DocumentEvent) = syncPackage()

            private fun syncPackage() {
                if (packageManuallyEdited || syncingPackage) return
                val name = projectNameField.text.trim().lowercase()
                    .replace("[^a-z0-9]".toRegex(), "")
                val base = packageRootField.text.substringBeforeLast(".", "com.example")
                syncingPackage = true
                try {
                    packageRootField.text = if (name.isEmpty()) base else "$base.$name"
                } finally {
                    syncingPackage = false
                }
            }
        })

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

        // Row 3: Spring Boot Version
        c.gridx = 0; c.gridy = 4; c.weightx = 0.3
        formPanel.add(JLabel("Boot Version:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(bootVersionCombo, c)

        // Row 4: Java Version
        c.gridx = 0; c.gridy = 5; c.weightx = 0.3
        formPanel.add(JLabel("Java Version:"), c); c.gridx = 1; c.weightx = 0.7
        formPanel.add(javaVersionCombo, c)

        depSelector.border = BorderFactory.createTitledBorder("Dependencies")

        mainPanel.add(formPanel, BorderLayout.NORTH)
        mainPanel.add(depSelector, BorderLayout.CENTER)

        return mainPanel
    }

    override fun doValidate(): ValidationInfo? {
        val name = projectNameField.text.trim()
        val pkg = packageRootField.text.trim()

        if (name.isBlank()) return ValidationInfo("Project name is required", projectNameField)
        if (name in JAVA_KEYWORDS)
            return ValidationInfo("'$name' is a Java reserved keyword and cannot be used as a project name", projectNameField)

        if (pkg.isBlank()) return ValidationInfo("Package is required", packageRootField)
        val badSegment = pkg.split(".").firstOrNull { it in JAVA_KEYWORDS }
        if (badSegment != null)
            return ValidationInfo("'$badSegment' is a Java reserved keyword and cannot be used in a package name", packageRootField)

        return null
    }

    fun getArtifactId() = projectNameField.text.trim()
    fun getPackageRoot() = packageRootField.text.trim()
    fun getJavaVersion() = javaVersionCombo.selectedItem.toString()

    fun getBootVersion(): String {
        val selected = bootVersionCombo.selectedItem.toString()
        // Extract version number from display string like "3.5.10 (Stable)"
        return selected.substringBefore(" ").trim()
    }

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