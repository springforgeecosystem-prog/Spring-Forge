package org.springforge.toolwindow.panels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import org.springforge.cicdassistant.audit.AuditService
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.github.GitHubApiClient
import org.springforge.cicdassistant.github.GitHubMCPClient
import org.springforge.cicdassistant.services.ProjectAnalyzerService
import org.springforge.cicdassistant.explainability.ExplainabilityResult
import org.springforge.cicdassistant.explainability.ExplainabilityService
import org.springforge.cicdassistant.explainability.ui.ExplainabilityPanel
import org.springforge.cicdassistant.validation.ValidationResult
import org.springforge.cicdassistant.validation.ValidationService
import org.springforge.cicdassistant.validation.ui.ValidationResultsPanel
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

/**
 * CI/CD Panel — card layout with styled artifact rows, color-coded log output,
 * and integrated validation tab.
 */
class CICDPanel(private val project: Project) : JPanel() {

    // ── Source state ──────────────────────────────────────────────────────────
    private var isLocalSelected = true
    private lateinit var localCard: JPanel
    private lateinit var githubCard: JPanel
    private lateinit var githubConfigPanel: JPanel

    private val githubUrlField = JBTextField("https://github.com/spring-projects/spring-petclinic")
    private val githubBranchComboBox = JComboBox<String>(arrayOf("main"))
    private val fetchBranchesButton = JButton("Fetch Branches")
    private var availableBranches = listOf<String>()

    // ── Artifact checkboxes ───────────────────────────────────────────────────
    private val dockerfileCheck = JCheckBox("", true)
    private val actionsCheck    = JCheckBox("", true)
    private val composeCheck    = JCheckBox("", true)

    // ── Action buttons ────────────────────────────────────────────────────────
    private val generateButton         = JButton("Generate CI/CD Files")
    private val validateButton         = JButton("Validate Generated")
    private val validateExistingButton = JButton("Validate Existing")
    private val explainButton          = JButton("Explain Generated")
    private val explainFileButton      = JButton("Explain File...")
    private val clearButton            = JButton("Clear")

    // ── Output pane ───────────────────────────────────────────────────────────
    private val outputPane = JTextPane()

    // ── Generated content store ───────────────────────────────────────────────
    private var generatedDockerfile:    String? = null
    private var generatedDockerCompose: String? = null
    private var generatedGitHubWorkflow: String? = null
    private var dockerfilePath:     String? = null
    private var dockerComposePath:  String? = null
    private var githubWorkflowPath: String? = null

    init {
        layout = BorderLayout()
        background = UIUtil.getPanelBackground()
        setupOutputStyles()
        setupUI()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Output styles
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupOutputStyles() {
        outputPane.isEditable = false
        outputPane.background = UIUtil.getPanelBackground()
        outputPane.border = JBUI.Borders.empty(8)

        val doc = outputPane.styledDocument
        val defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)

        fun addStyle(name: String, fg: Color, bold: Boolean = false) {
            val s = doc.addStyle(name, defaultStyle)
            StyleConstants.setFontFamily(s, "Monospaced")
            StyleConstants.setFontSize(s, 11)
            StyleConstants.setForeground(s, fg)
            StyleConstants.setBold(s, bold)
        }

        addStyle("normal",  JBColor(Color(0x383A42), Color(0xABB2BF)))
        addStyle("success", JBColor(Color(0x28A745), Color(0x4CAF50)), bold = true)
        addStyle("error",   JBColor(Color(0xD73A49), Color(0xEF5350)))
        addStyle("warning", JBColor(Color(0xE36209), Color(0xFFA726)))
        addStyle("info",    JBColor(Color(0x005CC5), Color(0x64B5F6)))
        addStyle("dim",     JBColor(Color(0x999999), Color(0x606060)))
        addStyle("header",  JBColor(Color(0x6F42C1), Color(0xCE93D8)), bold = true)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI construction
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupUI() {
        // ── Top controls (scrollable) ─────────────────────────────────────────
        val controlsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(12, 12, 8, 12)
        }

        controlsPanel.add(buildHeaderPanel())
        controlsPanel.add(Box.createVerticalStrut(14))
        controlsPanel.add(buildSection("SOURCE", buildSourceContent()))
        controlsPanel.add(Box.createVerticalStrut(10))
        controlsPanel.add(buildSection("GENERATE ARTIFACTS", buildArtifactsContent()))
        controlsPanel.add(Box.createVerticalStrut(10))
        controlsPanel.add(buildActionsPanel())
        controlsPanel.add(Box.createVerticalStrut(8))

        val controlsScroll = JBScrollPane(controlsPanel).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // ── Bottom results ────────────────────────────────────────────────────
        val resultsPanel = buildResultsPanel()

        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, controlsScroll, resultsPanel).apply {
            resizeWeight = 0.48
            border = JBUI.Borders.empty()
        }
        add(split, BorderLayout.CENTER)

        setupListeners()
        appendLog("Ready. Select source and artifacts, then click Generate.\n", "dim")
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, 28)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val title = JBLabel("CI/CD Pipeline Generator").apply {
            font = JBUI.Fonts.label(13f).asBold()
        }

        val badge = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        badge.add(JBLabel("●").apply {
            foreground = JBColor(Color(0x28A745), Color(0x4CAF50))
            font = font.deriveFont(10f)
        })
        badge.add(JBLabel("Ready").apply {
            font = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
        })

        panel.add(title, BorderLayout.WEST)
        panel.add(badge, BorderLayout.EAST)
        return panel
    }

    // ── Section wrapper ───────────────────────────────────────────────────────

    private fun buildSection(title: String, content: JPanel): JPanel {
        val section = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val labelRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, 18)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        labelRow.add(JBLabel(title).apply {
            font = JBUI.Fonts.label(10f).asBold()
            foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
        }, BorderLayout.WEST)

        section.add(labelRow)
        section.add(Box.createVerticalStrut(6))
        section.add(content)
        return section
    }

    // ── Source section ────────────────────────────────────────────────────────

    private fun buildSourceContent(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val cardRow = JPanel(GridLayout(1, 2, 8, 0)).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, 58)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        localCard  = buildSourceCard("📁", "Local Project",      selected = true)
        githubCard = buildSourceCard("🔗", "GitHub Repository",  selected = false)
        cardRow.add(localCard)
        cardRow.add(githubCard)

        githubConfigPanel = buildGithubConfig().apply {
            isVisible = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        panel.add(cardRow)
        panel.add(Box.createVerticalStrut(8))
        panel.add(githubConfigPanel)
        return panel
    }

    private fun buildSourceCard(icon: String, label: String, selected: Boolean): JPanel {
        val card = JPanel(BorderLayout(8, 0)).apply {
            border    = cardBorder(selected)
            background = cardBg(selected)
            cursor    = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        card.add(JBLabel(icon).apply { font = font.deriveFont(15f) }, BorderLayout.WEST)
        card.add(JBLabel(label).apply {
            font       = if (selected) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(12f)
            foreground = if (selected) JBColor(Color(0x2563EB), Color(0x60A5FA)) else UIUtil.getLabelForeground()
        }, BorderLayout.CENTER)
        return card
    }

    private fun cardBorder(selected: Boolean) = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(
            if (selected) JBColor(Color(0x4A90D9), Color(0x4A90D9)) else JBColor.border(),
            if (selected) 2 else 1, true
        ),
        JBUI.Borders.empty(if (selected) 8 else 9, if (selected) 12 else 13)
    )

    private fun cardBg(selected: Boolean) =
        if (selected) JBColor(Color(0xEFF6FF), Color(0x1E3A5F)) else UIUtil.getPanelBackground()

    private fun buildGithubConfig(): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, 72)
        }
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(3, 0, 3, 6)
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JBLabel("URL:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        githubUrlField.font = JBUI.Fonts.label(11f)
        panel.add(githubUrlField, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JBLabel("Branch:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        val branchRow = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
        fetchBranchesButton.font = JBUI.Fonts.label(11f)
        githubBranchComboBox.maximumRowCount = 10
        branchRow.add(githubBranchComboBox, BorderLayout.CENTER)
        branchRow.add(fetchBranchesButton, BorderLayout.EAST)
        panel.add(branchRow, gbc)
        return panel
    }

    // ── Artifacts section ─────────────────────────────────────────────────────

    private fun buildArtifactsContent(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(4, 10)
            )
        }

        panel.add(buildArtifactRow("🐳", "Dockerfile",
            "Multi-stage build · non-root user · health check", dockerfileCheck))
        panel.add(buildDivider())
        panel.add(buildArtifactRow("⚙️", "GitHub Actions",
            "CI workflow for Maven / Gradle projects", actionsCheck))
        panel.add(buildDivider())
        panel.add(buildArtifactRow("🐋", "Docker Compose",
            "Multi-service orchestration config", composeCheck))
        return panel
    }

    private fun buildArtifactRow(icon: String, name: String, desc: String, check: JCheckBox): JPanel {
        val row = JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, 48)
            border = JBUI.Borders.empty(6, 0)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val iconLabel = JBLabel(icon).apply {
            font = font.deriveFont(16f)
            preferredSize = Dimension(22, 22)
        }

        val textCol = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        textCol.add(JBLabel(name).apply { font = JBUI.Fonts.label(12f).asBold() })
        textCol.add(JBLabel(desc).apply {
            font = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
        })

        check.isOpaque = false
        row.add(iconLabel, BorderLayout.WEST)
        row.add(textCol,   BorderLayout.CENTER)
        row.add(check,     BorderLayout.EAST)

        // clicking anywhere on the row toggles the checkbox
        row.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { check.isSelected = !check.isSelected }
        })
        return row
    }

    private fun buildDivider() = JSeparator().apply {
        maximumSize = Dimension(Int.MAX_VALUE, 1)
        foreground  = JBColor(Color(0xEEEEEE), Color(0x3A3F4B))
    }

    // ── Actions panel ─────────────────────────────────────────────────────────

    private fun buildActionsPanel(): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        // Primary full-width button
        generateButton.apply {
            font             = JBUI.Fonts.label(13f).asBold()
            background       = JBColor(Color(0x2563EB), Color(0x1D4ED8))
            foreground       = Color.WHITE
            isFocusPainted   = false
            isContentAreaFilled = true
            border           = JBUI.Borders.empty(9, 16)
            cursor           = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            maximumSize      = Dimension(Int.MAX_VALUE, 38)
            alignmentX       = Component.LEFT_ALIGNMENT
        }

        // Secondary buttons row
        val secondRow = JPanel(GridLayout(1, 3, 6, 0)).apply {
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        for (btn in listOf(validateButton, validateExistingButton, clearButton)) {
            btn.font           = JBUI.Fonts.label(11f)
            btn.isFocusPainted = false
            btn.border         = JBUI.Borders.empty(4, 6)
            btn.cursor         = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        validateButton.isEnabled = false
        secondRow.add(validateButton)
        secondRow.add(validateExistingButton)
        secondRow.add(clearButton)

        // Explain row: [Explain Generated] [Explain File...]
        val explainRow = JPanel(GridLayout(1, 2, 6, 0)).apply {
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
        }
        for (btn in listOf(explainButton, explainFileButton)) {
            btn.font           = JBUI.Fonts.label(11f)
            btn.isFocusPainted = false
            btn.border         = JBUI.Borders.empty(5, 10)
            btn.cursor         = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        explainButton.isEnabled     = false   // enabled only after generation
        explainFileButton.isEnabled = true    // always enabled

        explainRow.add(explainButton)
        explainRow.add(explainFileButton)

        panel.add(generateButton)
        panel.add(Box.createVerticalStrut(8))
        panel.add(secondRow)
        panel.add(Box.createVerticalStrut(6))
        panel.add(explainRow)
        return panel
    }

    // ── Results panel (output log only) ──────────────────────────────────────

    private fun buildResultsPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
        }

        // Output label header
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(4, 10)
            )
            add(JBLabel("OUTPUT").apply {
                font = JBUI.Fonts.label(10f).asBold()
                foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
            }, BorderLayout.WEST)
        }

        val outputScroll = JBScrollPane(outputPane).apply { border = JBUI.Borders.empty() }

        panel.add(header,       BorderLayout.NORTH)
        panel.add(outputScroll, BorderLayout.CENTER)
        return panel
    }

    // ── Validation results dialog ─────────────────────────────────────────────

    private fun showValidationDialog(result: ValidationResult) {
        val owner = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(owner, "Validation Results — SpringForge", Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        val panel = ValidationResultsPanel(project)
        panel.displayResults(result)

        dialog.contentPane = panel
        dialog.preferredSize = Dimension(820, 560)
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Listeners
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupListeners() {
        localCard.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) = selectSource(local = true)
        })
        githubCard.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) = selectSource(local = false)
        })
        fetchBranchesButton.addActionListener    { fetchBranchesFromGitHub() }
        generateButton.addActionListener         { generateCICDFiles() }
        validateButton.addActionListener         { validateGeneratedFiles() }
        validateExistingButton.addActionListener { validateExistingFilesInProject() }
        explainButton.addActionListener          { explainGeneratedFiles() }
        explainFileButton.addActionListener      { explainExistingFile() }
        clearButton.addActionListener            { clearOutput() }
    }

    private fun selectSource(local: Boolean) {
        isLocalSelected = local
        refreshCard(localCard,  selected = local)
        refreshCard(githubCard, selected = !local)
        githubConfigPanel.isVisible = !local
        githubConfigPanel.parent?.revalidate()
        githubConfigPanel.parent?.repaint()
    }

    private fun refreshCard(card: JPanel, selected: Boolean) {
        card.border     = cardBorder(selected)
        card.background = cardBg(selected)
        card.revalidate()
        card.repaint()
    }

    private fun clearOutput() {
        outputPane.text = ""
        generatedDockerfile     = null
        generatedDockerCompose  = null
        generatedGitHubWorkflow = null
        validateButton.isEnabled = false
        explainButton.isEnabled  = false
        appendLog("Output cleared. Ready to generate CI/CD files.\n", "dim")
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Logging helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun appendLog(text: String, style: String = "normal") {
        SwingUtilities.invokeLater {
            try {
                val doc = outputPane.styledDocument
                doc.insertString(doc.length, text, outputPane.getStyle(style))
                outputPane.caretPosition = doc.length
            } catch (_: Exception) { }
        }
    }

    /** Infers log style from leading emoji. */
    private fun appendResults(text: String) {
        val style = when {
            text.contains("✅") || text.contains("🎉") -> "success"
            text.contains("❌")                        -> "error"
            text.contains("⚠️")                        -> "warning"
            text.any { it in "🐳⚙️🐋📁🔗🔍" }         -> "info"
            text.startsWith("━") || text.startsWith("─") -> "dim"
            else                                       -> "normal"
        }
        appendLog(text, style)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Business logic
    // ══════════════════════════════════════════════════════════════════════════

    private fun generateCICDFiles() {
        if (!dockerfileCheck.isSelected && !actionsCheck.isSelected && !composeCheck.isSelected) {
            Messages.showWarningDialog(project, "Please select at least one artifact to generate.", "No Selection")
            return
        }

        val isLocal    = isLocalSelected
        val githubUrl  = githubUrlField.text.trim()
        val branch     = githubBranchComboBox.selectedItem as? String ?: "main"

        appendLog("\n", "normal")
        appendLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
        appendLog("Starting generation...\n", "header")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating CI/CD Files", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val mcpContext = if (isLocal) {
                        indicator.text = "Analyzing local project..."
                        indicator.fraction = 0.1
                        appendResults("📁 Analyzing local project...\n")
                        ProjectAnalyzerService().analyzeProjectWithMCP(project)
                    } else {
                        indicator.text = "Connecting to GitHub..."
                        indicator.fraction = 0.1
                        appendResults("🔗 Connecting to GitHub: $githubUrl (branch: $branch)...\n")
                        val client    = GitHubMCPClient()
                        runBlocking { client.analyzeGitHubRepository(githubUrl, branch) }
                    }

                    val bedrockClient  = BedrockClient()
                    val mcpContextJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mcpContext)
                    var progress       = 0.3
                    val increment      = 0.6 / countSelectedOptions()

                    if (dockerfileCheck.isSelected) {
                        indicator.text = "Generating Dockerfile..."
                        indicator.fraction = progress
                        appendResults("🐳 Generating Dockerfile...\n")
                        val dockerfile = bedrockClient.generateDockerfile(mcpContextJson, usePrefill = true)
                        generatedDockerfile = dockerfile
                        saveToFile("Dockerfile", dockerfile)
                        dockerfilePath = File(project.basePath ?: "", "Dockerfile").absolutePath
                        appendResults("✅ Dockerfile generated successfully!\n")
                        progress += increment
                    }

                    if (actionsCheck.isSelected) {
                        indicator.text = "Generating GitHub Actions workflow..."
                        indicator.fraction = progress
                        appendResults("⚙️ Generating GitHub Actions workflow...\n")
                        val workflow  = bedrockClient.generateGitHubActionsWorkflow(mcpContextJson)
                        generatedGitHubWorkflow = workflow
                        val basePath  = project.basePath ?: throw IllegalStateException("Project path not found")
                        val wfDir     = File(basePath, ".github/workflows").also { it.mkdirs() }
                        val wfFile    = File(wfDir, "build.yml").also { it.writeText(workflow) }
                        githubWorkflowPath = wfFile.absolutePath
                        appendResults("✅ GitHub Actions workflow saved to .github/workflows/build.yml\n")
                        progress += increment
                    }

                    if (composeCheck.isSelected) {
                        indicator.text = "Generating docker-compose.yml..."
                        indicator.fraction = progress
                        appendResults("🐋 Generating docker-compose.yml...\n")
                        val compose = bedrockClient.generateDockerCompose(mcpContextJson)
                        generatedDockerCompose = compose
                        saveToFile("docker-compose.yml", compose)
                        dockerComposePath = File(project.basePath ?: "", "docker-compose.yml").absolutePath
                        appendResults("✅ docker-compose.yml generated successfully!\n")
                        progress += increment
                    }

                    bedrockClient.close()
                    indicator.fraction = 1.0

                    appendLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
                    appendResults("🎉 All selected CI/CD files generated successfully!\n")
                    appendLog("Files saved to project root.  Use Validate or Explain to analyze.\n", "info")

                    val generatedArtifacts = buildList {
                        if (dockerfileCheck.isSelected) add("dockerfile")
                        if (actionsCheck.isSelected)    add("workflow")
                        if (composeCheck.isSelected)    add("compose")
                    }
                    AuditService.getInstance(project).logGeneration(
                        source     = if (isLocal) "LOCAL" else "GITHUB",
                        artifacts  = generatedArtifacts,
                        durationMs = 0L,
                        success    = true
                    )

                    ApplicationManager.getApplication().invokeLater {
                        validateButton.isEnabled = true
                        explainButton.isEnabled  = true
                        Messages.showInfoMessage(
                            project,
                            "CI/CD files generated successfully!\n\nCheck the Output tab for details.",
                            "SpringForge"
                        )
                    }
                } catch (ex: Exception) {
                    appendResults("❌ Error: ${ex.message}\n")
                    AuditService.getInstance(project).logGeneration(
                        source     = if (isLocalSelected) "LOCAL" else "GITHUB",
                        artifacts  = emptyList(),
                        durationMs = 0L,
                        success    = false,
                        errorMsg   = ex.message
                    )
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Failed to generate files:\n${ex.message}", "SpringForge — Error")
                    }
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun countSelectedOptions() =
        listOf(dockerfileCheck, actionsCheck, composeCheck).count { it.isSelected }.coerceAtLeast(1)

    private fun saveToFile(filename: String, content: String) {
        File(project.basePath ?: return, filename).writeText(content)
    }

    private fun validateGeneratedFiles() {
        if (generatedDockerfile == null && generatedDockerCompose == null && generatedGitHubWorkflow == null) {
            Messages.showWarningDialog(project, "No generated files to validate. Generate files first.", "No Files")
            return
        }

        appendLog("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
        appendResults("🔍 Validating generated files...\n")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Validating CI/CD Files", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text     = "Validating..."
                    indicator.fraction = 0.1

                    val result = ValidationService.getInstance(project).validateGeneratedArtifacts(
                        dockerfile        = generatedDockerfile,
                        dockerCompose     = generatedDockerCompose,
                        githubWorkflow    = generatedGitHubWorkflow,
                        dockerfilePath    = dockerfilePath,
                        dockerComposePath = dockerComposePath,
                        githubWorkflowPath = githubWorkflowPath
                    )
                    indicator.fraction = 0.9

                    AuditService.getInstance(project).logValidation(
                        filesCount = result.filesValidated,
                        errorCount = result.getErrorCount(),
                        warnCount  = result.getWarningCount(),
                        infoCount  = result.getInfoCount(),
                        durationMs = result.durationMs,
                        success    = result.isSuccess()
                    )

                    ApplicationManager.getApplication().invokeLater {
                        val summaryStyle = if (result.isSuccess()) "success" else "warning"
                        appendLog("${result.getSummary()} — opening results window.\n", summaryStyle)
                        showValidationDialog(result)
                    }
                    indicator.fraction = 1.0
                } catch (ex: Exception) {
                    appendResults("❌ Validation error: ${ex.message}\n")
                    AuditService.getInstance(project).logValidation(
                        filesCount = 0, errorCount = 0, warnCount = 0, infoCount = 0,
                        durationMs = 0L, success = false, errorMsg = ex.message
                    )
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Validation failed:\n${ex.message}", "Validation Error")
                    }
                }
            }
        })
    }

    private fun validateExistingFilesInProject() {
        val basePath = project.basePath ?: run {
            Messages.showErrorDialog(project, "Project path not found.", "Error")
            return
        }

        val existingFiles = mutableMapOf<String, String>()
        File(basePath, "Dockerfile").takeIf { it.exists() }
            ?.let { existingFiles[it.absolutePath] = it.readText() }
        File(basePath, "docker-compose.yml").takeIf { it.exists() }
            ?.let { existingFiles[it.absolutePath] = it.readText() }
        File(basePath, ".github/workflows").takeIf { it.isDirectory }
            ?.listFiles { f -> f.isFile && (f.name.endsWith(".yml") || f.name.endsWith(".yaml")) }
            ?.forEach { existingFiles[it.absolutePath] = it.readText() }

        if (existingFiles.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No CI/CD files found in the project.\n\nLooking for:\n- Dockerfile\n- docker-compose.yml\n- .github/workflows/*.yml",
                "No Files Found"
            )
            return
        }

        appendLog("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
        appendResults("🔍 Validating ${existingFiles.size} existing file(s)...\n")
        existingFiles.keys.forEach { appendLog("  · ${File(it).name}\n", "dim") }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Validating Existing CI/CD Files", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text     = "Validating..."
                    indicator.fraction = 0.1

                    val result = ValidationService.getInstance(project).validateFiles(existingFiles)
                    indicator.fraction = 0.9

                    AuditService.getInstance(project).logValidation(
                        filesCount = result.filesValidated,
                        errorCount = result.getErrorCount(),
                        warnCount  = result.getWarningCount(),
                        infoCount  = result.getInfoCount(),
                        durationMs = result.durationMs,
                        success    = result.isSuccess()
                    )

                    ApplicationManager.getApplication().invokeLater {
                        val summaryStyle = if (result.isSuccess()) "success" else "warning"
                        appendLog("${result.getSummary()} — opening results window.\n", summaryStyle)
                        showValidationDialog(result)
                    }
                    indicator.fraction = 1.0
                } catch (ex: Exception) {
                    appendResults("❌ Validation error: ${ex.message}\n")
                    AuditService.getInstance(project).logValidation(
                        filesCount = 0, errorCount = 0, warnCount = 0, infoCount = 0,
                        durationMs = 0L, success = false, errorMsg = ex.message
                    )
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Validation failed:\n${ex.message}", "Validation Error")
                    }
                }
            }
        })
    }

    private fun explainGeneratedFiles() {
        if (generatedDockerfile == null && generatedDockerCompose == null && generatedGitHubWorkflow == null) {
            Messages.showWarningDialog(project, "No generated files to explain. Generate files first.", "No Files")
            return
        }

        appendLog("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
        appendResults("💡 Analyzing generated files with AI...\n")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Explaining CI/CD Artifacts", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text     = "Sending artifacts to Claude AI..."
                    indicator.fraction = 0.1

                    val result = ExplainabilityService.getInstance(project).explainGeneratedArtifacts(
                        dockerfile         = generatedDockerfile,
                        dockerCompose      = generatedDockerCompose,
                        githubWorkflow     = generatedGitHubWorkflow,
                        dockerfilePath     = dockerfilePath,
                        dockerComposePath  = dockerComposePath,
                        githubWorkflowPath = githubWorkflowPath
                    )
                    indicator.fraction = 0.95

                    AuditService.getInstance(project).logExplainability(
                        filesCount   = result.filesAnalyzed,
                        insightCount = result.getTotalCount(),
                        durationMs   = result.durationMs,
                        success      = true
                    )

                    ApplicationManager.getApplication().invokeLater {
                        appendLog("${result.getSummary()} — opening explainability window.\n", "info")
                        showExplainabilityDialog(result)
                    }
                    indicator.fraction = 1.0
                } catch (ex: Exception) {
                    appendResults("❌ Explanation error: ${ex.message}\n")
                    AuditService.getInstance(project).logExplainability(
                        filesCount = 0, insightCount = 0,
                        durationMs = 0L, success = false, errorMsg = ex.message
                    )
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Failed to explain files:\n${ex.message}", "SpringForge — Error")
                    }
                }
            }
        })
    }

    private fun explainExistingFile() {
        val fc = JFileChooser().apply {
            currentDirectory        = File(project.basePath ?: System.getProperty("user.home"))
            dialogTitle             = "Select CI/CD File to Explain"
            isMultiSelectionEnabled = true
            fileFilter = object : javax.swing.filechooser.FileFilter() {
                override fun accept(f: File): Boolean =
                    f.isDirectory ||
                    f.name == "Dockerfile" ||
                    f.name.endsWith(".yml") ||
                    f.name.endsWith(".yaml")
                override fun getDescription() = "CI/CD Files (Dockerfile, *.yml, *.yaml)"
            }
        }

        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return

        val selectedFiles = fc.selectedFiles
        if (selectedFiles.isEmpty()) return

        val files = selectedFiles.associate { it.absolutePath to it.readText() }

        appendLog("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n", "dim")
        appendResults("💡 Analyzing ${files.size} file(s) with AI...\n")
        files.keys.forEach { appendLog("  · ${File(it).name}\n", "dim") }

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Explaining CI/CD Files", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        indicator.text     = "Sending files to Claude AI..."
                        indicator.fraction = 0.1

                        val result = ExplainabilityService.getInstance(project).explainFiles(files)
                        indicator.fraction = 0.95

                        AuditService.getInstance(project).logExplainability(
                            filesCount   = result.filesAnalyzed,
                            insightCount = result.getTotalCount(),
                            durationMs   = result.durationMs,
                            success      = true
                        )

                        ApplicationManager.getApplication().invokeLater {
                            appendLog("${result.getSummary()} — opening explainability window.\n", "info")
                            showExplainabilityDialog(result)
                        }
                        indicator.fraction = 1.0
                    } catch (ex: Exception) {
                        appendResults("❌ Explanation error: ${ex.message}\n")
                        AuditService.getInstance(project).logExplainability(
                            filesCount = 0, insightCount = 0,
                            durationMs = 0L, success = false, errorMsg = ex.message
                        )
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project, "Failed to explain files:\n${ex.message}", "SpringForge — Error"
                            )
                        }
                    }
                }
            })
    }

    private fun showExplainabilityDialog(result: ExplainabilityResult) {
        val owner  = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(owner, "CI/CD Explainability — SpringForge", Dialog.ModalityType.MODELESS)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        val panel = ExplainabilityPanel(project)
        panel.displayResults(result)
        panel.setDismissAction { dialog.dispose() }

        dialog.contentPane = panel
        dialog.preferredSize = Dimension(900, 620)
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }

    private fun fetchBranchesFromGitHub() {
        val githubUrl = githubUrlField.text.trim()
        if (githubUrl.isEmpty() || !githubUrl.contains("github.com")) {
            Messages.showWarningDialog(project, "Please enter a valid GitHub repository URL.", "Invalid URL")
            return
        }

        appendResults("🔍 Fetching branches from $githubUrl...\n")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Fetching Branches", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text     = "Connecting to GitHub API..."
                    indicator.fraction = 0.2

                    val cleaned   = githubUrl
                        .removePrefix("https://").removePrefix("http://")
                        .removePrefix("www.").removePrefix("github.com/")
                        .removeSuffix(".git").removeSuffix("/")

                    val parts = cleaned.split("/")
                    if (parts.size < 2) throw IllegalArgumentException("Invalid GitHub URL. Expected: owner/repo")

                    val (owner, repo) = parts[0] to parts[1]
                    indicator.text     = "Fetching branches from $owner/$repo..."
                    indicator.fraction = 0.5

                    val branchNames = GitHubApiClient().listBranches(owner, repo)
                    if (branchNames.isEmpty()) throw IllegalStateException("No branches found in repository")

                    availableBranches = branchNames
                    indicator.fraction = 1.0

                    ApplicationManager.getApplication().invokeLater {
                        githubBranchComboBox.removeAllItems()
                        branchNames.forEach { githubBranchComboBox.addItem(it) }
                        branchNames.find { it == "main" || it == "master" }
                            ?.let { githubBranchComboBox.selectedItem = it }

                        val preview = branchNames.take(5).joinToString(", ") +
                            if (branchNames.size > 5) "…" else ""
                        appendResults("✅ Found ${branchNames.size} branch(es): $preview\n")
                        Messages.showInfoMessage(
                            project,
                            "Found ${branchNames.size} branches:\n${branchNames.take(10).joinToString(", ")}",
                            "Branches Fetched"
                        )
                    }
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        appendResults("❌ Failed to fetch branches: ${ex.message}\n")
                        Messages.showErrorDialog(project, "Failed to fetch branches:\n${ex.message}", "Error")
                    }
                    ex.printStackTrace()
                }
            }
        })
    }
}
