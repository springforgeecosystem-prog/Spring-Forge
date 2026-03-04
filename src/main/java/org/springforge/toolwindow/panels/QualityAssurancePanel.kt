package org.springforge.toolwindow.panels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.ProjectFixResult
import org.springforge.qualityassurance.network.MLServiceClient
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterException
import java.awt.print.PrinterJob
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.roundToInt

/**
 * Quality Assurance Panel — lives inside the "Quality" tab of the main SpringForge tool window.
 *
 * Flow:
 *   1. User selects architecture pattern (card-based selector)
 *   2. User clicks "Analyze Code Quality"
 *   3. Background task runs ML + Gemini
 *   4. Full report opens in a popup dialog with Print + Close buttons
 */
class QualityAssurancePanel(private val project: Project) : JPanel() {

    // ── State ─────────────────────────────────────────────────────────────────
    private var selectedArchitecture = "Layered"
    private var lastResult: CombinedAnalysisResult? = null
    private var lastFixes: ProjectFixResult? = null

    // ── Architecture cards ────────────────────────────────────────────────────
    private val architectures = listOf(
        Triple("Layered",     "🏗️", "Controller → Service → Repository"),
        Triple("Hexagonal",   "⬡",  "Ports & Adapters pattern"),
        Triple("Clean",       "◎",  "Dependency Rule enforced"),
        Triple("MVC",         "🔄", "Model-View-Controller")
    )
    private val archCards = mutableMapOf<String, JPanel>()

    // ── Status widgets ────────────────────────────────────────────────────────
    private val statusLabel    = JBLabel("Ready to analyze").apply {
        font       = JBUI.Fonts.label(11f)
        foreground = JBColor.GRAY
    }
    private val progressBar    = JProgressBar().apply {
        isIndeterminate = true
        isVisible       = false
        preferredSize   = Dimension(Int.MAX_VALUE, 4)
        border          = EmptyBorder(0, 0, 0, 0)
    }
    private val analyzeButton  = JButton("Analyze Code Quality")
    private val viewReportButton = JButton("View Last Report").apply { isEnabled = false }

    // ── Summary mini-cards ────────────────────────────────────────────────────
    private val scoreCard      = buildMiniCard("—",   "Overall Score",   JBColor(Color(0x3B82F6), Color(0x60A5FA)))
    private val violationsCard = buildMiniCard("—",   "Violations",      JBColor(Color(0xEF4444), Color(0xF87171)))
    private val filesCard      = buildMiniCard("—",   "Files Analyzed",  JBColor(Color(0x10B981), Color(0x34D399)))
    private val fixesCard      = buildMiniCard("—",   "AI Fixes",        JBColor(Color(0x8B5CF6), Color(0xA78BFA)))

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        layout     = BorderLayout()
        background = UIUtil.getPanelBackground()
        border     = JBUI.Borders.empty(14, 14, 14, 14)
        buildUI()
    }

    // =========================================================================
    //  UI Construction
    // =========================================================================

    private fun buildUI() {
        val root = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        root.add(buildSectionLabel("ARCHITECTURE PATTERN"))
        root.add(Box.createVerticalStrut(8))
        root.add(buildArchCards())
        root.add(Box.createVerticalStrut(18))
        root.add(buildSectionLabel("ACTIONS"))
        root.add(Box.createVerticalStrut(8))
        root.add(buildActionRow())
        root.add(Box.createVerticalStrut(6))
        root.add(progressBar.also { it.alignmentX = Component.LEFT_ALIGNMENT; it.maximumSize = Dimension(Int.MAX_VALUE, 4) })
        root.add(Box.createVerticalStrut(4))
        root.add(statusLabel.also { it.alignmentX = Component.LEFT_ALIGNMENT })
        root.add(Box.createVerticalStrut(18))
        root.add(buildSectionLabel("LAST ANALYSIS SUMMARY"))
        root.add(Box.createVerticalStrut(8))
        root.add(buildSummaryCards())
        root.add(Box.createVerticalStrut(18))
        root.add(buildSectionLabel("HOW IT WORKS"))
        root.add(Box.createVerticalStrut(8))
        root.add(buildHowItWorks())
        root.add(Box.createVerticalGlue())

        add(JBScrollPane(root).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
        wireListeners()
    }

    // ── Section label ─────────────────────────────────────────────────────────

    private fun buildSectionLabel(text: String): JBLabel =
        JBLabel(text).apply {
            font       = JBUI.Fonts.label(9f).asBold()
            foreground = JBColor(Color(0x6B7280), Color(0x9CA3AF))
            alignmentX = Component.LEFT_ALIGNMENT
        }

    // ── Architecture cards ────────────────────────────────────────────────────

    private fun buildArchCards(): JPanel {
        val grid = JPanel(GridLayout(2, 2, 8, 8)).apply {
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 120)
        }
        architectures.forEach { (name, icon, desc) ->
            val card = buildArchCard(name, icon, desc, name == selectedArchitecture)
            archCards[name] = card
            grid.add(card)
        }
        return grid
    }

    private fun buildArchCard(name: String, icon: String, desc: String, selected: Boolean): JPanel {
        val card = JPanel(BorderLayout(8, 2)).apply {
            isOpaque = true
            cursor   = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            applyCardStyle(this, selected)
        }

        val iconLabel = JBLabel(icon).apply { font = font.deriveFont(16f) }
        val textCol   = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        textCol.add(JBLabel(name).apply {
            font       = JBUI.Fonts.label(11f).asBold()
            foreground = if (selected) JBColor(Color(0x2563EB), Color(0x93C5FD)) else UIUtil.getLabelForeground()
        })
        textCol.add(JBLabel(desc).apply {
            font       = JBUI.Fonts.label(9f)
            foreground = JBColor.GRAY
        })

        card.border = JBUI.Borders.empty(8, 10)
        card.add(iconLabel, BorderLayout.WEST)
        card.add(textCol,   BorderLayout.CENTER)
        return card
    }

    private fun applyCardStyle(card: JPanel, selected: Boolean) {
        card.background = if (selected)
            JBColor(Color(0xEFF6FF), Color(0x1E3A5F))
        else
            UIUtil.getPanelBackground()
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                if (selected) JBColor(Color(0x3B82F6), Color(0x60A5FA)) else JBColor.border(),
                if (selected) 2 else 1, true
            ),
            JBUI.Borders.empty(8, 10)
        )
    }

    // ── Action row ────────────────────────────────────────────────────────────

    private fun buildActionRow(): JPanel {
        val row = JPanel(GridLayout(1, 2, 8, 0)).apply {
            isOpaque    = false
            alignmentX  = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 38)
        }

        analyzeButton.apply {
            font             = JBUI.Fonts.label(12f).asBold()
            background       = JBColor(Color(0x2563EB), Color(0x1D4ED8))
            foreground       = Color.WHITE
            isFocusPainted   = false
            isContentAreaFilled = true
            border           = JBUI.Borders.empty(8, 14)
            cursor           = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        viewReportButton.apply {
            font           = JBUI.Fonts.label(12f)
            isFocusPainted = false
            border         = JBUI.Borders.empty(8, 14)
            cursor         = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        row.add(analyzeButton)
        row.add(viewReportButton)
        return row
    }

    // ── Summary mini cards ────────────────────────────────────────────────────

    private fun buildSummaryCards(): JPanel {
        val grid = JPanel(GridLayout(1, 4, 8, 0)).apply {
            isOpaque    = false
            alignmentX  = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 72)
        }
        grid.add(scoreCard)
        grid.add(violationsCard)
        grid.add(filesCard)
        grid.add(fixesCard)
        return grid
    }

    private fun buildMiniCard(value: String, label: String, accent: Color): JPanel {
        val card = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIUtil.getPanelBackground()
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(8, 10)
            )
        }
        val valLabel = JBLabel(value).apply {
            font       = JBUI.Fonts.label(20f).asBold()
            foreground = accent
            alignmentX = Component.LEFT_ALIGNMENT
        }
        val lbl = JBLabel(label).apply {
            font       = JBUI.Fonts.label(9f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        }
        // Tag value label so we can find and update it later
        valLabel.name = "value"
        card.add(valLabel)
        card.add(Box.createVerticalStrut(2))
        card.add(lbl)
        return card
    }

    private fun updateMiniCard(card: JPanel, value: String) {
        for (comp in card.components) {
            if (comp is JBLabel && comp.name == "value") {
                comp.text = value
                break
            }
        }
        card.revalidate()
        card.repaint()
    }

    // ── How it works ──────────────────────────────────────────────────────────

    private fun buildHowItWorks(): JPanel {
        val panel = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(10, 12)
            )
        }
        val steps = listOf(
            "1️⃣  Select your architecture pattern above",
            "2️⃣  Click  Analyze Code Quality",
            "3️⃣  SpringForge scans all Java files via PSI",
            "4️⃣  ML models classify anti-patterns & score quality",
            "5️⃣  Gemini AI generates fix suggestions",
            "6️⃣  Full report opens in a popup — print or close"
        )
        steps.forEach { step ->
            panel.add(JBLabel(step).apply {
                font       = JBUI.Fonts.label(11f)
                foreground = UIUtil.getLabelForeground()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            panel.add(Box.createVerticalStrut(4))
        }
        return panel
    }

    // ── Wire listeners ────────────────────────────────────────────────────────

    private fun wireListeners() {
        // Architecture card selection
        architectures.forEach { (name, _, _) ->
            archCards[name]?.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = selectArchitecture(name)
            })
        }

        // Analyze
        analyzeButton.addActionListener { runAnalysis() }

        // View last report
        viewReportButton.addActionListener {
            val r = lastResult ?: return@addActionListener
            showReportDialog(r, lastFixes)
        }
    }

    private fun selectArchitecture(name: String) {
        selectedArchitecture = name
        architectures.forEach { (n, _, _) ->
            archCards[n]?.let { card -> applyCardStyle(card, n == name) }
        }
        // Update bold label foreground inside card
        archCards.forEach { (n, card) ->
            card.components.filterIsInstance<JPanel>().firstOrNull()?.let { col ->
                col.components.filterIsInstance<JBLabel>().firstOrNull()?.let { lbl ->
                    lbl.foreground = if (n == name)
                        JBColor(Color(0x2563EB), Color(0x93C5FD))
                    else
                        UIUtil.getLabelForeground()
                }
            }
        }
        revalidate(); repaint()
    }

    // =========================================================================
    //  Analysis workflow
    // =========================================================================

    private fun runAnalysis() {
        analyzeButton.isEnabled    = false
        viewReportButton.isEnabled = false
        progressBar.isVisible      = true
        setStatus("Scanning project files…", JBColor.GRAY)

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "SpringForge: Analyzing Code Quality…", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Step 1: PSI extraction
                    indicator.text     = "Scanning Java files…"
                    indicator.fraction = 0.10
                    setStatus("Extracting PSI features…", JBColor.GRAY)

                    val files = ReadAction.compute<List<FileFeatureModel>, Throwable> {
                        PsiFeatureExtractor.extractAllFiles(project, selectedArchitecture)
                    }

                    if (files.isEmpty()) {
                        setStatus("❌ No Java files found.", JBColor(Color(0xEF4444), Color(0xF87171)))
                        resetButtons(); return
                    }

                    setStatus("Found ${files.size} Java files — running ML models…", JBColor.GRAY)

                    // Step 2: ML analysis
                    indicator.text     = "Running ML models…"
                    indicator.fraction = 0.40
                    val result = MLServiceClient.analyzeProjectFull(files)

                    setStatus("ML complete — calling Gemini for AI fixes…", JBColor.GRAY)
                    indicator.fraction = 0.65

                    // Step 3: AI fixes
                    var fixes: ProjectFixResult? = null
                    if (result.anti_patterns.isNotEmpty()) {
                        indicator.text = "Generating AI fix suggestions…"
                        try {
                            fixes = MLServiceClient.generateProjectFixes(result)
                        } catch (_: Exception) { /* Gemini optional */ }
                    }

                    indicator.fraction = 1.0
                    lastResult = result
                    lastFixes  = fixes

                    // Step 4: Update summary cards + show report
                    ApplicationManager.getApplication().invokeLater {
                        val scoreStr = "${result.overall_score.roundToInt()}/100"
                        updateMiniCard(scoreCard,      scoreStr)
                        updateMiniCard(violationsCard, result.total_violations.toString())
                        updateMiniCard(filesCard,      result.total_files_analyzed.toString())
                        updateMiniCard(fixesCard,      fixes?.total_fixes?.toString() ?: "0")

                        val emoji = when {
                            result.overall_score >= 90 -> "🟢"
                            result.overall_score >= 75 -> "🟢"
                            result.overall_score >= 60 -> "🟠"
                            result.overall_score >= 40 -> "🔴"
                            else                       -> "🔴"
                        }
                        setStatus("$emoji Analysis complete — ${result.overall_display}", JBColor(Color(0x10B981), Color(0x34D399)))
                        resetButtons()
                        viewReportButton.isEnabled = true
                        showReportDialog(result, fixes)
                    }

                } catch (ex: Exception) {
                    setStatus("❌ ${ex.message}", JBColor(Color(0xEF4444), Color(0xF87171)))
                    resetButtons()
                    ApplicationManager.getApplication().invokeLater {
                        JOptionPane.showMessageDialog(
                            this@QualityAssurancePanel,
                            "Analysis failed:\n${ex.message}\n\nMake sure the ML Service is running on port 8081.\nStart: uvicorn app.main:app --port 8081 --reload",
                            "SpringForge — Analysis Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun setStatus(msg: String, color: Color) {
        ApplicationManager.getApplication().invokeLater {
            statusLabel.text       = msg
            statusLabel.foreground = color
            progressBar.isVisible  = msg.contains("…") || msg.contains("Scanning") || msg.contains("Running") || msg.contains("Gemini") || msg.contains("Extracting")
        }
    }

    private fun resetButtons() {
        ApplicationManager.getApplication().invokeLater {
            analyzeButton.isEnabled = true
            progressBar.isVisible   = false
        }
    }

    // =========================================================================
    //  Report popup dialog
    // =========================================================================

    fun showReportDialog(result: CombinedAnalysisResult, fixes: ProjectFixResult?) {
        val dialog = QualityReportDialog(project, result, fixes)
        dialog.show()
    }
}

// =============================================================================
//  QualityReportDialog — full-screen popup with styled report + Print + Close
// =============================================================================

class QualityReportDialog(
    private val project : Project,
    private val result  : CombinedAnalysisResult,
    private val fixes   : ProjectFixResult?
) {

    fun show() {
        val frame = JFrame("SpringForge — Code Quality Report").apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            preferredSize         = Dimension(1000, 760)
            minimumSize           = Dimension(700, 500)
        }

        val root = JPanel(BorderLayout()).apply { background = UIUtil.getPanelBackground() }

        // ── Header bar ────────────────────────────────────────────────────────
        root.add(buildHeader(frame), BorderLayout.NORTH)

        // ── Tabbed report body ────────────────────────────────────────────────
        root.add(buildBody(), BorderLayout.CENTER)

        // ── Bottom button bar ─────────────────────────────────────────────────
        root.add(buildFooter(frame), BorderLayout.SOUTH)

        frame.contentPane = root
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(frame: JFrame): JPanel {
        val panel = JPanel(BorderLayout(12, 0)).apply {
            background = JBColor(Color(0x1E293B), Color(0x0F172A))
            border     = JBUI.Borders.empty(14, 20, 14, 20)
        }

        val left = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        left.add(JLabel("SpringForge Code Quality Report").apply {
            font       = Font("Monospaced", Font.BOLD, 16)
            foreground = Color.WHITE
        })
        left.add(JLabel("${result.architecture_pattern.uppercase()} architecture · ${result.analysis_date}").apply {
            font       = Font("Monospaced", Font.PLAIN, 11)
            foreground = Color(0x94A3B8)
        })

        val scorePanel = buildHeaderScoreBadge()

        panel.add(left,        BorderLayout.WEST)
        panel.add(scorePanel,  BorderLayout.EAST)
        return panel
    }

    private fun buildHeaderScoreBadge(): JPanel {
        val score = result.overall_score.roundToInt()
        val (bg, label) = when {
            score >= 90 -> Color(0x065F46) to "EXCELLENT"
            score >= 75 -> Color(0x14532D) to "GOOD"
            score >= 60 -> Color(0x78350F) to "FAIR"
            score >= 40 -> Color(0x7F1D1D) to "POOR"
            else        -> Color(0x450A0A) to "CRITICAL"
        }
        val panel = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = bg
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color(0x334155), 1, true),
                JBUI.Borders.empty(8, 16)
            )
        }
        panel.add(JLabel("$score").apply {
            font       = Font("Monospaced", Font.BOLD, 28)
            foreground = Color.WHITE
            alignmentX = Component.CENTER_ALIGNMENT
        })
        panel.add(JLabel("$label / 100").apply {
            font       = Font("Monospaced", Font.PLAIN, 10)
            foreground = Color(0xD1FAE5)
            alignmentX = Component.CENTER_ALIGNMENT
        })
        return panel
    }

    // ── Body (tabs) ───────────────────────────────────────────────────────────

    private fun buildBody(): JTabbedPane {
        val tabs = JTabbedPane().apply {
            tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
            font            = JBUI.Fonts.label(12f)
        }

        tabs.addTab("📊 Overview",          buildOverviewTab())
        tabs.addTab("⚠️ Violations",         buildViolationsTab())
        tabs.addTab("📁 File Details",       buildFileDetailsTab())
        tabs.addTab("🤖 AI Fix Suggestions", buildAIFixesTab())
        tabs.addTab("📈 Metrics",            buildMetricsTab())
        tabs.addTab("📋 Full Report",        buildFullReportTab())

        return tabs
    }

    // ── TAB: Overview ─────────────────────────────────────────────────────────

    private fun buildOverviewTab(): JComponent {
        val panel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(16)
        }

        // Score bar chart by layer
        panel.add(sectionTitle("LAYER QUALITY SCORES"))
        panel.add(Box.createVerticalStrut(10))

        result.layer_scores.forEach { ls ->
            panel.add(buildLayerScoreRow(ls.layer, ls.mean_score, ls.quality_emoji, ls.quality_label))
            panel.add(Box.createVerticalStrut(6))
        }

        panel.add(Box.createVerticalStrut(6))
        panel.add(buildDivider())
        panel.add(Box.createVerticalStrut(6))

        // Overall bar
        panel.add(buildLayerScoreRow("Overall Project", result.overall_score, "", result.overall_label, bold = true))
        panel.add(Box.createVerticalStrut(20))

        // Key metrics grid
        panel.add(sectionTitle("KEY METRICS"))
        panel.add(Box.createVerticalStrut(10))
        panel.add(buildMetricsGrid())
        panel.add(Box.createVerticalStrut(20))

        // Quality interpretation legend
        panel.add(sectionTitle("QUALITY INTERPRETATION"))
        panel.add(Box.createVerticalStrut(8))
        panel.add(buildLegend())
        panel.add(Box.createVerticalGlue())

        return JBScrollPane(panel).apply { border = JBUI.Borders.empty() }
    }

    private fun buildLayerScoreRow(
        name  : String,
        score : Double,
        emoji : String,
        label : String,
        bold  : Boolean = false
    ): JPanel {
        val row = JPanel(BorderLayout(12, 0)).apply {
            isOpaque    = false
            maximumSize = Dimension(Int.MAX_VALUE, 26)
        }

        val nameLabel = JLabel("${emoji.ifBlank { "  " }} ${name.replaceFirstChar { it.uppercase() }}").apply {
            font       = if (bold) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(11f)
            preferredSize = Dimension(180, 20)
        }

        val barColor = when {
            score >= 75 -> JBColor(Color(0x10B981), Color(0x34D399))
            score >= 60 -> JBColor(Color(0xF59E0B), Color(0xFBBF24))
            else        -> JBColor(Color(0xEF4444), Color(0xF87171))
        }

        val barPanel = JPanel(BorderLayout()).apply {
            isOpaque    = false
            preferredSize = Dimension(300, 16)
        }
        val filled  = JPanel().apply {
            background  = barColor
            preferredSize = Dimension((score / 100.0 * 300).toInt().coerceIn(4, 300), 16)
        }
        val empty = JPanel().apply { isOpaque = false }
        val barInner = JPanel(BorderLayout()).apply { isOpaque = false }
        barInner.add(filled, BorderLayout.WEST)
        barInner.add(empty,  BorderLayout.CENTER)
        barPanel.add(barInner, BorderLayout.CENTER)

        val scoreLabel = JLabel("${score.roundToInt()}/100  $label").apply {
            font      = if (bold) JBUI.Fonts.label(12f).asBold() else JBUI.Fonts.label(11f)
            preferredSize = Dimension(130, 20)
        }

        row.add(nameLabel,  BorderLayout.WEST)
        row.add(barPanel,   BorderLayout.CENTER)
        row.add(scoreLabel, BorderLayout.EAST)
        return row
    }

    private fun buildMetricsGrid(): JPanel {
        val grid = JPanel(GridLayout(2, 3, 10, 8)).apply { isOpaque = false }
        val metrics = listOf(
            Triple("Files Analyzed",      result.total_files_analyzed.toString(), "📁"),
            Triple("Violations Found",    result.total_violations.toString(),     "⚠️"),
            Triple("Files with Issues",   result.files_with_violations.toString(),"🔍"),
            Triple("Clean Files",         result.clean_files.size.toString(),     "✅"),
            Triple("Avg LOC",             result.avg_loc.roundToInt().toString(), "📏"),
            Triple("Projected Score",     "${result.projected_score_after_fixes.roundToInt()}/100", "📈")
        )
        metrics.forEach { (label, value, icon) ->
            val card = JPanel().apply {
                layout     = BoxLayout(this, BoxLayout.Y_AXIS)
                background = UIUtil.getPanelBackground()
                border     = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBColor.border(), 1, true),
                    JBUI.Borders.empty(10, 12)
                )
            }
            card.add(JLabel("$icon  $value").apply {
                font       = JBUI.Fonts.label(16f).asBold()
                alignmentX = Component.LEFT_ALIGNMENT
            })
            card.add(JLabel(label).apply {
                font       = JBUI.Fonts.label(9f)
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
            grid.add(card)
        }
        return grid
    }

    private fun buildLegend(): JPanel {
        val panel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(10, 14)
            )
        }
        listOf(
            "🟢 90–100 : Excellent — Production ready, follows all best practices",
            "🟢 75–89  : Good      — Minor improvements needed",
            "🟠 60–74  : Fair      — Several issues require attention",
            "🔴 40–59  : Poor      — Significant refactoring recommended",
            "🔴  0–39  : Critical  — Immediate action required"
        ).forEach { line ->
            panel.add(JLabel(line).apply { font = JBUI.Fonts.label(11f) })
            panel.add(Box.createVerticalStrut(3))
        }
        return panel
    }

    // ── TAB: Violations ───────────────────────────────────────────────────────

    private fun buildViolationsTab(): JComponent {
        val panel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(16)
        }

        if (result.anti_patterns.isEmpty()) {
            panel.add(Box.createVerticalStrut(40))
            panel.add(JLabel("✅  No architectural violations detected!", SwingConstants.CENTER).apply {
                font       = JBUI.Fonts.label(16f).asBold()
                foreground = JBColor(Color(0x10B981), Color(0x34D399))
                alignmentX = Component.CENTER_ALIGNMENT
            })
            panel.add(JLabel("Your project follows ${result.architecture_pattern} best practices.").apply {
                foreground = JBColor.GRAY; alignmentX = Component.CENTER_ALIGNMENT
            })
        } else {
            val groups = result.anti_patterns.groupBy { it.severity }
            listOf("CRITICAL", "HIGH", "MEDIUM", "LOW").forEach { sev ->
                groups[sev]?.let { list ->
                    val (icon, bg) = when (sev) {
                        "CRITICAL" -> "🔴" to JBColor(Color(0xFEF2F2), Color(0x2D0A0A))
                        "HIGH"     -> "🟠" to JBColor(Color(0xFFFBEB), Color(0x2D1B00))
                        "MEDIUM"   -> "🟡" to JBColor(Color(0xFEFCE8), Color(0x2D2500))
                        else       -> "🟢" to UIUtil.getPanelBackground()
                    }
                    panel.add(sectionTitle("$icon $sev SEVERITY (${list.size})"))
                    panel.add(Box.createVerticalStrut(8))
                    list.forEach { ap ->
                        panel.add(buildViolationCard(ap, bg))
                        panel.add(Box.createVerticalStrut(8))
                    }
                    panel.add(Box.createVerticalStrut(8))
                }
            }
        }
        panel.add(Box.createVerticalGlue())
        return JBScrollPane(panel).apply { border = JBUI.Borders.empty() }
    }

    private fun buildViolationCard(ap: org.springforge.qualityassurance.model.AntiPatternDetail, bg: Color): JPanel {
        val card = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = bg
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(12, 14)
            )
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        val title = ap.type.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        card.add(JLabel("$title  [${ap.severity}]").apply {
            font       = JBUI.Fonts.label(12f).asBold()
            alignmentX = Component.LEFT_ALIGNMENT
        })
        card.add(Box.createVerticalStrut(4))
        card.add(JLabel("Layer: ${ap.affected_layer}   |   Confidence: ${(ap.confidence * 100).roundToInt()}%   |   Files: ${ap.files.size}").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })
        card.add(Box.createVerticalStrut(6))
        card.add(JLabel("<html><b>Problem:</b> ${ap.description}</html>").apply {
            font       = JBUI.Fonts.label(11f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        card.add(Box.createVerticalStrut(4))
        card.add(JLabel("<html><b>Fix:</b> ${ap.recommendation}</html>").apply {
            font       = JBUI.Fonts.label(11f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        card.add(Box.createVerticalStrut(6))
        val filesText = ap.files.take(5).joinToString("  •  ") +
                        if (ap.files.size > 5) "  +${ap.files.size - 5} more" else ""
        card.add(JLabel("📄 $filesText").apply {
            font       = Font("Monospaced", Font.PLAIN, 10)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })
        return card
    }

    // ── TAB: File Details ─────────────────────────────────────────────────────

    private fun buildFileDetailsTab(): JComponent {
        val columns = arrayOf("File", "Layer", "Score", "Label", "Issues")
        val data    = result.files.sortedBy { it.quality_score }.map { f ->
            arrayOf(
                f.file_name,
                f.layer.replaceFirstChar { it.uppercase() },
                "${f.quality_score.roundToInt()}/100",
                "${f.quality_emoji} ${f.quality_label}",
                if (f.issues.isEmpty()) "✅ Clean" else f.issues.joinToString(", ")
            )
        }.toTypedArray()

        val table = object : javax.swing.JTable(data, columns) {
            override fun isCellEditable(r: Int, c: Int) = false
        }.apply {
            font            = JBUI.Fonts.label(11f)
            rowHeight       = 26
            showHorizontalLines = true
            showVerticalLines   = false
            gridColor           = JBColor.border()
            tableHeader.font    = JBUI.Fonts.label(11f).asBold()
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            columnModel.getColumn(0).preferredWidth = 220
            columnModel.getColumn(1).preferredWidth = 90
            columnModel.getColumn(2).preferredWidth = 70
            columnModel.getColumn(3).preferredWidth = 120
            columnModel.getColumn(4).preferredWidth = 280
        }

        // Color rows by score
        table.setDefaultRenderer(Object::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: javax.swing.JTable, value: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                val c = super.getTableCellRendererComponent(t, value, sel, foc, row, col)
                if (!sel) {
                    val score = data[row][2].toString().removeSuffix("/100").toIntOrNull() ?: 50
                    c.background = when {
                        score >= 75 -> JBColor(Color(0xF0FDF4), Color(0x052E16))
                        score >= 60 -> UIUtil.getTableBackground()
                        else        -> JBColor(Color(0xFFF1F2), Color(0x2D0A0A))
                    }
                }
                return c
            }
        })

        val panel = JPanel(BorderLayout()).apply { isOpaque = false; border = JBUI.Borders.empty(8) }
        panel.add(JBScrollPane(table), BorderLayout.CENTER)

        // Summary row
        val summaryRow = JPanel(FlowLayout(FlowLayout.LEFT, 16, 4)).apply { isOpaque = false }
        val critCount  = result.files.count { it.quality_score < 40 }
        val goodCount  = result.files.count { it.quality_score >= 75 }
        summaryRow.add(JLabel("Total: ${result.files.size}  |  Good: $goodCount  |  Critical: $critCount  |  Clean: ${result.clean_files.size}").apply {
            font = JBUI.Fonts.label(10f); foreground = JBColor.GRAY
        })
        panel.add(summaryRow, BorderLayout.SOUTH)
        return panel
    }

    // ── TAB: AI Fix Suggestions ───────────────────────────────────────────────

    private fun buildAIFixesTab(): JComponent {
        val panel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(16)
        }

        if (fixes == null || fixes.suggestions.isEmpty()) {
            panel.add(Box.createVerticalStrut(40))
            panel.add(JLabel(
                if (fixes == null) "⏳  AI fix suggestions unavailable (Gemini not reachable)"
                else               "✅  No violations — no fixes needed!"
            ).apply {
                font = JBUI.Fonts.label(13f); alignmentX = Component.CENTER_ALIGNMENT
            })
        } else {
            panel.add(sectionTitle("🤖 GEMINI AI FIX SUGGESTIONS  (${fixes.suggestions.size} fixes)"))
            panel.add(Box.createVerticalStrut(12))

            fixes.suggestions.sortedBy { it.impact_points }.forEach { fix ->
                panel.add(buildFixCard(fix))
                panel.add(Box.createVerticalStrut(12))
            }
        }
        panel.add(Box.createVerticalGlue())
        return JBScrollPane(panel).apply { border = JBUI.Borders.empty() }
    }

    private fun buildFixCard(fix: org.springforge.qualityassurance.model.FixSuggestion): JPanel {
        val card = JPanel().apply {
            layout     = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIUtil.getPanelBackground()
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor(Color(0x8B5CF6), Color(0x7C3AED)), 1, true),
                JBUI.Borders.empty(12, 14)
            )
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        val title = fix.anti_pattern.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        card.add(JLabel("$title  [${fix.severity}]  ${if (fix.ai_powered) "🤖 AI-Powered" else "📖 Static"}").apply {
            font       = JBUI.Fonts.label(12f).asBold()
            alignmentX = Component.LEFT_ALIGNMENT
        })
        card.add(Box.createVerticalStrut(4))
        card.add(JLabel("Layer: ${fix.layer}   |   Impact: ${fix.impact_points} pts").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })

        if (fix.problem.isNotBlank()) {
            card.add(Box.createVerticalStrut(8))
            card.add(JLabel("<html><b>Problem:</b> ${fix.problem}</html>").apply {
                font = JBUI.Fonts.label(11f); alignmentX = Component.LEFT_ALIGNMENT
            })
        }

        if (fix.recommendation.isNotBlank()) {
            card.add(Box.createVerticalStrut(6))
            card.add(JLabel("<html><b>💡 Recommendation:</b> ${fix.recommendation}</html>").apply {
                font = JBUI.Fonts.label(11f); alignmentX = Component.LEFT_ALIGNMENT
            })
        }

        if (fix.before_code.isNotBlank()) {
            card.add(Box.createVerticalStrut(8))
            card.add(JLabel("🔧 Example Fix:").apply { font = JBUI.Fonts.label(10f).asBold(); alignmentX = Component.LEFT_ALIGNMENT })
            card.add(Box.createVerticalStrut(4))
            card.add(buildCodeBlock("// ❌ BEFORE\n${fix.before_code}\n\n// ✅ AFTER\n${fix.after_code}"))
        }

        if (fix.gemini_fix.isNotBlank()) {
            card.add(Box.createVerticalStrut(8))
            card.add(JLabel(if (fix.ai_powered) "🤖 Gemini AI Analysis:" else "📖 Fix Guidance:").apply { font = JBUI.Fonts.label(10f).asBold(); alignmentX = Component.LEFT_ALIGNMENT })
            card.add(Box.createVerticalStrut(4))
            card.add(buildCodeBlock(fix.gemini_fix))
        }

        val filesStr = fix.files.joinToString("  •  ")
        if (filesStr.isNotBlank()) {
            card.add(Box.createVerticalStrut(6))
            card.add(JLabel("📄 $filesStr").apply {
                font       = Font("Monospaced", Font.PLAIN, 10)
                foreground = JBColor.GRAY
                alignmentX = Component.LEFT_ALIGNMENT
            })
        }
        return card
    }

    private fun buildCodeBlock(code: String): JPanel {
        val area = JTextArea(code).apply {
            isEditable    = false
            font          = Font("Monospaced", Font.PLAIN, 10)
            background    = JBColor(Color(0xF8FAFC), Color(0x0F172A))
            foreground    = JBColor(Color(0x1E293B), Color(0xE2E8F0))
            lineWrap      = true
            wrapStyleWord = true
            border        = JBUI.Borders.empty(6, 8)
            // No row cap — show every line of the Gemini/code content
        }
        val wrapper = JPanel(BorderLayout()).apply {
            border = BorderFactory.createLineBorder(JBColor.border(), 1, true)
            add(area, BorderLayout.CENTER)
            // No maximumSize height cap — let it expand to show full content
        }
        return wrapper
    }

    // ── TAB: Metrics ──────────────────────────────────────────────────────────

    private fun buildMetricsTab(): JComponent {
        val panel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(16)
        }

        panel.add(sectionTitle("CODE HEALTH METRICS"))
        panel.add(Box.createVerticalStrut(10))

        val metricRows = listOf(
            "Architecture Pattern"       to result.architecture_pattern.uppercase(),
            "Total Files Analyzed"       to result.total_files_analyzed.toString(),
            "Files with Violations"      to result.files_with_violations.toString(),
            "Total Issues Found"         to result.total_violations.toString(),
            "Clean Files"                to result.clean_files.size.toString(),
            "Average File Size (LOC)"    to result.avg_loc.roundToInt().toString(),
            "Avg Cross-Layer Deps"       to "%.2f".format(result.avg_cross_layer_deps),
            "Projected Score After Fixes" to "${result.projected_score_after_fixes.roundToInt()}/100"
        )
        panel.add(buildMetricTable(metricRows))
        panel.add(Box.createVerticalStrut(20))

        panel.add(sectionTitle("LAYER BREAKDOWN"))
        panel.add(Box.createVerticalStrut(10))

        val layerRows = result.layer_scores.map { ls ->
            "${ls.layer.replaceFirstChar { it.uppercase() }} Layer" to
                    "${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji} ${ls.quality_label}  (${ls.file_count} files)"
        }
        panel.add(buildMetricTable(layerRows))
        panel.add(Box.createVerticalStrut(20))

        panel.add(sectionTitle("VIOLATION BREAKDOWN"))
        panel.add(Box.createVerticalStrut(10))
        val sevRows = listOf("CRITICAL", "HIGH", "MEDIUM", "LOW").mapNotNull { sev ->
            val count = result.anti_patterns.count { it.severity == sev }
            if (count > 0) "$sev Violations" to count.toString() else null
        }
        if (sevRows.isEmpty()) panel.add(JLabel("✅  No violations.").apply { alignmentX = Component.LEFT_ALIGNMENT })
        else panel.add(buildMetricTable(sevRows))

        panel.add(Box.createVerticalGlue())
        return JBScrollPane(panel).apply { border = JBUI.Borders.empty() }
    }

    private fun buildMetricTable(rows: List<Pair<String, String>>): JPanel {
        val panel = JPanel(GridLayout(rows.size, 2, 0, 0)).apply {
            border   = BorderFactory.createLineBorder(JBColor.border(), 1, true)
            isOpaque = false
            maximumSize = Dimension(Int.MAX_VALUE, rows.size * 28)
        }
        rows.forEachIndexed { i, (key, value) ->
            val bg = if (i % 2 == 0) UIUtil.getTableBackground() else UIUtil.getDecoratedRowColor()
            panel.add(JLabel("  $key").apply { background = bg; isOpaque = true; font = JBUI.Fonts.label(11f) })
            panel.add(JLabel("  $value").apply { background = bg; isOpaque = true; font = JBUI.Fonts.label(11f).asBold() })
        }
        return panel
    }

    // ── TAB: Full Text Report ─────────────────────────────────────────────────

    private fun buildFullReportTab(): JComponent {
        val reportText = buildFullReportText()
        val area = JBTextArea(reportText).apply {
            isEditable = false
            font       = Font("Monospaced", Font.PLAIN, 11)
            lineWrap   = false
            border     = JBUI.Borders.empty(10)
        }
        return JBScrollPane(area).apply { border = JBUI.Borders.empty() }
    }

    private fun buildFullReportText(): String {
        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════════════════════════════════════════════╗")
        sb.appendLine("║       SPRINGFORGE CODE QUALITY ANALYSIS REPORT                  ║")
        sb.appendLine("║          Complete System Analysis  +  AI Fix Suggestions        ║")
        sb.appendLine("╚══════════════════════════════════════════════════════════════════╝")
        sb.appendLine()
        sb.appendLine("Architecture : ${result.architecture_pattern.uppercase()}")
        sb.appendLine("Date         : ${result.analysis_date}")
        sb.appendLine("Files        : ${result.total_files_analyzed}")
        sb.appendLine("Score        : ${result.overall_display}")
        sb.appendLine("Violations   : ${result.total_violations}")
        sb.appendLine("AI Fixes     : ${fixes?.total_fixes ?: 0}")
        sb.appendLine()
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  LAYER QUALITY SCORES")
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        result.layer_scores.forEach { ls ->
            val bar = "█".repeat((ls.mean_score / 5).roundToInt().coerceIn(0, 20)) +
                      "░".repeat(20 - (ls.mean_score / 5).roundToInt().coerceIn(0, 20))
            sb.appendLine("  ${ls.layer.padEnd(16)} $bar  ${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji} ${ls.quality_label}")
        }
        sb.appendLine()
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  ARCHITECTURAL VIOLATIONS")
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        if (result.anti_patterns.isEmpty()) {
            sb.appendLine("  ✅ No architectural violations detected!")
        } else {
            listOf("CRITICAL", "HIGH", "MEDIUM", "LOW").forEach { sev ->
                result.anti_patterns.filter { it.severity == sev }.forEach { ap ->
                    sb.appendLine()
                    sb.appendLine("  [${ap.severity}] ${ap.type.replace("_", " ").uppercase()}")
                    sb.appendLine("  Layer      : ${ap.affected_layer}")
                    sb.appendLine("  Confidence : ${(ap.confidence * 100).roundToInt()}%")
                    sb.appendLine("  Files      : ${ap.files.joinToString(", ")}")
                    sb.appendLine("  Problem    : ${ap.description}")
                    sb.appendLine("  Fix        : ${ap.recommendation}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  FILE DETAILS  (worst → best)")
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        result.files.sortedBy { it.quality_score }.forEach { f ->
            sb.appendLine("  ${f.file_name.padEnd(40)} ${f.quality_display}")
            f.issues.forEach { issue -> sb.appendLine("    • $issue") }
        }
        sb.appendLine()
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  CLEAN FILES")
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        result.clean_files.forEach { sb.appendLine("  ✅ $it") }
        if (fixes != null && fixes.suggestions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("══════════════════════════════════════════════════════════════════")
            sb.appendLine("  🤖 AI FIX SUGGESTIONS  (Google Gemini)")
            sb.appendLine("══════════════════════════════════════════════════════════════════")
            fixes.suggestions.forEach { fix ->
                sb.appendLine()
                sb.appendLine("  [${fix.severity}] ${fix.anti_pattern.replace("_", " ").uppercase()}")
                sb.appendLine("  Layer  : ${fix.layer}  |  Impact: ${fix.impact_points} pts  |  AI: ${fix.ai_powered}")
                sb.appendLine("  Files  : ${fix.files.joinToString(", ")}")
                if (fix.problem.isNotBlank()) sb.appendLine("  ❗ Problem    : ${fix.problem}")
                if (fix.recommendation.isNotBlank()) sb.appendLine("  💡 Recommendation: ${fix.recommendation}")
                if (fix.gemini_fix.isNotBlank()) {
                    sb.appendLine()
                    if (fix.ai_powered) sb.appendLine("  🤖 Gemini AI Fix:") else sb.appendLine("  📖 Fix Guidance:")
                    fix.gemini_fix.lines().forEach { sb.appendLine("     $it") }
                }
                if (fix.before_code.isNotBlank()) {
                    sb.appendLine()
                    sb.appendLine("  🔧 Code Example:")
                    sb.appendLine("  // ❌ BEFORE")
                    fix.before_code.lines().forEach { sb.appendLine("     $it") }
                    sb.appendLine("  // ✅ AFTER")
                    fix.after_code.lines().forEach { sb.appendLine("     $it") }
                }
            }
        }
        sb.appendLine()
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  METRICS")
        sb.appendLine("══════════════════════════════════════════════════════════════════")
        sb.appendLine("  ${result.quality_summary}")
        sb.appendLine()
        sb.appendLine("  ${result.violation_summary}")
        sb.appendLine()
        sb.appendLine("  Projected Score After Fixes: ${result.projected_score_after_fixes.roundToInt()}/100")
        sb.appendLine()
        sb.appendLine("━".repeat(66))
        sb.appendLine("  Generated by SpringForge Code Quality Analyzer v2.1")
        if (fixes != null) sb.appendLine("  AI fixes powered by Google Gemini 2.5 Flash 🤖")
        return sb.toString()
    }

    // ── Footer (Print + Close) ────────────────────────────────────────────────

    private fun buildFooter(frame: JFrame): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = JBColor(Color(0x1E293B), Color(0x0F172A))
            border     = JBUI.Borders.empty(10, 20, 10, 20)
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
        left.add(JLabel("SpringForge v2.1  |  Powered by XGBoost + Gemini AI").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = Color(0x94A3B8)
        })

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }

        val printBtn  = JButton("🖨️  Print Report").apply {
            font             = JBUI.Fonts.label(12f)
            background       = JBColor(Color(0x334155), Color(0x1E293B))
            foreground       = Color.WHITE
            isFocusPainted   = false
            isContentAreaFilled = true
            border           = JBUI.Borders.empty(7, 16)
            cursor           = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val closeBtn  = JButton("✕  Close").apply {
            font             = JBUI.Fonts.label(12f).asBold()
            background       = JBColor(Color(0xDC2626), Color(0xB91C1C))
            foreground       = Color.WHITE
            isFocusPainted   = false
            isContentAreaFilled = true
            border           = JBUI.Borders.empty(7, 16)
            cursor           = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        printBtn.addActionListener { printReport(frame) }
        closeBtn.addActionListener { frame.dispose() }

        right.add(printBtn)
        right.add(closeBtn)

        panel.add(left,  BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    // ── Print ─────────────────────────────────────────────────────────────────

    private fun printReport(frame: JFrame) {
        val job = PrinterJob.getPrinterJob()
        job.setJobName("SpringForge Quality Report")
        val reportText = buildFullReportText()

        job.setPrintable(object : Printable {
            override fun print(g: Graphics, pf: PageFormat, page: Int): Int {
                val g2 = g as Graphics2D
                val font = Font("Monospaced", Font.PLAIN, 8)
                g2.font  = font
                val fm   = g2.fontMetrics
                val lh   = fm.height
                val x    = pf.imageableX.toFloat()
                val y0   = pf.imageableY.toFloat()
                val w    = pf.imageableWidth.toFloat()
                val h    = pf.imageableHeight.toFloat()
                val chPerLine = (w / fm.charWidth('M')).toInt()
                val linesPerPage = (h / lh).toInt()

                // Word-wrap to fit page width
                val lines = mutableListOf<String>()
                reportText.lines().forEach { line ->
                    if (line.length <= chPerLine) lines.add(line)
                    else {
                        var start = 0
                        while (start < line.length) {
                            lines.add(line.substring(start, minOf(start + chPerLine, line.length)))
                            start += chPerLine
                        }
                    }
                }

                val startLine = page * linesPerPage
                if (startLine >= lines.size) return Printable.NO_SUCH_PAGE

                g2.color = Color.BLACK
                var yPos = y0 + lh
                for (i in startLine until minOf(startLine + linesPerPage, lines.size)) {
                    g2.drawString(lines[i], x, yPos)
                    yPos += lh
                }
                return Printable.PAGE_EXISTS
            }
        })

        if (job.printDialog()) {
            try {
                job.print()
            } catch (ex: PrinterException) {
                JOptionPane.showMessageDialog(
                    frame, "Print failed: ${ex.message}", "Print Error", JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun sectionTitle(text: String): JLabel =
        JLabel(text).apply {
            font       = JBUI.Fonts.label(10f).asBold()
            foreground = JBColor(Color(0x6B7280), Color(0x9CA3AF))
            alignmentX = Component.LEFT_ALIGNMENT
        }

    private fun buildDivider(): JSeparator =
        JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) }
}