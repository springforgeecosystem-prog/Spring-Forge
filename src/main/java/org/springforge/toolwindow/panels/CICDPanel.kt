package org.springforge.toolwindow.panels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.runBlocking
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.github.GitHubMCPClient
import org.springforge.cicdassistant.github.mcp.GitHubMCPServerConnector
import org.springforge.cicdassistant.mcp.models.MCPContext
import org.springforge.cicdassistant.services.ClaudeService
import org.springforge.cicdassistant.services.ProjectAnalyzerService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import javax.swing.*

/**
 * CI/CD Panel for SpringForge Tool Window
 * Allows users to generate CI/CD artifacts from the sidebar
 */
class CICDPanel(private val project: Project) : JPanel() {

    // Source selection
    private val localRadio = JBRadioButton("Local Project", true)
    private val githubRadio = JBRadioButton("GitHub Repository")
    private val githubUrlField = JBTextField("https://github.com/spring-projects/spring-petclinic", 30)
    private val githubBranchField = JBTextField("main", 15)

    // Generation options
    private val dockerfileCheckbox = JBCheckBox("Dockerfile", true)
    private val githubActionsCheckbox = JBCheckBox("GitHub Actions Workflow", true)
    private val dockerComposeCheckbox = JBCheckBox("Docker Compose", true)
    private val kubernetesCheckbox = JBCheckBox("Kubernetes Manifests", false)

    // Actions
    private val generateButton = JButton("Generate CI/CD Files")
    private val clearButton = JButton("Clear Output")

    // Results area
    private val resultsArea = JBTextArea()

    init {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        setupUI()
    }

    private fun setupUI() {
        // Main content panel
        val contentPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)

        // Title
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 2
        val titleLabel = JBLabel("CI/CD Pipeline Generator")
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)
        contentPanel.add(titleLabel, gbc)

        // Source selection section
        gbc.gridy++
        gbc.gridwidth = 2
        contentPanel.add(createSeparator("Source"), gbc)

        gbc.gridy++
        gbc.gridwidth = 1
        contentPanel.add(localRadio, gbc)

        gbc.gridy++
        contentPanel.add(githubRadio, gbc)

        // GitHub URL
        gbc.gridx = 0
        gbc.gridy++
        contentPanel.add(JBLabel("Repository URL:"), gbc)

        gbc.gridx = 1
        contentPanel.add(githubUrlField, gbc)

        // GitHub Branch
        gbc.gridx = 0
        gbc.gridy++
        contentPanel.add(JBLabel("Branch:"), gbc)

        gbc.gridx = 1
        contentPanel.add(githubBranchField, gbc)

        // Generation options section
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        contentPanel.add(createSeparator("Generate"), gbc)

        gbc.gridy++
        gbc.gridwidth = 1
        contentPanel.add(dockerfileCheckbox, gbc)

        gbc.gridy++
        contentPanel.add(githubActionsCheckbox, gbc)

        gbc.gridy++
        contentPanel.add(dockerComposeCheckbox, gbc)

        gbc.gridy++
        contentPanel.add(kubernetesCheckbox, gbc)

        // Buttons
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        gbc.insets = Insets(15, 5, 5, 5)
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(generateButton)
        buttonPanel.add(Box.createHorizontalStrut(10))
        buttonPanel.add(clearButton)
        contentPanel.add(buttonPanel, gbc)

        // Wrap content in scroll pane
        val scrollPane = JBScrollPane(contentPanel)
        scrollPane.preferredSize = Dimension(400, 450)

        // Results section
        resultsArea.isEditable = false
        resultsArea.lineWrap = true
        resultsArea.wrapStyleWord = true
        resultsArea.text = "Ready to generate CI/CD files.\n\nSelect source and options, then click Generate."
        val resultsScrollPane = JBScrollPane(resultsArea)
        resultsScrollPane.preferredSize = Dimension(400, 200)

        // Add to main panel
        add(scrollPane, BorderLayout.CENTER)
        add(resultsScrollPane, BorderLayout.SOUTH)

        // Setup listeners
        setupListeners()
    }

    private fun createSeparator(title: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 0, 5, 0)

        val label = JBLabel(title)
        label.font = label.font.deriveFont(java.awt.Font.BOLD)
        panel.add(label, BorderLayout.WEST)

        val separator = JSeparator()
        panel.add(separator, BorderLayout.SOUTH)

        return panel
    }

    private fun setupListeners() {
        // Radio button group
        val buttonGroup = ButtonGroup()
        buttonGroup.add(localRadio)
        buttonGroup.add(githubRadio)

        // Enable/disable GitHub fields
        localRadio.addActionListener {
            githubUrlField.isEnabled = false
            githubBranchField.isEnabled = false
        }

        githubRadio.addActionListener {
            githubUrlField.isEnabled = true
            githubBranchField.isEnabled = true
        }

        githubUrlField.isEnabled = false
        githubBranchField.isEnabled = false

        // Generate button
        generateButton.addActionListener {
            generateCICDFiles()
        }

        // Clear button
        clearButton.addActionListener {
            resultsArea.text = "Output cleared.\n\nReady to generate CI/CD files."
        }
    }

    private fun generateCICDFiles() {
        // Validate selection
        if (!dockerfileCheckbox.isSelected &&
            !githubActionsCheckbox.isSelected &&
            !dockerComposeCheckbox.isSelected &&
            !kubernetesCheckbox.isSelected) {
            Messages.showWarningDialog(
                project,
                "Please select at least one file type to generate.",
                "No Selection"
            )
            return
        }

        val isLocal = localRadio.isSelected
        val githubUrl = githubUrlField.text.trim()
        val githubBranch = githubBranchField.text.trim()

        resultsArea.text = "Starting generation...\n"

        // Run in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating CI/CD Files",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Get MCP Context
                    val mcpContext = if (isLocal) {
                        indicator.text = "Analyzing local project..."
                        indicator.fraction = 0.1
                        appendResults("📁 Analyzing local project...\n")

                        val analyzerService = ProjectAnalyzerService()
                        analyzerService.analyzeProjectWithMCP(project)
                    } else {
                        indicator.text = "Connecting to GitHub..."
                        indicator.fraction = 0.1
                        appendResults("🔗 Connecting to GitHub: $githubUrl (branch: $githubBranch)...\n")

                        val connector = GitHubMCPServerConnector()
                        val githubClient = GitHubMCPClient(connector)

                        runBlocking {
                            githubClient.analyzeGitHubRepository(githubUrl, githubBranch)
                        }
                    }

                    val bedrockClient = BedrockClient()
                    val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(mcpContext)

                    var progress = 0.3
                    val increment = 0.6 / countSelectedOptions()

                    // Generate selected files
                    if (dockerfileCheckbox.isSelected) {
                        indicator.text = "Generating Dockerfile..."
                        indicator.fraction = progress
                        appendResults("🐳 Generating Dockerfile...\n")

                        val dockerfile = bedrockClient.generateDockerfile(mcpContextJson, usePrefill = true)
                        saveToFile("Dockerfile", dockerfile)
                        appendResults("✅ Dockerfile generated successfully!\n\n")
                        progress += increment
                    }

                    if (githubActionsCheckbox.isSelected) {
                        indicator.text = "Generating GitHub Actions workflow..."
                        indicator.fraction = progress
                        appendResults("⚙️ Generating GitHub Actions workflow...\n")

                        val workflow = bedrockClient.generateGitHubActionsWorkflow(mcpContextJson)
                        val basePath = project.basePath ?: throw IllegalStateException("Project path not found")
                        val workflowDir = File(basePath, ".github/workflows")
                        workflowDir.mkdirs()
                        File(workflowDir, "build.yml").writeText(workflow)
                        appendResults("✅ GitHub Actions workflow generated at .github/workflows/build.yml\n\n")
                        progress += increment
                    }

                    if (dockerComposeCheckbox.isSelected) {
                        indicator.text = "Generating docker-compose.yml..."
                        indicator.fraction = progress
                        appendResults("🐋 Generating docker-compose.yml...\n")

                        val compose = bedrockClient.generateDockerCompose(mcpContextJson)
                        saveToFile("docker-compose.yml", compose)
                        appendResults("✅ docker-compose.yml generated successfully!\n\n")
                        progress += increment
                    }

                    if (kubernetesCheckbox.isSelected) {
                        indicator.text = "Generating Kubernetes manifests..."
                        indicator.fraction = progress
                        appendResults("☸️ Generating Kubernetes manifests...\n")

                        val analyzerService = ProjectAnalyzerService()
                        val projectInfo = analyzerService.analyzeProject(project)
                        val projectInfoStr = analyzerService.formatProjectInfoForPrompt(projectInfo)

                        val claudeService = ClaudeService()
                        val manifests = claudeService.generateKubernetesManifests(projectInfoStr)
                        claudeService.close()

                        val basePath = project.basePath ?: throw IllegalStateException("Project path not found")
                        val k8sDir = File(basePath, "k8s")
                        k8sDir.mkdirs()
                        File(k8sDir, "deployment.yml").writeText(manifests)
                        appendResults("✅ Kubernetes manifests generated at k8s/deployment.yml\n\n")
                        progress += increment
                    }

                    bedrockClient.close()

                    indicator.fraction = 1.0
                    appendResults("━".repeat(50) + "\n")
                    appendResults("🎉 All selected CI/CD files generated successfully!\n")
                    appendResults("Check your project root directory.\n")

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "CI/CD files generated successfully!\n\nCheck the output panel for details.",
                            "SpringForge - Success"
                        )
                    }

                } catch (ex: Exception) {
                    appendResults("\n❌ Error: ${ex.message}\n")
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to generate files:\n${ex.message}",
                            "SpringForge - Error"
                        )
                    }
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun countSelectedOptions(): Int {
        var count = 0
        if (dockerfileCheckbox.isSelected) count++
        if (githubActionsCheckbox.isSelected) count++
        if (dockerComposeCheckbox.isSelected) count++
        if (kubernetesCheckbox.isSelected) count++
        return count.coerceAtLeast(1)
    }

    private fun saveToFile(filename: String, content: String) {
        val basePath = project.basePath ?: return
        val file = File(basePath, filename)
        file.writeText(content)
    }

    private fun appendResults(text: String) {
        SwingUtilities.invokeLater {
            resultsArea.append(text)
            resultsArea.caretPosition = resultsArea.document.length
        }
    }
}
