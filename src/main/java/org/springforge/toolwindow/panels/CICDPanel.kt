package org.springforge.toolwindow.panels

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

    // JSON mapper for parsing MCP responses
    private val mapper = jacksonObjectMapper()

    // Source selection
    private val localRadio = JBRadioButton("Local Project", true)
    private val githubRadio = JBRadioButton("GitHub Repository")
    private val githubUrlField = JBTextField("https://github.com/spring-projects/spring-petclinic", 30)
    private val githubBranchComboBox = JComboBox<String>(arrayOf("main"))
    private val fetchBranchesButton = JButton("Fetch Branches")
    private var availableBranches = listOf<String>()

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

        // Fetch Branches Button
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 2
        val fetchPanel = JPanel()
        fetchPanel.layout = BoxLayout(fetchPanel, BoxLayout.X_AXIS)
        fetchBranchesButton.isEnabled = false
        fetchPanel.add(fetchBranchesButton)
        fetchPanel.add(Box.createHorizontalGlue())
        contentPanel.add(fetchPanel, gbc)

        // GitHub Branch Dropdown
        gbc.gridx = 0
        gbc.gridy++
        gbc.gridwidth = 1
        contentPanel.add(JBLabel("Branch:"), gbc)

        gbc.gridx = 1
        githubBranchComboBox.isEditable = false
        githubBranchComboBox.maximumRowCount = 10 // Show 10 branches at once
        githubBranchComboBox.isEnabled = false
        contentPanel.add(githubBranchComboBox, gbc)

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
            fetchBranchesButton.isEnabled = false
            githubBranchComboBox.isEnabled = false
        }

        githubRadio.addActionListener {
            githubUrlField.isEnabled = true
            fetchBranchesButton.isEnabled = true
            githubBranchComboBox.isEnabled = true
        }

        githubUrlField.isEnabled = false
        fetchBranchesButton.isEnabled = false
        githubBranchComboBox.isEnabled = false

        // Fetch branches button
        fetchBranchesButton.addActionListener {
            fetchBranchesFromGitHub()
        }

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
        val githubBranch = githubBranchComboBox.selectedItem as? String ?: "main"

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

    private fun fetchBranchesFromGitHub() {
        val githubUrl = githubUrlField.text.trim()

        if (githubUrl.isEmpty() || !githubUrl.contains("github.com")) {
            Messages.showWarningDialog(
                project,
                "Please enter a valid GitHub repository URL.",
                "Invalid URL"
            )
            return
        }

        appendResults("🔍 Fetching branches from $githubUrl...\n")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Fetching Branches",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Connecting to GitHub MCP Server..."
                    indicator.fraction = 0.2

                    val connector = GitHubMCPServerConnector()
                    
                    // Parse owner and repo from URL
                    val cleaned = githubUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                        .removePrefix("github.com/")
                        .removeSuffix(".git")
                        .removeSuffix("/")
                    
                    val parts = cleaned.split("/")
                    if (parts.size < 2) {
                        throw IllegalArgumentException("Invalid GitHub URL format. Expected: owner/repo")
                    }

                    val owner = parts[0]
                    val repo = parts[1]

                    indicator.text = "Fetching branches from $owner/$repo..."
                    indicator.fraction = 0.5

                    // Connect to GitHub MCP Server
                    runBlocking {
                        connector.connect()
                    }

                    // Call GitHub MCP Server to list branches
                    val result = runBlocking {
                        connector.callTool(
                            toolName = "list_branches",
                            arguments = mapOf(
                                "owner" to owner,
                                "repo" to repo
                            )
                        )
                    }

                    // Parse MCP response format: {"content": [{"type":"text","text":"[{...}]"}]}
                    val content = result["content"] as? List<*>
                    val textItem = content?.firstOrNull() as? Map<*, *>
                    val jsonText = textItem?.get("text") as? String
                        ?: throw IllegalStateException("Invalid MCP response format")

                    // Parse JSON array of branches
                    val branches = mapper.readValue<List<Map<String, Any>>>(jsonText)
                    val branchNames = branches.mapNotNull { it["name"] as? String }

                    if (branchNames.isEmpty()) {
                        throw IllegalStateException("No branches found in repository")
                    }

                    availableBranches = branchNames
                    indicator.fraction = 1.0

                    // Update UI on EDT
                    ApplicationManager.getApplication().invokeLater {
                        githubBranchComboBox.removeAllItems()
                        branchNames.forEach { branch ->
                            githubBranchComboBox.addItem(branch)
                        }

                        // Select "main" or "master" if available
                        val defaultBranch = branchNames.find { it == "main" || it == "master" }
                        if (defaultBranch != null) {
                            githubBranchComboBox.selectedItem = defaultBranch
                        }

                        appendResults("✅ Found ${branchNames.size} branches: ${branchNames.joinToString(", ")}\n")
                        
                        Messages.showInfoMessage(
                            project,
                            "Found ${branchNames.size} branches:\n${branchNames.take(10).joinToString(", ")}${if (branchNames.size > 10) "..." else ""}",
                            "Branches Fetched"
                        )
                    }

                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        appendResults("❌ Failed to fetch branches: ${ex.message}\n")
                        Messages.showErrorDialog(
                            project,
                            "Failed to fetch branches:\n${ex.message}",
                            "Fetch Error"
                        )
                    }
                    ex.printStackTrace()
                }
            }
        })
    }
}

