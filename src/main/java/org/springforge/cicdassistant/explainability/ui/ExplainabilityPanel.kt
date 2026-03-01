package org.springforge.cicdassistant.explainability.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.springforge.cicdassistant.explainability.ExplainabilityInsight
import org.springforge.cicdassistant.explainability.ExplainabilityResult
import org.springforge.cicdassistant.explainability.InsightCategory
import java.awt.*
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.swing.*
import javax.swing.text.DefaultHighlighter

/**
 * Displays Explainability Engine results in a side-by-side layout:
 *   LEFT  — source code viewer with highlighted line ranges
 *   RIGHT — scrollable insight cards grouped by category
 *
 * Clicking an insight card scrolls the code pane to the relevant lines
 * and highlights them in amber.
 *
 *   ┌─ summary bar ──────────────────────────────────────────────────────┐
 *   ├─ filter bar: [File ▼] [Category ▼] ─── Click a card to highlight → ┤
 *   ├─ CODE viewer (38%) ──────┬─ insight cards (62%) ───────────────────┤
 *   │  Dockerfile              │  ── 🔴 SECURITY ──── 2 ──────────────  │
 *   │  FROM eclipse-temurin… ← │  ┌─[red]──────────────────────────────┐ │
 *   │  ░░░░░░░░░░░░░░░░░░░░    │  │ 🔴 Non-root user  Lines 42–52 🔍   │ │
 *   │  ██ highlighted lines ██ │  │ explanation text...                │ │
 *   │  ░░░░░░░░░░░░░░░░░░░░    │  └────────────────────────────────────┘ │
 *   ├──────────────────────────┴─────────────────────────────────────────┤
 *   │                                         [Export Report]  [Close]  │
 *   └────────────────────────────────────────────────────────────────────┘
 */
class ExplainabilityPanel(private val project: Project) : JPanel(BorderLayout()) {

    // ── State ─────────────────────────────────────────────────────────────────

    private var currentResult: ExplainabilityResult? = null
    private var dismissAction: (() -> Unit)? = null
    private var populatingCombos = false

    // ── Filter controls ───────────────────────────────────────────────────────

    private val fileCombo     = JComboBox<String>()
    private val categoryCombo = JComboBox<String>()

    // ── Code viewer ───────────────────────────────────────────────────────────

    private val codePane = JTextArea().apply {
        isEditable    = false
        font          = Font("Monospaced", Font.PLAIN, 11)
        background    = JBColor(Color(0xF6F8FA), Color(0x1A1A2E))
        foreground    = JBColor(Color(0x24292E), Color(0xCDD9E5))
        border        = JBUI.Borders.empty(8)
        tabSize       = 2
    }
    private val codeFileLabel = JBLabel("Select a file to view code").apply {
        font   = JBUI.Fonts.label(10f).asBold()
        border = JBUI.Borders.empty(0, 6)
    }
    private val highlightPainter = DefaultHighlighter.DefaultHighlightPainter(
        JBColor(Color(0xFFF176), Color(0x3A3200))
    )

    // ── Persistent UI elements ────────────────────────────────────────────────

    private val summaryLabel = JLabel()
    private val exportButton = JButton("Export PDF")
    private val closeButton  = JButton("Close")

    private val cardsPanel = JPanel().apply {
        layout   = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border   = JBUI.Borders.empty(10, 12)
    }

    companion object {
        private const val ALL_FILES_KEY  = "__ALL__"
        private const val ALL_CATEGORIES = "All Categories"
    }

    init {
        setupUI()
        setupListeners()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI construction
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupUI() {
        // ── Summary bar ───────────────────────────────────────────────────────
        val summaryBar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(7, 10)
            )
            add(summaryLabel, BorderLayout.WEST)
        }

        // ── Filter bar ────────────────────────────────────────────────────────
        for (combo in listOf(fileCombo, categoryCombo)) {
            combo.font      = JBUI.Fonts.label(11f)
            combo.isFocusable = false
        }
        fileCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = when (val v = value as? String) {
                    null, ALL_FILES_KEY -> "All Files"
                    else                -> File(v).name
                }
                return this
            }
        }

        val hintLabel = JBLabel("← click a card to highlight code").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = JBColor(Color(0x999999), Color(0x606060))
        }
        val filterBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            isOpaque = false
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                JBUI.Borders.empty(2, 6)
            )
            add(JBLabel("File:").apply { font = JBUI.Fonts.label(11f) })
            add(fileCombo)
            add(Box.createHorizontalStrut(10))
            add(JBLabel("Category:").apply { font = JBUI.Fonts.label(11f) })
            add(categoryCombo)
            add(Box.createHorizontalStrut(16))
            add(hintLabel)
        }

        val topBar = JPanel(BorderLayout()).apply {
            add(summaryBar, BorderLayout.NORTH)
            add(filterBar,  BorderLayout.SOUTH)
        }
        add(topBar, BorderLayout.NORTH)

        // ── Left: code viewer ─────────────────────────────────────────────────
        val codeHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            border   = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border())
            isOpaque = false
            add(JBLabel("CODE").apply {
                font       = JBUI.Fonts.label(9f).asBold()
                foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
            })
            add(codeFileLabel)
        }
        val codeScroll = JBScrollPane(codePane).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
        val leftPanel = JPanel(BorderLayout()).apply {
            minimumSize   = Dimension(160, 0)
            preferredSize = Dimension(340, 0)
            add(codeHeader, BorderLayout.NORTH)
            add(codeScroll, BorderLayout.CENTER)
        }

        // ── Right: cards scroll ───────────────────────────────────────────────
        val cardsScroll = JBScrollPane(cardsPanel).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // ── Split pane ────────────────────────────────────────────────────────
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, cardsScroll).apply {
            resizeWeight = 0.38
            dividerSize  = 5
            border       = JBUI.Borders.empty()
        }
        add(splitPane, BorderLayout.CENTER)

        // ── Button row ────────────────────────────────────────────────────────
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
            for (btn in listOf(exportButton, closeButton)) {
                btn.font           = JBUI.Fonts.label(11f)
                btn.isFocusPainted = false
                btn.border         = JBUI.Borders.empty(5, 10)
            }
            add(exportButton)
            add(closeButton)
        }
        add(buttonPanel, BorderLayout.SOUTH)
        exportButton.isEnabled = false
    }

    private fun setupListeners() {
        fileCombo.addActionListener     { if (!populatingCombos) { updateCodePane(); rebuildCards() } }
        categoryCombo.addActionListener { if (!populatingCombos) rebuildCards() }
        exportButton.addActionListener  { currentResult?.let { exportReport(it) } }
        closeButton.addActionListener   { dismissAction?.invoke() ?: clearResults() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════════════════

    fun displayResults(result: ExplainabilityResult) {
        currentResult = result
        populatingCombos = true

        fileCombo.removeAllItems()
        fileCombo.addItem(ALL_FILES_KEY)
        result.getInsightsByFile().keys.sortedBy { File(it).name }.forEach { fileCombo.addItem(it) }
        // Auto-select the first file so the code pane shows something useful
        fileCombo.selectedIndex = if (result.getInsightsByFile().size == 1) 1 else 0

        categoryCombo.removeAllItems()
        categoryCombo.addItem(ALL_CATEGORIES)
        val usedCats = result.insights.map { it.category }.toSet()
        InsightCategory.values().filter { it in usedCats }.forEach { categoryCombo.addItem(it.name) }
        categoryCombo.selectedIndex = 0

        populatingCombos = false

        summaryLabel.text      = buildSummaryHtml(result)
        exportButton.isEnabled = result.getTotalCount() > 0
        updateCodePane()
        rebuildCards()
    }

    fun clearResults() {
        currentResult = null
        populatingCombos = true
        fileCombo.removeAllItems()
        categoryCombo.removeAllItems()
        populatingCombos = false
        summaryLabel.text      = ""
        exportButton.isEnabled = false
        codePane.text          = ""
        codeFileLabel.text     = "Select a file to view code"
        cardsPanel.removeAll()
        cardsPanel.revalidate()
        cardsPanel.repaint()
    }

    fun setDismissAction(action: () -> Unit) { dismissAction = action }

    // ══════════════════════════════════════════════════════════════════════════
    //  Code pane helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun updateCodePane() {
        val selFile = fileCombo.selectedItem as? String ?: ALL_FILES_KEY
        if (selFile == ALL_FILES_KEY) {
            codePane.foreground = JBColor(Color(0x999999), Color(0x606060))
            codePane.text       = "← Select a specific file in the filter above\n   to view its source code here.\n\n   Click any insight card to auto-switch\n   and highlight the relevant lines."
            codeFileLabel.text  = "No file selected"
        } else {
            codePane.foreground = JBColor(Color(0x24292E), Color(0xCDD9E5))
            val file = File(selFile)
            if (file.exists()) {
                codePane.text      = file.readText()
                codeFileLabel.text = file.name
            } else {
                codePane.text      = "(File not found on disk:\n $selFile)"
                codeFileLabel.text = file.name + " ⚠"
            }
            codePane.caretPosition = 0
            codePane.highlighter.removeAllHighlights()
        }
    }

    private fun highlightLines(startLine: Int?, endLine: Int?) {
        if (startLine == null || startLine < 1) return
        val doc      = codePane.document
        val root     = doc.defaultRootElement
        val lineCount = root.elementCount
        val safeStart = (startLine - 1).coerceIn(0, lineCount - 1)
        val safeEnd   = ((endLine ?: startLine) - 1).coerceIn(safeStart, lineCount - 1)
        val startOff  = root.getElement(safeStart).startOffset
        val endOff    = root.getElement(safeEnd).endOffset

        codePane.highlighter.removeAllHighlights()
        try { codePane.highlighter.addHighlight(startOff, endOff, highlightPainter) } catch (_: Exception) {}

        // Scroll so highlighted block appears near the top of the viewport
        SwingUtilities.invokeLater {
            try {
                val rect = codePane.modelToView(startOff) ?: return@invokeLater
                val vp   = codePane.parent as? JViewport ?: return@invokeLater
                rect.y = maxOf(0, rect.y - vp.height / 4)
                codePane.scrollRectToVisible(rect)
            } catch (_: Exception) {}
        }
    }

    private fun onInsightSelected(insight: ExplainabilityInsight) {
        val currentFile = fileCombo.selectedItem as? String
        if (currentFile != insight.filePath) {
            populatingCombos = true
            fileCombo.selectedItem = insight.filePath
            populatingCombos = false
            updateCodePane()
            rebuildCards()
            SwingUtilities.invokeLater { highlightLines(insight.lineStart, insight.lineEnd) }
        } else {
            highlightLines(insight.lineStart, insight.lineEnd)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Card building
    // ══════════════════════════════════════════════════════════════════════════

    private fun rebuildCards() {
        if (populatingCombos) return
        cardsPanel.removeAll()

        val result = currentResult ?: run { cardsPanel.revalidate(); cardsPanel.repaint(); return }

        var insights = result.insights
        val selFile = fileCombo.selectedItem as? String ?: ALL_FILES_KEY
        if (selFile != ALL_FILES_KEY) insights = insights.filter { it.filePath == selFile }

        val selCat = categoryCombo.selectedItem as? String ?: ALL_CATEGORIES
        if (selCat != ALL_CATEGORIES) {
            val cat = runCatching { InsightCategory.valueOf(selCat) }.getOrNull()
            if (cat != null) insights = insights.filter { it.category == cat }
        }

        if (insights.isEmpty()) {
            cardsPanel.add(JBLabel("No insights match the current filter.").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                border     = JBUI.Borders.empty(30)
                foreground = JBColor.GRAY
            })
        } else {
            val grouped = insights.groupBy { it.category }
            InsightCategory.values().forEach { cat ->
                val catInsights = (grouped[cat] ?: return@forEach).sortedBy { it.priority }
                if (catInsights.isEmpty()) return@forEach
                cardsPanel.add(buildGroupHeader(cat, catInsights.size))
                cardsPanel.add(Box.createVerticalStrut(6))
                catInsights.forEach { insight ->
                    cardsPanel.add(InsightCard(insight))
                    cardsPanel.add(Box.createVerticalStrut(8))
                }
                cardsPanel.add(Box.createVerticalStrut(10))
            }
        }

        cardsPanel.revalidate()
        cardsPanel.repaint()
    }

    private fun buildGroupHeader(cat: InsightCategory, count: Int): JPanel {
        val panel = JPanel(BorderLayout(8, 0)).apply {
            isOpaque    = false
            maximumSize = Dimension(Int.MAX_VALUE, 22)
            alignmentX  = Component.LEFT_ALIGNMENT
            border      = JBUI.Borders.empty(4, 0, 2, 0)
        }
        panel.add(JBLabel("${categoryEmoji(cat)}  ${cat.name}").apply {
            font       = JBUI.Fonts.label(10f).asBold()
            foreground = categoryForeground(cat)
        }, BorderLayout.WEST)
        panel.add(JSeparator().apply {
            foreground = JBColor(Color(0xDDDDDD), Color(0x3A3F4B))
        }, BorderLayout.CENTER)
        panel.add(JBLabel("$count").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
        }, BorderLayout.EAST)
        return panel
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  InsightCard
    // ══════════════════════════════════════════════════════════════════════════

    private inner class InsightCard(insight: ExplainabilityInsight) : JPanel() {
        init {
            layout      = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque    = true
            background  = UIUtil.getPanelBackground()
            alignmentX  = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            cursor      = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, categoryAccentColor(insight.category)),
                    BorderFactory.createLineBorder(JBColor.border(), 1)
                ),
                JBUI.Borders.empty(10, 12, 10, 12)
            )

            // ── Header row ────────────────────────────────────────────────────
            val headerRow = JPanel(BorderLayout(8, 0)).apply {
                isOpaque    = false
                maximumSize = Dimension(Int.MAX_VALUE, 20)
                alignmentX  = Component.LEFT_ALIGNMENT
            }
            headerRow.add(JBLabel("${categoryEmoji(insight.category)} ${insight.getCategoryLabel()}").apply {
                font       = JBUI.Fonts.label(10f).asBold()
                foreground = categoryForeground(insight.category)
            }, BorderLayout.WEST)
            headerRow.add(JBLabel(insight.sectionName).apply {
                font       = JBUI.Fonts.label(10f)
                foreground = JBColor.GRAY
            }, BorderLayout.CENTER)
            val lineRange = insight.getLineRange()
            if (lineRange != "—") {
                headerRow.add(JBLabel("Lines $lineRange  🔍").apply {
                    font        = JBUI.Fonts.label(10f)
                    foreground  = JBColor(Color(0x005CC5), Color(0x64B5F6))
                    toolTipText = "Click to highlight these lines in the code viewer"
                }, BorderLayout.EAST)
            }

            val sep = JSeparator().apply {
                maximumSize = Dimension(Int.MAX_VALUE, 1)
                foreground  = JBColor(Color(0xEEEEEE), Color(0x3A3F4B))
            }

            val titleLabel = JBLabel("<html><b>${escapeHtml(insight.title)}</b></html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border     = JBUI.Borders.empty(6, 0, 4, 0)
            }

            val explanationArea = JTextArea(insight.explanation).apply {
                isEditable    = false
                lineWrap      = true
                wrapStyleWord = true
                font          = JBUI.Fonts.label(11f)
                background    = UIUtil.getPanelBackground()
                border        = JBUI.Borders.empty(0, 0, 4, 0)
                alignmentX    = Component.LEFT_ALIGNMENT
            }

            add(headerRow)
            add(Box.createVerticalStrut(6))
            add(sep)
            add(titleLabel)
            add(explanationArea)

            if (insight.recommendations.isNotEmpty()) {
                add(Box.createVerticalStrut(6))
                add(JBLabel("Recommendations:").apply {
                    font       = JBUI.Fonts.label(10f).asBold()
                    foreground = JBColor(Color(0x6A737D), Color(0x868E9B))
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(2))
                add(JTextArea(insight.recommendations.mapIndexed { i, r -> "  ${i + 1}. $r" }.joinToString("\n")).apply {
                    isEditable    = false
                    lineWrap      = true
                    wrapStyleWord = true
                    font          = JBUI.Fonts.label(11f)
                    background    = UIUtil.getPanelBackground()
                    border        = JBUI.Borders.empty()
                    alignmentX    = Component.LEFT_ALIGNMENT
                })
            }

            // Click to highlight corresponding lines in code pane
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) = onInsightSelected(insight)
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Category colour helpers
    // ══════════════════════════════════════════════════════════════════════════

    private fun categoryAccentColor(cat: InsightCategory): Color = when (cat) {
        InsightCategory.SECURITY      -> JBColor(Color(0xD73A49), Color(0xEF5350))
        InsightCategory.PERFORMANCE   -> JBColor(Color(0x28A745), Color(0x4CAF50))
        InsightCategory.BUILD         -> JBColor(Color(0x005CC5), Color(0x64B5F6))
        InsightCategory.CONFIGURATION -> JBColor(Color(0xE36209), Color(0xFFA726))
        InsightCategory.RELIABILITY   -> JBColor(Color(0x6F42C1), Color(0xCE93D8))
        InsightCategory.DESIGN        -> JBColor(Color(0x6A737D), Color(0x868E9B))
    }

    private fun categoryForeground(cat: InsightCategory): Color = when (cat) {
        InsightCategory.SECURITY      -> JBColor(Color(0xA0001A), Color(0xFF8A80))
        InsightCategory.PERFORMANCE   -> JBColor(Color(0x1A6B2A), Color(0x69F0AE))
        InsightCategory.BUILD         -> JBColor(Color(0x0050A0), Color(0x82B1FF))
        InsightCategory.CONFIGURATION -> JBColor(Color(0x7A6A00), Color(0xFFD180))
        InsightCategory.RELIABILITY   -> JBColor(Color(0x5A1A8A), Color(0xE040FB))
        InsightCategory.DESIGN        -> JBColor(Color(0x444444), Color(0xAAAAAA))
    }

    private fun categoryEmoji(cat: InsightCategory): String = when (cat) {
        InsightCategory.SECURITY      -> "🔴"
        InsightCategory.PERFORMANCE   -> "🟢"
        InsightCategory.BUILD         -> "🔵"
        InsightCategory.CONFIGURATION -> "🟠"
        InsightCategory.RELIABILITY   -> "🟣"
        InsightCategory.DESIGN        -> "⬜"
    }

    private fun categoryDescription(cat: InsightCategory): String = when (cat) {
        InsightCategory.SECURITY      -> "Authentication, secrets management, permissions, and vulnerability mitigations"
        InsightCategory.PERFORMANCE   -> "Caching strategies, parallel execution, conditional steps, and optimisations"
        InsightCategory.BUILD         -> "Compilation, testing, packaging, and artifact creation steps"
        InsightCategory.CONFIGURATION -> "Environment variables, runner selection, matrix strategy, and workflow settings"
        InsightCategory.RELIABILITY   -> "Retry logic, timeout settings, health checks, and fault-tolerance mechanisms"
        InsightCategory.DESIGN        -> "Overall structure, job dependencies, branching strategy, and architectural decisions"
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ══════════════════════════════════════════════════════════════════════════
    //  Summary HTML
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildSummaryHtml(result: ExplainabilityResult): String = buildString {
        append("<html>")
        append("<b>${result.filesAnalyzed}</b> file${if (result.filesAnalyzed != 1) "s" else ""}")
        append("  &#160;·&#160;  <b>${result.getTotalCount()}</b> insight${if (result.getTotalCount() != 1) "s" else ""}")
        val secCount  = result.getCountByCategory(InsightCategory.SECURITY)
        val perfCount = result.getCountByCategory(InsightCategory.PERFORMANCE)
        val relCount  = result.getCountByCategory(InsightCategory.RELIABILITY)
        if (secCount  > 0) append("  &#160;·&#160;  <font color='#D73A49'>$secCount security</font>")
        if (perfCount > 0) append("  &#160;·&#160;  <font color='#28A745'>$perfCount performance</font>")
        if (relCount  > 0) append("  &#160;·&#160;  <font color='#6F42C1'>$relCount reliability</font>")
        if (result.durationMs > 0) append("  &#160;·&#160;  <font color='gray'>${result.durationMs}ms</font>")
        append("</html>")
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Export — PDF
    // ══════════════════════════════════════════════════════════════════════════

    private fun exportReport(result: ExplainabilityResult) {
        val fc = JFileChooser().apply {
            dialogTitle  = "Export Explainability Report as PDF"
            selectedFile = File("SpringForge-Explainability-Report.pdf")
            fileFilter   = object : javax.swing.filechooser.FileFilter() {
                override fun accept(f: File) = f.isDirectory || f.name.endsWith(".pdf")
                override fun getDescription() = "PDF Document (*.pdf)"
            }
        }
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                val out = fc.selectedFile.let {
                    if (it.name.endsWith(".pdf")) it else File(it.absolutePath + ".pdf")
                }
                generatePdfReport(result, out)
                Messages.showInfoMessage(project, "Report exported to:\n${out.absolutePath}", "Export Successful")
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to export PDF:\n${e.message}", "Export Failed")
            }
        }
    }

    private fun generatePdfReport(result: ExplainabilityResult, outputFile: File) {
        val html = buildHtmlReport(result)
        outputFile.outputStream().use { os ->
            PdfRendererBuilder()
                .useFastMode()
                .withHtmlContent(html, null)
                .toStream(os)
                .run()
        }
    }

    private fun buildHtmlReport(result: ExplainabilityResult): String {
        val formatter   = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' HH:mm")
        val dateStr     = result.timestamp.atZone(ZoneId.systemDefault()).format(formatter)
        val filesByName = result.getInsightsByFile().entries.sortedBy { File(it.key).name }
        val allUsedCats = InsightCategory.values().filter { cat -> result.insights.any { it.category == cat } }

        val logoSrc: String = runCatching {
            this::class.java.classLoader
                .getResourceAsStream("icons/springforge.png")
                ?.readBytes()
                ?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) }
                ?: ""
        }.getOrDefault("")

        return buildString {

            // ── Document head ─────────────────────────────────────────────────
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><meta charset=\"UTF-8\"/>")
            appendLine("<style>")
            appendLine("""
@page { size: A4; margin: 1.4cm 1.5cm 1.8cm 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 10pt; color: #24292E; margin: 0; padding: 0; }
a { color: inherit; text-decoration: none; }

/* ── Header ── */
.hdr { background-color: #1E3A5F; padding: 18pt 22pt; width: 100%; }
.hdr td { vertical-align: middle; }
.hdr-title { font-size: 17pt; font-weight: bold; color: #FFFFFF; }
.hdr-sub   { font-size: 9.5pt; color: #B0C4DE; margin-top: 3pt; }
.hdr-meta  { font-size: 8.5pt; color: #90A4AE; margin-top: 7pt; }
.hdr-brand { font-size: 7.5pt; color: #B0C4DE; font-weight: bold; text-align: right; }

/* ── Section titles ── */
.sec-title { font-size: 12pt; font-weight: bold; color: #1E3A5F;
             border-bottom: 2pt solid #CBD5E1; padding-bottom: 5pt;
             margin: 18pt 0 10pt 0; }

/* ── File title ── */
.file-title { font-size: 11pt; font-weight: bold; color: #24292E;
              background-color: #F1F5F9; border-left: 4pt solid #2563EB;
              padding: 7pt 12pt; margin: 14pt 0 8pt 0; }

/* ── Category header ── */
.cat-hdr { font-size: 9.5pt; font-weight: bold; padding: 5pt 8pt;
           margin: 10pt 0 5pt 0; }

/* ── Insight card ── */
.card { border: 1pt solid #E1E4E8; border-left-width: 4pt;
        margin-bottom: 8pt; padding: 9pt 12pt;
        page-break-inside: avoid; background-color: #FFFFFF; }
.c-meta  { font-size: 8pt; color: #6A737D; margin-bottom: 4pt; }
.c-cat   { font-weight: bold; font-size: 8pt; }
.c-title { font-size: 10.5pt; font-weight: bold; color: #24292E; margin: 4pt 0; }
.c-expl  { font-size: 9.5pt; line-height: 1.55; color: #3D3D3D; margin: 5pt 0; }
.rec-box { background-color: #FFFDE7; border: 1pt solid #F9A825;
           padding: 7pt 10pt; margin-top: 7pt; }
.rec-hdr { font-weight: bold; font-size: 8.5pt; color: #6D4C00; margin-bottom: 4pt; }
.rec-ol  { margin: 0; padding-left: 16pt; font-size: 9pt; color: #444444; }
.rec-ol li { margin-bottom: 2pt; }

/* ── Summary table ── */
.sum-tbl { width: 100%; border-collapse: collapse; margin: 10pt 0; font-size: 9.5pt; }
.sum-tbl th { background-color: #1E3A5F; color: #FFFFFF; padding: 6pt 10pt;
              text-align: center; font-size: 9pt; }
.sum-tbl th.lbl { text-align: left; }
.sum-tbl td { padding: 5pt 10pt; text-align: center; border: 1pt solid #E2E8F0; }
.sum-tbl td.lbl { text-align: left; font-weight: bold; }
.sum-tbl tr:nth-child(even) td { background-color: #F8FAFC; }
.sum-tbl .tot td { background-color: #EFF6FF; font-weight: bold;
                   border-top: 2pt solid #2563EB; }

/* ── Legend table ── */
.leg-tbl { width: 100%; border-collapse: collapse; margin: 8pt 0; font-size: 9pt; }
.leg-tbl th { background-color: #F1F5F9; padding: 6pt 10pt; text-align: left;
              border-bottom: 1pt solid #CBD5E1; }
.leg-tbl td { padding: 5pt 10pt; border-bottom: 1pt solid #F1F5F9; vertical-align: top; }
.leg-tbl tr:nth-child(even) td { background-color: #FAFBFC; }

/* ── Footer ── */
.footer { text-align: center; color: #94A3B8; font-size: 7.5pt;
          font-style: italic; padding-top: 10pt;
          border-top: 1pt solid #E2E8F0; margin-top: 20pt; }
            """.trimIndent())
            appendLine("</style></head><body>")

            // ── Header ────────────────────────────────────────────────────────
            appendLine("<table class=\"hdr\" cellspacing=\"0\" cellpadding=\"0\"><tr>")
            if (logoSrc.isNotEmpty()) {
                appendLine("  <td style=\"width:52pt; padding-right:14pt;\">")
                appendLine("    <img src=\"$logoSrc\" style=\"height:44pt; width:44pt;\"/>")
                appendLine("  </td>")
            }
            appendLine("  <td>")
            appendLine("    <div class=\"hdr-title\">CI/CD Explainability Report</div>")
            appendLine("    <div class=\"hdr-sub\">AI-Powered Learning Guide &#8212; Understanding your CI/CD configuration, line by line</div>")
            append("    <div class=\"hdr-meta\">Generated: $dateStr")
            append(" &#160;&#8226;&#160; ${result.filesAnalyzed} file${if (result.filesAnalyzed != 1) "s" else ""}")
            append(" &#160;&#8226;&#160; ${result.getTotalCount()} insight${if (result.getTotalCount() != 1) "s" else ""}")
            if (result.durationMs > 0) append(" &#160;&#8226;&#160; ${result.durationMs}ms")
            appendLine("</div>")
            appendLine("  </td>")
            appendLine("  <td style=\"width:80pt; vertical-align:top; padding-left:12pt;\">")
            appendLine("    <div class=\"hdr-brand\">SPRINGFORGE<br/>Explainability Engine</div>")
            appendLine("  </td>")
            appendLine("</tr></table>")

            // ── Summary Dashboard ─────────────────────────────────────────────
            appendLine("<div class=\"sec-title\">Summary Dashboard</div>")
            appendLine("<table class=\"sum-tbl\" cellspacing=\"0\">")
            appendLine("  <tr>")
            appendLine("    <th class=\"lbl\">Category</th>")
            filesByName.forEach { (fp, _) -> appendLine("    <th>${escapeHtml(File(fp).name)}</th>") }
            appendLine("    <th>Total</th>")
            appendLine("  </tr>")
            allUsedCats.forEach { cat ->
                var total = 0
                appendLine("  <tr>")
                appendLine("    <td class=\"lbl\"><span style=\"color:${categoryHex(cat)};\">${escapeHtml(cat.name)}</span></td>")
                filesByName.forEach { (_, ins) ->
                    val c = ins.count { it.category == cat }; total += c
                    appendLine("    <td>${if (c > 0) "<b>$c</b>" else "&#8212;"}</td>")
                }
                appendLine("    <td><b>$total</b></td>")
                appendLine("  </tr>")
            }
            var grand = 0
            appendLine("  <tr class=\"tot\">")
            appendLine("    <td class=\"lbl\">TOTAL</td>")
            filesByName.forEach { (_, ins) -> grand += ins.size; appendLine("    <td>${ins.size}</td>") }
            appendLine("    <td>$grand</td>")
            appendLine("  </tr>")
            appendLine("</table>")

            // ── Per-file sections ─────────────────────────────────────────────
            filesByName.forEach { (filePath, insights) ->
                val fileName = File(filePath).name
                appendLine("<div class=\"file-title\">${escapeHtml(fileIconLabel(filePath))} ${escapeHtml(fileName)}</div>")

                InsightCategory.values().forEach { cat ->
                    val catInsights = insights.filter { it.category == cat }.sortedBy { it.priority }
                    if (catInsights.isEmpty()) return@forEach
                    val hex = categoryHex(cat)
                    val bg  = categoryLightBg(cat)

                    appendLine("<div class=\"cat-hdr\" style=\"color:${hex}; background-color:${bg};\">")
                    appendLine("  ${escapeHtml(cat.name)} (${catInsights.size})")
                    appendLine("  <span style=\"font-size:8pt; font-weight:normal; margin-left:8pt;\">&#8212; ${escapeHtml(categoryDescription(cat))}</span>")
                    appendLine("</div>")

                    catInsights.forEach { insight ->
                        appendLine("<div class=\"card\" style=\"border-left-color:${hex};\">")
                        appendLine("  <div class=\"c-meta\">")
                        appendLine("    <span class=\"c-cat\" style=\"color:${hex};\">${escapeHtml(cat.name)}</span>")
                        if (insight.sectionName.isNotBlank())
                            appendLine("    &#160;&#8226;&#160; ${escapeHtml(insight.sectionName)}")
                        val range = insight.getLineRange()
                        if (range != "—")
                            appendLine("    &#160;&#8226;&#160; <i>Lines ${escapeHtml(range)}</i>")
                        appendLine("  </div>")
                        appendLine("  <div class=\"c-title\">${escapeHtml(insight.title)}</div>")
                        appendLine("  <div class=\"c-expl\">${escapeHtml(insight.explanation).replace("\n", "<br/>")}</div>")
                        if (insight.recommendations.isNotEmpty()) {
                            appendLine("  <div class=\"rec-box\">")
                            appendLine("    <div class=\"rec-hdr\">Recommendations</div>")
                            appendLine("    <ol class=\"rec-ol\">")
                            insight.recommendations.forEach { rec ->
                                appendLine("      <li>${escapeHtml(rec)}</li>")
                            }
                            appendLine("    </ol></div>")
                        }
                        appendLine("</div>")
                    }
                }
            }

            // ── Category Legend ───────────────────────────────────────────────
            appendLine("<div class=\"sec-title\">Category Legend</div>")
            appendLine("<table class=\"leg-tbl\" cellspacing=\"0\">")
            appendLine("  <tr><th>Category</th><th>What it covers</th></tr>")
            InsightCategory.values().forEach { cat ->
                appendLine("  <tr>")
                appendLine("    <td><span style=\"color:${categoryHex(cat)}; font-weight:bold;\">${escapeHtml(cat.name)}</span></td>")
                appendLine("    <td>${escapeHtml(categoryDescription(cat))}</td>")
                appendLine("  </tr>")
            }
            appendLine("</table>")

            // ── Footer ────────────────────────────────────────────────────────
            appendLine("<div class=\"footer\">")
            appendLine("  Generated by SpringForge Explainability Engine")
            appendLine("</div>")
            appendLine("</body></html>")
        }
    }

    // ── PDF report helpers ────────────────────────────────────────────────────

    private fun categoryHex(cat: InsightCategory): String = when (cat) {
        InsightCategory.SECURITY      -> "#D73A49"
        InsightCategory.PERFORMANCE   -> "#28A745"
        InsightCategory.BUILD         -> "#005CC5"
        InsightCategory.CONFIGURATION -> "#E36209"
        InsightCategory.RELIABILITY   -> "#6F42C1"
        InsightCategory.DESIGN        -> "#6A737D"
    }

    private fun categoryLightBg(cat: InsightCategory): String = when (cat) {
        InsightCategory.SECURITY      -> "#FFF0F0"
        InsightCategory.PERFORMANCE   -> "#F0FFF4"
        InsightCategory.BUILD         -> "#EFF4FF"
        InsightCategory.CONFIGURATION -> "#FFF8F0"
        InsightCategory.RELIABILITY   -> "#F8F0FF"
        InsightCategory.DESIGN        -> "#F6F8FA"
    }

    private fun fileIconLabel(filePath: String): String = when {
        File(filePath).name.lowercase() == "dockerfile"         -> "[Dockerfile]"
        File(filePath).name.lowercase().contains("compose")     -> "[Compose]"
        filePath.endsWith(".yml") || filePath.endsWith(".yaml") -> "[Workflow]"
        else                                                     -> "[File]"
    }
}
