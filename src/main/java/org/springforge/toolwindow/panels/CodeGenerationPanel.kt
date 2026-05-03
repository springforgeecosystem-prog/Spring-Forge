package org.springforge.toolwindow.panels

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.springforge.codegeneration.actions.CreateNewProjectAction
import org.springforge.codegeneration.actions.GenerateCodeAction
import org.springforge.codegeneration.analysis.ExistingEntityExtractor
import org.springforge.codegeneration.parser.InputModel
import org.springforge.codegeneration.parser.YamlParser
import org.springforge.codegeneration.parser.YamlWriter
import org.springforge.codegeneration.service.GenerationResult
import org.springforge.codegeneration.service.GenerationResultService
import org.springforge.codegeneration.ui.EntityDesignerDialog
import org.springforge.auth.SessionManager
import org.springforge.subscription.SubscriptionManager
import org.springforge.subscription.ui.RequestLimitDialog
import java.awt.*
import java.io.File
import javax.swing.*

// ─────────────────────────────────────────────────────────────────
//  Helper: a panel that always sizes itself to the scroll-pane's
//  viewport width so content never overflows horizontally.
// ─────────────────────────────────────────────────────────────────
private class ScrollableBoxPanel : JPanel(), Scrollable {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(r: Rectangle?, o: Int, d: Int) = 16
    override fun getScrollableBlockIncrement(r: Rectangle?, o: Int, d: Int) = 64
    override fun getScrollableTracksViewportWidth() = true   // KEY: match viewport width
    override fun getScrollableTracksViewportHeight() = false  // scroll vertically
}

/**
 * Code Generation Panel for SpringForge Tool Window
 */
class CodeGenerationPanel(private val project: Project) : JPanel() {

    private val resultArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font("JetBrains Mono", Font.PLAIN, 11).let { f ->
            if (f.family == "JetBrains Mono") f else Font(Font.MONOSPACED, Font.PLAIN, 11)
        }
        background = JBColor(Color(43, 43, 43), Color(43, 43, 43))
        foreground = JBColor(Color(187, 187, 187), Color(187, 187, 187))
        caretColor = foreground
        border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
        text = " No generation results yet."
    }

    private val resultListener: () -> Unit = {
        ApplicationManager.getApplication().invokeLater {
            val latest = GenerationResultService.getInstance(project).getLatestResult()
            if (latest != null) updateResultDisplay(latest)
        }
    }

    init {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        setupUI()
        GenerationResultService.getInstance(project).addListener(resultListener)
    }

    // ─── Helpers ─────────────────────────────────────────────────

    /** A read-only, word-wrapping text area that looks like a label. */
    private fun wrappingLabel(text: String, bold: Boolean = false, fg: Color? = null): JTextArea {
        val ta = JTextArea(text)
        ta.lineWrap = true
        ta.wrapStyleWord = true
        ta.isEditable = false
        ta.isFocusable = false
        ta.isOpaque = false
        ta.border = BorderFactory.createEmptyBorder()
        if (bold) ta.font = ta.font.deriveFont(Font.BOLD)
        if (fg != null) ta.foreground = fg
        // Must stretch horizontally but use only needed height
        ta.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        return ta
    }

    /** Wraps a component so BoxLayout stretches it horizontally but not vertically. */
    private fun fullWidth(comp: JComponent): JComponent {
        comp.alignmentX = Component.LEFT_ALIGNMENT
        comp.maximumSize = Dimension(Int.MAX_VALUE, comp.preferredSize.height)
        return comp
    }

    /** Wraps a component, letting it stretch both ways (for fillers / scroll areas). */
    private fun fullWidthStretch(comp: JComponent): JComponent {
        comp.alignmentX = Component.LEFT_ALIGNMENT
        comp.maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        return comp
    }

    // ─── Main Layout ─────────────────────────────────────────────

    private fun setupUI() {
        val content = ScrollableBoxPanel()
        content.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        // Title
        val titleLabel = JBLabel("Code Generation Tools")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        content.add(fullWidth(titleLabel))

        content.add(Box.createVerticalStrut(6))
        content.add(fullWidth(JSeparator()))
        content.add(Box.createVerticalStrut(4))

        // Description — wrapping text area, reflows automatically
        val descArea = wrappingLabel(
            "Generate Spring Boot projects with architecture-aware scaffolding and intelligent code templates.",
            fg = JBColor.GRAY
        )
        descArea.font = descArea.font.deriveFont(12f)
        content.add(fullWidthStretch(descArea))

        content.add(Box.createVerticalStrut(12))

        // ★ Primary button — Generate Code
        content.add(fullWidthStretch(createActionCard(
            title = "\uD83D\uDE80 Generate Code (Entity Designer)",
            description = "Design entities visually, save to input.yml, and generate Spring Boot code with AI",
            primary = true
        ) { openEntityDesignerAndGenerate() }))

        content.add(Box.createVerticalStrut(8))

        // Secondary button — Create New Project
        content.add(fullWidthStretch(createActionCard(
            title = "Create New Spring Boot Project",
            description = "Create and initialize a new Spring Boot project with architecture template"
        ) {
            val action = CreateNewProjectAction()
            action.actionPerformed(createActionEvent())
        }))

        // Vertical glue pushes bottom panels down
        content.add(Box.createVerticalGlue())

        // ── Generation Output terminal ───────────────────────────
        content.add(fullWidthStretch(createResultsPanel()))

        // ── Architecture info ────────────────────────────────────
        content.add(fullWidthStretch(createInfoPanel()))

        // Outer scroll — only vertical, no horizontal
        val scrollPane = JBScrollPane(content)
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.border = BorderFactory.createEmptyBorder()
        add(scrollPane, BorderLayout.CENTER)
    }

    // ─── Action Card ─────────────────────────────────────────────

    private fun createActionCard(
        title: String,
        description: String,
        primary: Boolean = false,
        action: () -> Unit
    ): JPanel {
        val card = JPanel()
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)

        val borderColor = if (primary)
            JBColor(Color(76, 175, 80), Color(76, 175, 80))
        else JBColor.border()

        val pad = if (primary) 10 else 8
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, if (primary) 2 else 1),
            BorderFactory.createEmptyBorder(pad, 10, pad, 10)
        )

        val titleArea = wrappingLabel(title, bold = true)
        titleArea.font = titleArea.font.deriveFont(if (primary) 12.5f else 11.5f)
        card.add(titleArea)

        card.add(Box.createVerticalStrut(3))

        val descArea = wrappingLabel(description, fg = JBColor.GRAY)
        descArea.font = descArea.font.deriveFont(11f)
        card.add(descArea)

        // Whole card is clickable
        card.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        card.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) { action() }
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                card.background = JBColor.background().brighter()
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                card.background = null
            }
        })

        return card
    }

    private fun createSeparator(): JSeparator = JSeparator()

    // ─── Entity Designer → save YAML → trigger code generation ─────

    private fun openEntityDesignerAndGenerate() {
        // Subscription gate
        if (!SubscriptionManager.getInstance().canMakeRequest()) {
            RequestLimitDialog.show(project)
            return
        }

        val baseDir = project.basePath ?: return

        // Try to load existing input.yml so the dialog pre-populates
        val yamlFile = File(baseDir, "input.yml")
        var existingModel: InputModel? = null
        if (yamlFile.exists()) {
            try {
                val result = YamlParser.parse(yamlFile.readText())
                if (result.isValid) existingModel = result.data
            } catch (_: Exception) { /* ignore, start fresh */ }
        }

        // Extract existing entities from the project (for read-only display & clash prevention)
        val existingEntities = try {
            val extractor = ExistingEntityExtractor(baseDir)
            val result = extractor.extract()
            if (result.isEmpty) null else result
        } catch (_: Exception) {
            null
        }

        val dialog = EntityDesignerDialog(project, existingModel, existingEntities)
        if (!dialog.showAndGet()) return  // user cancelled

        val model = dialog.toInputModel()

        // Save to input.yml for future use / manual review
        try {
            val yaml = YamlWriter.write(model)
            yamlFile.writeText(yaml)
            Notifications.Bus.notify(
                Notification("SpringForge", "Entity Designer",
                    "Saved ${model.entities.size} entities to input.yml",
                    NotificationType.INFORMATION),
                project
            )
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("SpringForge", "Entity Designer",
                    "Failed to save input.yml: ${ex.message}",
                    NotificationType.ERROR),
                project
            )
            return
        }

        // Trigger code generation (existing action reads input.yml)
        val action = GenerateCodeAction()
        val event = createActionEvent()
        action.actionPerformed(event)

        // Increment subscription usage after generation is triggered
        SessionManager.getInstance().token?.let { tok ->
            Thread { SubscriptionManager.getInstance().incrementUsage(tok) }.start()
        }
    }

    // ─── Generation Results Display ───────────────────────────────

    private fun createResultsPanel(): JPanel {
        val wrapper = JPanel(BorderLayout(0, 4))

        // Header
        val titleLabel = JBLabel("\u25A0 Generation Output")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 11f)
        titleLabel.foreground = JBColor(Color(76, 175, 80), Color(76, 175, 80))
        wrapper.add(titleLabel, BorderLayout.NORTH)

        // Terminal scroll — height adapts, width follows parent
        val scrollPane = JBScrollPane(resultArea)
        scrollPane.minimumSize = Dimension(0, 80)
        scrollPane.preferredSize = Dimension(0, 140)
        scrollPane.border = BorderFactory.createLineBorder(
            JBColor(Color(60, 63, 65), Color(60, 63, 65))
        )
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        wrapper.add(scrollPane, BorderLayout.CENTER)

        wrapper.border = BorderFactory.createEmptyBorder(6, 0, 4, 0)
        return wrapper
    }

    private fun updateResultDisplay(result: GenerationResult) {
        val sb = StringBuilder()
        sb.appendLine("── SpringForge  ${result.formattedTimestamp()} ──")
        sb.appendLine()

        // Files summary
        sb.appendLine("Files generated: ${result.totalFromLLM}")
        if (result.written.isNotEmpty()) {
            sb.appendLine("Written: ${result.written.size}")
            result.written.forEach { path ->
                val shortName = path.substringAfterLast('/')
                val kind = classifyFile(shortName)
                sb.appendLine("  \u2713 $shortName  ($kind)")
            }
        }
        if (result.skipped.isNotEmpty()) {
            sb.appendLine("Skipped: ${result.skipped.size}")
            result.skipped.forEach { path ->
                sb.appendLine("  \u23ED ${path.substringAfterLast('/')}")
            }
        }
        if (result.errors.isNotEmpty()) {
            sb.appendLine("Errors: ${result.errors.size}")
            result.errors.forEach { (path, err) ->
                sb.appendLine("  \u2717 ${path.substringAfterLast('/')} \u2192 $err")
            }
        }

        // Dependencies
        if (result.addedDependencies.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Dependencies added (${result.buildFile}):")
            result.addedDependencies.forEach { sb.appendLine("  \u25AA $it") }
        }
        if (result.depError != null) {
            sb.appendLine("\u26A0 ${result.depError}")
        }

        resultArea.text = sb.toString().trimEnd()
        resultArea.caretPosition = 0
    }

    /**
     * Infers a short description based on the file name / path layer.
     */
    private fun classifyFile(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith("repository.java") -> "Repository"
            lower.endsWith("service.java") && "impl" !in lower -> "Service interface"
            lower.endsWith("serviceimpl.java") || lower.contains("impl") -> "Service impl"
            lower.endsWith("controller.java") -> "REST Controller"
            lower.endsWith("dto.java") -> "DTO"
            lower.endsWith("mapper.java") -> "Mapper"
            lower.endsWith("config.java") || lower.endsWith("configuration.java") -> "Configuration"
            lower.endsWith("exception.java") || lower.contains("exception") -> "Exception"
            lower.endsWith("test.java") -> "Test"
            else -> "Entity / Model"
        }
    }

    private fun createInfoPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(8, 4, 4, 4)
        )

        val infoArea = wrappingLabel(
            "Architecture Patterns Supported: Layered \u2022 Clean \u2022 MVC",
            bold = true, fg = JBColor.GRAY
        )
        infoArea.font = infoArea.font.deriveFont(Font.BOLD, 10.5f)
        panel.add(infoArea, BorderLayout.CENTER)

        return panel
    }

    private fun createActionEvent(): com.intellij.openapi.actionSystem.AnActionEvent {
        val dataContext = com.intellij.openapi.actionSystem.DataContext { dataId ->
            when (dataId) {
                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        return com.intellij.openapi.actionSystem.AnActionEvent(
            null,
            dataContext,
            com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
            com.intellij.openapi.actionSystem.Presentation(),
            com.intellij.openapi.actionSystem.ActionManager.getInstance(),
            0
        )
    }
}
