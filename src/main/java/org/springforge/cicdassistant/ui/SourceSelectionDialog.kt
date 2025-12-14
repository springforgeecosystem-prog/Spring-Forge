package org.springforge.cicdassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * Source type for project analysis
 */
enum class SourceType {
    /** Analyze current IntelliJ project (local files) */
    LOCAL,

    /** Analyze remote GitHub repository */
    GITHUB
}

/**
 * Dialog for selecting the source of project analysis
 *
 * Allows users to choose between:
 * 1. Local Project - Current IntelliJ project
 * 2. GitHub Repository - Remote repository via GitHub MCP Server
 *
 * Usage:
 * ```kotlin
 * val dialog = SourceSelectionDialog(project)
 * if (dialog.showAndGet()) {
 *     when (dialog.selectedSource) {
 *         SourceType.LOCAL -> analyzeLocalProject()
 *         SourceType.GITHUB -> analyzeGitHub(dialog.githubUrl)
 *     }
 * }
 * ```
 */
class SourceSelectionDialog(project: Project) : DialogWrapper(project) {

    private val localRadio = JBRadioButton("Local Project (Current IntelliJ Project)", true)
    private val githubRadio = JBRadioButton("GitHub Repository")
    private val githubUrlField = JBTextField("https://github.com/spring-projects/spring-petclinic", 40)

    /**
     * Selected source type (LOCAL or GITHUB)
     * Only valid after dialog is closed with OK
     */
    var selectedSource: SourceType = SourceType.LOCAL
        private set

    /**
     * GitHub repository URL (only valid if selectedSource == GITHUB)
     * Only valid after dialog is closed with OK
     */
    var githubUrl: String = ""
        private set

    init {
        title = "Select Project Source"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))

        // Radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(localRadio)
        buttonGroup.add(githubRadio)

        // Main panel with radio buttons and descriptions
        val radioPanel = JPanel(GridLayout(0, 1, 5, 5))

        // Local project option
        radioPanel.add(localRadio)
        radioPanel.add(createLocalDescription())
        radioPanel.add(Box.createVerticalStrut(10))

        // GitHub option
        radioPanel.add(githubRadio)
        radioPanel.add(createGitHubInputPanel())

        panel.add(radioPanel, BorderLayout.CENTER)

        // Warning if GitHub not configured
        if (!EnvironmentConfig.GitHub.isConfigured()) {
            val warningPanel = createWarningPanel()
            panel.add(warningPanel, BorderLayout.SOUTH)
        }

        // Enable/disable URL field based on selection
        localRadio.addActionListener {
            githubUrlField.isEnabled = false
        }

        githubRadio.addActionListener {
            githubUrlField.isEnabled = true
            githubUrlField.requestFocus()
        }

        githubUrlField.isEnabled = false

        return panel
    }

    /**
     * Create description for local project option
     */
    private fun createLocalDescription(): JComponent {
        val label = JLabel(
            "<html><small>" +
            "  <b>Benefits:</b><br>" +
            "  • Fast analysis (reads local files)<br>" +
            "  • No authentication required<br>" +
            "  • Works offline<br>" +
            "</small></html>"
        )
        label.border = BorderFactory.createEmptyBorder(0, 30, 0, 0)
        return label
    }

    /**
     * Create input panel for GitHub repository URL
     */
    private fun createGitHubInputPanel(): JComponent {
        val panel = JPanel(BorderLayout(5, 5))
        panel.border = BorderFactory.createEmptyBorder(0, 30, 0, 0)

        // URL input field
        val urlPanel = JPanel(BorderLayout(5, 0))
        urlPanel.add(JLabel("Repository URL:"), BorderLayout.WEST)
        urlPanel.add(githubUrlField, BorderLayout.CENTER)

        // Description
        val descLabel = JLabel(
            "<html><small>" +
            "  <b>Benefits:</b><br>" +
            "  • Analyze remote projects without cloning<br>" +
            "  • Learn from community Spring Boot templates<br>" +
            "  • Generate artifacts for popular repositories<br>" +
            "  <br>" +
            "  <b>Requirements:</b><br>" +
            "  • Docker Desktop must be installed and running<br>" +
            "  • GitHub PAT must be configured in .env file<br>" +
            "</small></html>"
        )

        panel.add(urlPanel, BorderLayout.NORTH)
        panel.add(Box.createVerticalStrut(5), BorderLayout.CENTER)
        panel.add(descLabel, BorderLayout.SOUTH)

        return panel
    }

    /**
     * Create warning panel for missing GitHub configuration
     */
    private fun createWarningPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 0, 0, 0)

        val warningLabel = JLabel(
            "<html><b>⚠ Note:</b> GitHub Personal Access Token not configured in .env file.<br>" +
            "GitHub repository analysis will not work until you configure it.<br>" +
            "See <code>.env.example</code> for setup instructions.</html>"
        )
        warningLabel.foreground = java.awt.Color(255, 140, 0) // Orange color
        warningLabel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(java.awt.Color(255, 140, 0)),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )

        panel.add(warningLabel, BorderLayout.CENTER)
        return panel
    }

    /**
     * Validate input when OK is clicked
     */
    override fun doOKAction() {
        selectedSource = if (localRadio.isSelected) SourceType.LOCAL else SourceType.GITHUB
        githubUrl = githubUrlField.text.trim()

        // Validate GitHub URL if GitHub source is selected
        if (selectedSource == SourceType.GITHUB) {
            if (githubUrl.isBlank()) {
                showError("Please enter a GitHub repository URL")
                return
            }

            if (!isValidGitHubUrl(githubUrl)) {
                showError(
                    "Invalid GitHub URL format.\n\n" +
                    "Expected formats:\n" +
                    "  • https://github.com/owner/repo\n" +
                    "  • github.com/owner/repo\n" +
                    "  • owner/repo\n\n" +
                    "Example: spring-projects/spring-boot"
                )
                return
            }

            if (!EnvironmentConfig.GitHub.isConfigured()) {
                showError(
                    "GitHub Personal Access Token not configured.\n\n" +
                    "To use GitHub repository analysis:\n" +
                    "1. Create a GitHub PAT at: https://github.com/settings/tokens\n" +
                    "2. Add GITHUB_PERSONAL_ACCESS_TOKEN to your .env file\n" +
                    "3. Restart the IDE\n\n" +
                    "See .env.example for details."
                )
                return
            }

            // Check if Docker is available
            if (!isDockerAvailable()) {
                showError(
                    "Docker is not available.\n\n" +
                    "GitHub MCP Server requires Docker Desktop to be installed and running.\n\n" +
                    "Please:\n" +
                    "1. Install Docker Desktop from: https://www.docker.com/products/docker-desktop\n" +
                    "2. Start Docker Desktop\n" +
                    "3. Try again"
                )
                return
            }
        }

        super.doOKAction()
    }

    /**
     * Validate GitHub URL format
     */
    private fun isValidGitHubUrl(url: String): Boolean {
        val patterns = listOf(
            // https://github.com/owner/repo
            Regex("""^https?://(www\.)?github\.com/[\w-]+/[\w.-]+/?$"""),
            // github.com/owner/repo
            Regex("""^(www\.)?github\.com/[\w-]+/[\w.-]+/?$"""),
            // owner/repo
            Regex("""^[\w-]+/[\w.-]+$""")
        )

        return patterns.any { it.matches(url) }
    }

    /**
     * Check if Docker is available
     */
    private fun isDockerAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("docker", "--version")
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Show error message dialog
     */
    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            contentPanel,
            message,
            "Validation Error",
            JOptionPane.ERROR_MESSAGE
        )
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return if (localRadio.isSelected) localRadio else githubUrlField
    }
}
