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
import org.springforge.feedback.ui.FeedbackDialog
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.model.AntiPatternDetail
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.FixSuggestion
import org.springforge.qualityassurance.model.ProjectFixResult
import org.springforge.qualityassurance.network.MLServiceClient
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterException
import java.awt.print.PrinterJob
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.border.EmptyBorder
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  DESIGN TOKENS  — single source of truth for all colors / radii / spacing
// ═══════════════════════════════════════════════════════════════════════════════

private object SF {
    // Surface hierarchy
    val bg         = JBColor(Color(0xF0F4F8), Color(0x0D1117))
    val surface    = JBColor(Color(0xFFFFFF), Color(0x161B22))
    val surfaceAlt = JBColor(Color(0xF6F8FA), Color(0x1C2128))
    val overlay    = JBColor(Color(0xEAEFF5), Color(0x21262D))

    // Text
    val textPrimary   = JBColor(Color(0x0D1117), Color(0xE6EDF3))
    val textSecondary = JBColor(Color(0x57606A), Color(0x8B949E))
    val textMuted     = JBColor(Color(0x8C959F), Color(0x6E7681))

    // Borders
    val border        = JBColor(Color(0xD0D7DE), Color(0x30363D))
    val borderStrong  = JBColor(Color(0xAFB8C1), Color(0x484F58))

    // Brand / accent
    val brand         = JBColor(Color(0x0969DA), Color(0x58A6FF))
    val brandBg       = JBColor(Color(0xDDF4FF), Color(0x0D2135))

    // Semantic — success / warning / danger
    val green         = JBColor(Color(0x1A7F37), Color(0x3FB950))
    val greenBg       = JBColor(Color(0xDCFCE7), Color(0x0D2A15))
    val greenBgRow    = JBColor(Color(0xF0FDF4), Color(0x071E0F))

    val amber         = JBColor(Color(0xBF8700), Color(0xD29922))
    val amberBg       = JBColor(Color(0xFFF8C5), Color(0x2B1D00))

    val red           = JBColor(Color(0xCF222E), Color(0xF85149))
    val redBg         = JBColor(Color(0xFFEBE9), Color(0x2A0A0A))
    val redBgRow      = JBColor(Color(0xFFF1F2), Color(0x1C0606))

    val purple        = JBColor(Color(0x8250DF), Color(0xD2A8FF))
    val purpleBg      = JBColor(Color(0xFBEFFF), Color(0x1D0E30))

    val orange        = JBColor(Color(0xBC4C00), Color(0xFFA657))

    // Header / dark band
    val headerBg      = JBColor(Color(0x0D1117), Color(0x010409))
    val headerBorder  = JBColor(Color(0x21262D), Color(0x21262D))

    // Code block
    val codeBg        = JBColor(Color(0xF6F8FA), Color(0x161B22))
    val codeFg        = JBColor(Color(0x1F2328), Color(0xE6EDF3))

    // Geometry
    const val RADIUS  = 8
    const val RADIUS_S = 5
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CUSTOM PRIMITIVES
// ═══════════════════════════════════════════════════════════════════════════════

/** Anti-aliased rounded border with configurable stroke thickness. */
private class RoundBorder(
    private val radius : Int,
    private val color  : Color,
    private val stroke : Float = 1f
) : AbstractBorder() {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, w: Int, h: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color  = color
        g2.stroke = BasicStroke(stroke)
        g2.draw(RoundRectangle2D.Float(stroke / 2, stroke / 2, w - stroke, h - stroke,
            radius.toFloat(), radius.toFloat()))
        g2.dispose()
    }
    override fun getBorderInsets(c: Component) =
        Insets(radius / 3 + 2, radius / 3 + 3, radius / 3 + 2, radius / 3 + 3)
}

/** Pill-shaped label badge. */
private class Badge(text: String, bg: Color, fg: Color = Color.WHITE) : JComponent() {
    private val txt = text
    private val bgC = bg
    private val fgC = fg
    init {
        isOpaque    = false
        preferredSize = run {
            val fm = getFontMetrics(Font("Monospaced", Font.BOLD, 10))
            Dimension(fm.stringWidth(txt) + 16, fm.height + 6)
        }
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bgC; g2.fillRoundRect(0, 0, width, height, height, height)
        g2.color = fgC; g2.font = Font("Monospaced", Font.BOLD, 10)
        val fm = g2.fontMetrics
        g2.drawString(txt, (width - fm.stringWidth(txt)) / 2, (height + fm.ascent - fm.descent) / 2)
        g2.dispose()
    }
}

/** Circular score ring drawn with Arc2D. */
private class ScoreRing(private val score: Int) : JComponent() {
    private val ringFg = when {
        score >= 90 -> SF.green
        score >= 75 -> SF.green
        score >= 60 -> SF.amber
        score >= 40 -> SF.orange
        else        -> SF.red
    }
    init { isOpaque = false; preferredSize = Dimension(96, 96) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val cx = width / 2; val cy = height / 2; val r = 38
        // Track ring
        g2.color  = SF.overlay
        g2.stroke = BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawOval(cx - r, cy - r, r * 2, r * 2)
        // Score arc
        g2.color  = ringFg
        g2.stroke = BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, 90, -(score * 360 / 100))
        // Score number
        g2.color = SF.textPrimary
        g2.font  = Font("Monospaced", Font.BOLD, 20)
        val fm1  = g2.fontMetrics
        val s    = "$score"
        g2.drawString(s, cx - fm1.stringWidth(s) / 2, cy + fm1.ascent / 2 - 1)
        // "/100" beneath
        g2.color = SF.textMuted
        g2.font  = Font("Monospaced", Font.PLAIN, 9)
        val fm2  = g2.fontMetrics
        val sub  = "/100"
        g2.drawString(sub, cx - fm2.stringWidth(sub) / 2, cy + 15)
        g2.dispose()
    }
}

/** Thin horizontal progress bar. */
private class ScoreBar(private val score: Double, private val w: Int = 0) : JComponent() {
    private val fill = when {
        score >= 75 -> SF.green
        score >= 60 -> SF.amber
        else        -> SF.red
    }
    init { isOpaque = false; preferredSize = Dimension(w.coerceAtLeast(60), 8) }
    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = SF.overlay; g2.fillRoundRect(0, 0, width, height, height, height)
        val fw = (score / 100.0 * width).toInt().coerceIn(height, width)
        g2.color = fill; g2.fillRoundRect(0, 0, fw, height, height, height)
        g2.dispose()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SHARED BUILDER HELPERS  (module-level so both classes can use them)
// ═══════════════════════════════════════════════════════════════════════════════

/** Card panel with rounded corners painted on a per-component basis. */
private fun sfCard(radius: Int = SF.RADIUS, bg: Color = SF.surface, extraPad: Insets = Insets(12, 14, 12, 14)): JPanel =
    object : JPanel() {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = bg; g2.fillRoundRect(0, 0, width, height, radius, radius)
            g2.dispose()
            super.paintComponent(g)
        }
    }.apply {
        isOpaque   = false
        border     = BorderFactory.createCompoundBorder(
            RoundBorder(radius, SF.border),
            JBUI.Borders.empty(extraPad.top, extraPad.left, extraPad.bottom, extraPad.right)
        )
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

/** Monospace label — section-header style */
private fun sectionLabel(text: String): JLabel = JLabel(text).apply {
    font       = Font("Monospaced", Font.BOLD, 12)
    foreground = SF.textMuted
    alignmentX = Component.LEFT_ALIGNMENT
}

/** Wrap a panel in a JBScrollPane with no decoration. */
private fun scrolled(p: JPanel): JBScrollPane = JBScrollPane(p).apply {
    border              = JBUI.Borders.empty()
    verticalScrollBar.unitIncrement = 14
}

/** Small monospaced chip */
private fun chip(text: String): JLabel = JLabel(text).apply {
    font       = Font("Monospaced", Font.PLAIN, 10)
    foreground = SF.textSecondary
    background = SF.surfaceAlt
    isOpaque   = true
    border     = BorderFactory.createCompoundBorder(
        RoundBorder(SF.RADIUS_S, SF.border),
        JBUI.Borders.empty(1, 6)
    )
}

/** Left-stripe accent panel used on violation / fix cards. */
private fun stripeCard(stripeColor: Color): JPanel {
    val card = sfCard(SF.RADIUS)
    card.layout = BorderLayout(0, 0)
    val stripe = JPanel().apply {
        background  = stripeColor
        preferredSize = Dimension(4, 0)
        isOpaque    = true
    }
    card.add(stripe, BorderLayout.WEST)
    return card
}

// ═══════════════════════════════════════════════════════════════════════════════
//  QualityAssurancePanel  — side-panel (architecture selector + action buttons)
// ═══════════════════════════════════════════════════════════════════════════════

class QualityAssurancePanel(private val project: Project) : JPanel() {

    // ── Data ──────────────────────────────────────────────────────────────────
    private val architectures = listOf(
        Triple("layered",            "Layered",            "Controller → Service → Repository"),
        Triple("mvc",                "MVC",                "Model-View-Controller pattern"),
        Triple("hexagonal",          "Hexagonal",          "Ports & Adapters / Onion"),
        Triple("clean_architecture", "Clean Architecture", "Dependency Rule enforced")
    )
    private var selectedArchitecture = "layered"
    private val archCards = mutableMapOf<String, JPanel>()

    // ── State ─────────────────────────────────────────────────────────────────
    private var lastResult: CombinedAnalysisResult? = null
    private var lastFixes : ProjectFixResult?       = null

    // ── Widgets ───────────────────────────────────────────────────────────────
    private val statusLabel = JLabel("Ready to analyze").apply {
        font = Font("Monospaced", Font.PLAIN, 11); foreground = SF.textSecondary
    }
    private val progressBar = JProgressBar().apply {
        isIndeterminate = true; isVisible = false
        preferredSize   = Dimension(Int.MAX_VALUE, 2); border = EmptyBorder(0, 0, 0, 0)
    }

    private val analyzeButton = JButton("⚡  Analyze Code Quality").apply {
        font             = Font("Monospaced", Font.BOLD, 12)
        background       = SF.brand; foreground = Color.WHITE
        isFocusPainted   = false; isContentAreaFilled = true; isOpaque = true
        border           = JBUI.Borders.empty(8, 14)
        cursor           = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }
    private val viewReportButton = JButton("📋  View Last Report").apply {
        font           = Font("Monospaced", Font.PLAIN, 12); isEnabled = false
        isFocusPainted = false; border = JBUI.Borders.empty(8, 14)
        cursor         = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // Mini summary tiles
    private val scoreCard      = miniTile("—",  "Score",     SF.brand)
    private val violationsCard = miniTile("—",  "Issues",    SF.red)
    private val filesCard      = miniTile("—",  "Files",     SF.green)
    private val fixesCard      = miniTile("—",  "AI Fixes",  SF.purple)

    init {
        layout     = BorderLayout()
        background = UIUtil.getPanelBackground()
        border     = JBUI.Borders.empty(14)
        buildUI()
    }

    // ── UI Construction ───────────────────────────────────────────────────────

    private fun buildUI() {
        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
        }
        fun gap(n: Int) = root.add(Box.createVerticalStrut(n))

        root.add(sectionLabel("ARCHITECTURE PATTERN")); gap(8)
        root.add(buildArchGrid()); gap(18)

        root.add(sectionLabel("ACTIONS")); gap(8)
        root.add(buildActionRow()); gap(4)
        root.add(progressBar.also { it.alignmentX = Component.LEFT_ALIGNMENT; it.maximumSize = Dimension(Int.MAX_VALUE, 2) }); gap(4)
        root.add(statusLabel.also { it.alignmentX = Component.LEFT_ALIGNMENT }); gap(18)

        root.add(sectionLabel("LAST ANALYSIS SUMMARY")); gap(8)
        root.add(buildSummaryTiles()); gap(18)

        root.add(sectionLabel("HOW IT WORKS")); gap(8)
        root.add(buildHowItWorks())

        root.add(Box.createVerticalGlue())
        add(scrolled(root), BorderLayout.CENTER)
        wireListeners()
    }

    // ── Architecture selector grid ────────────────────────────────────────────

    private fun buildArchGrid(): JPanel =
        JPanel(GridLayout(2, 2, 8, 8)).apply {
            isOpaque    = false; alignmentX  = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 112)
        }.also { grid ->
            architectures.forEach { (key, name, desc) ->
                val icon = mapOf("layered" to "🏗", "mvc" to "🔄",
                                 "hexagonal" to "⬡", "clean_architecture" to "◎")[key] ?: "◻"
                val card = buildArchCard(key, name, icon, desc, key == selectedArchitecture)
                archCards[key] = card; grid.add(card)
            }
        }

    private fun buildArchCard(key: String, name: String, icon: String, desc: String, selected: Boolean): JPanel {
        val isSelected = selected
        val card = object : JPanel(BorderLayout(6, 0)) {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = background; g2.fillRoundRect(0, 0, width, height, SF.RADIUS, SF.RADIUS)
                g2.dispose()
            }
        }.apply {
            isOpaque = false; cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        refreshCardStyle(card, key, name, desc, icon, isSelected)
        return card
    }

    private fun refreshCardStyle(card: JPanel, key: String, name: String, desc: String, icon: String, selected: Boolean) {
        card.removeAll()
        val accent = if (selected) SF.brand else SF.textMuted
        card.background = if (selected) SF.brandBg else SF.surface
        card.border = BorderFactory.createCompoundBorder(
            RoundBorder(SF.RADIUS, if (selected) SF.brand else SF.border, if (selected) 2f else 1f),
            JBUI.Borders.empty(8, 10)
        )
        // Icon
        card.add(JLabel(icon).apply { font = font.deriveFont(15f); foreground = accent }, BorderLayout.WEST)
        // Text column
        val col = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }
        col.add(JLabel(name).apply {
            font = Font("Monospaced", Font.BOLD, 11); foreground = if (selected) SF.brand else SF.textPrimary; this.name = "nameLabel"
        })
        col.add(JLabel(desc).apply { font = Font("Monospaced", Font.PLAIN, 9); foreground = SF.textSecondary })
        card.add(col, BorderLayout.CENTER)
        // Check mark
        if (selected) card.add(JLabel("✓").apply {
            font = Font("Monospaced", Font.BOLD, 12); foreground = SF.brand
        }, BorderLayout.EAST)
        card.revalidate(); card.repaint()
    }

    private fun wireCardHover(card: JPanel, key: String) {
        card.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (key != selectedArchitecture) card.background = SF.surfaceAlt; card.repaint()
            }
            override fun mouseExited(e: MouseEvent) {
                if (key != selectedArchitecture) card.background = SF.surface; card.repaint()
            }
        })
    }

    // ── Action row ────────────────────────────────────────────────────────────

    private fun buildActionRow(): JPanel = JPanel(GridLayout(1, 2, 8, 0)).apply {
        isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(Int.MAX_VALUE, 38)
        add(analyzeButton); add(viewReportButton)
    }

    // ── Summary tiles ─────────────────────────────────────────────────────────

    private fun buildSummaryTiles(): JPanel = JPanel(GridLayout(1, 4, 8, 0)).apply {
        isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(Int.MAX_VALUE, 68)
        add(scoreCard); add(violationsCard); add(filesCard); add(fixesCard)
    }

    private fun miniTile(value: String, label: String, accent: Color): JPanel {
        val tile = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = SF.surface; g2.fillRoundRect(0, 0, width, height, SF.RADIUS, SF.RADIUS); g2.dispose()
                super.paintComponent(g)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false
            border = BorderFactory.createCompoundBorder(RoundBorder(SF.RADIUS, SF.border), JBUI.Borders.empty(8, 10))
        }
        tile.add(JLabel(value).apply {
            font = Font("Monospaced", Font.BOLD, 18); foreground = accent; alignmentX = Component.LEFT_ALIGNMENT; name = "value"
        })
        tile.add(JLabel(label).apply {
            font = Font("Monospaced", Font.PLAIN, 9); foreground = SF.textMuted; alignmentX = Component.LEFT_ALIGNMENT
        })
        return tile
    }

    private fun updateTile(tile: JPanel, value: String) {
        (tile.components.firstOrNull { it is JLabel && (it as JLabel).name == "value" } as? JLabel)?.text = value
        tile.revalidate(); tile.repaint()
    }

    // ── How it works ──────────────────────────────────────────────────────────

    private fun buildHowItWorks(): JPanel {
        val card = sfCard(extraPad = Insets(12, 14, 12, 14))
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
        val steps = listOf(
            "1" to "Select architecture pattern above",
            "2" to "Click  Analyze Code Quality",
            "3" to "SpringForge scans all Java files via PSI",
            "4" to "ML models classify anti-patterns & score quality",
            "5" to "Gemini AI generates targeted fix suggestions",
            "6" to "Report opens — review, print or close"
        )
        steps.forEach { (num, text) ->
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 22) }
            row.add(Badge(num, SF.brand))
            row.add(JLabel(text).apply { font = Font("Monospaced", Font.PLAIN, 11); foreground = SF.textPrimary })
            card.add(row)
            if (num != "6") card.add(Box.createVerticalStrut(6))
        }
        return card
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun wireListeners() {
        architectures.forEach { (key, _, _) ->
            archCards[key]?.let { card ->
                wireCardHover(card, key)
                card.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) = selectArchitecture(key)
                })
            }
        }
        analyzeButton.addActionListener { runAnalysis() }
        viewReportButton.addActionListener { lastResult?.let { showReportDialog(it, lastFixes) } }
    }

    private fun selectArchitecture(key: String) {
        val prev = selectedArchitecture; selectedArchitecture = key
        architectures.forEach { (k, name, desc) ->
            val icon = mapOf("layered" to "🏗", "mvc" to "🔄",
                             "hexagonal" to "⬡", "clean_architecture" to "◎")[k] ?: "◻"
            archCards[k]?.let { refreshCardStyle(it, k, name, desc, icon, k == key) }
        }
        revalidate(); repaint()
    }

    // ── Analysis workflow (unchanged logic) ───────────────────────────────────

    private fun runAnalysis() {
        analyzeButton.isEnabled = false; viewReportButton.isEnabled = false
        progressBar.isVisible   = true; setStatus("Scanning project files…", SF.textSecondary)
        val archKey = selectedArchitecture
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "SpringForge: Analyzing…", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Scanning Java files…"; indicator.fraction = 0.10
                    setStatus("Extracting PSI features…", SF.textSecondary)
                    val files = ReadAction.compute<List<FileFeatureModel>, Throwable> {
                        PsiFeatureExtractor.extractAllFiles(project, archKey)
                    }
                    if (files.isEmpty()) {
                        setStatus("❌  No Java files found.", SF.red); resetButtons(); return
                    }
                    setStatus("Found ${files.size} files — running ML models…", SF.textSecondary)
                    indicator.text = "Running ML models…"; indicator.fraction = 0.40
                    val result = MLServiceClient.analyzeProjectFull(files)
                    setStatus("ML complete — calling Gemini…", SF.textSecondary); indicator.fraction = 0.65
                    var fixes: ProjectFixResult? = null
                    if (result.anti_patterns.isNotEmpty()) {
                        indicator.text = "Generating AI fix suggestions…"
                        try { fixes = MLServiceClient.generateProjectFixes(result) } catch (_: Exception) {}
                    }
                    indicator.fraction = 1.0; lastResult = result; lastFixes = fixes
                    ApplicationManager.getApplication().invokeLater {
                        updateTile(scoreCard,      "${result.overall_score.roundToInt()}/100")
                        updateTile(violationsCard, result.total_violations.toString())
                        updateTile(filesCard,      result.total_files_analyzed.toString())
                        updateTile(fixesCard,      fixes?.total_fixes?.toString() ?: "0")
                        val emoji = when { result.overall_score >= 75 -> "🟢"; result.overall_score >= 60 -> "🟠"; else -> "🔴" }
                        setStatus("$emoji  Analysis complete — ${result.overall_display}", SF.green)
                        resetButtons(); viewReportButton.isEnabled = true
                        showReportDialog(result, fixes) {
                            FeedbackDialog.showForModule(project, "quality-assurance", "Code Quality Analysis")
                        }
                    }
                } catch (ex: Exception) {
                    setStatus("❌  ${ex.message}", SF.red); resetButtons()
                    ApplicationManager.getApplication().invokeLater {
                        JOptionPane.showMessageDialog(this@QualityAssurancePanel,
                            "Analysis failed:\n${ex.message}\n\nMake sure the ML Service is running on port 8081.\nStart: uvicorn app.main:app --port 8081 --reload",
                            "SpringForge — Analysis Error", JOptionPane.ERROR_MESSAGE)
                    }
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun setStatus(msg: String, color: Color) {
        ApplicationManager.getApplication().invokeLater {
            statusLabel.text       = msg; statusLabel.foreground = color
            progressBar.isVisible  = msg.endsWith("…")
        }
    }
    private fun resetButtons() = ApplicationManager.getApplication().invokeLater {
        analyzeButton.isEnabled = true; progressBar.isVisible = false
    }

    fun showReportDialog(result: CombinedAnalysisResult, fixes: ProjectFixResult?, onClose: (() -> Unit)? = null) =
        QualityReportDialog(project, result, fixes).show(onClose)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  QualityReportDialog  — redesigned, fully responsive, tabbed popup
// ═══════════════════════════════════════════════════════════════════════════════

class QualityReportDialog(
    private val project : Project,
    private val result  : CombinedAnalysisResult,
    private val fixes   : ProjectFixResult?
) {

    fun show(onClose: (() -> Unit)? = null) {
        val frame = JFrame("SpringForge — Code Quality Report").apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            preferredSize = Dimension(1080, 800)
            minimumSize   = Dimension(740, 520)
        }
        frame.contentPane = buildRoot(frame)
        frame.pack()
        frame.setLocationRelativeTo(null)

        // Re-layout on resize to keep everything proportional
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                frame.contentPane.revalidate(); frame.contentPane.repaint()
            }
        })

        // Trigger callback (e.g. feedback dialog) when the report window is closed
        if (onClose != null) {
            frame.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    onClose()
                }
            })
        }

        frame.isVisible = true
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    private fun buildRoot(frame: JFrame): JPanel = JPanel(BorderLayout(0, 0)).apply {
        background = SF.bg
        add(buildHeader(),          BorderLayout.NORTH)
        add(buildTabPane(),         BorderLayout.CENTER)
        add(buildFooter(frame),     BorderLayout.SOUTH)
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private fun buildHeader(): JPanel {
        val panel = JPanel(BorderLayout(20, 0)).apply {
            background = SF.headerBg
            border     = JBUI.Borders.empty(16, 24)
        }

        /* ---- Left: title + meta badges ---- */
        val left = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }

        left.add(JLabel("SpringForge  Code Quality Report").apply {
            font       = Font("Monospaced", Font.BOLD, 18)
            foreground = Color(0xE6EDF3)
        })
        left.add(Box.createVerticalStrut(6))

        val metaRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        metaRow.add(Badge(humanArchName(result.architecture_pattern).uppercase(), Color(0x21262D), Color(0x8B949E)))
        metaRow.add(JLabel("·").apply { font = Font("Monospaced", Font.PLAIN, 14); foreground = Color(0x484F58) })
        metaRow.add(JLabel(result.analysis_date).apply { font = Font("Monospaced", Font.PLAIN, 12); foreground = Color(0x8B949E) })
        metaRow.add(Box.createHorizontalStrut(4))

        // Severity badge
        val (sevBg, sevFg, sevText) = when {
            result.total_violations == 0 -> Triple(Color(0x0D2A15), Color(0x3FB950), "✓  Clean")
            result.anti_patterns.any { it.severity == "CRITICAL" } ->
                Triple(Color(0x2A0A0A), Color(0xF85149), "● Critical issues")
            else -> Triple(Color(0x2B1D00), Color(0xD29922), "● Issues found")
        }
        metaRow.add(Badge("$sevText  •  ${result.total_files_analyzed} files", sevBg, sevFg))
        left.add(metaRow)

        /* ---- Right: score ring ---- */
        val scoreVal   = result.overall_score.roundToInt()
        val scoreLabel = when { scoreVal >= 90 -> "EXCELLENT"; scoreVal >= 75 -> "GOOD"; scoreVal >= 60 -> "FAIR"; scoreVal >= 40 -> "POOR"; else -> "CRITICAL" }

        val right = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }
        right.add(ScoreRing(scoreVal).also { it.alignmentX = Component.CENTER_ALIGNMENT })
        right.add(Box.createVerticalStrut(2))
        right.add(JLabel(scoreLabel).apply {
            font = Font("Monospaced", Font.BOLD, 9); foreground = Color(0x8B949E); alignmentX = Component.CENTER_ALIGNMENT
        })

        panel.add(left,  BorderLayout.WEST)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    // ── Tab pane ──────────────────────────────────────────────────────────────

    private fun buildTabPane(): JTabbedPane = JTabbedPane(JTabbedPane.TOP).apply {
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        font            = Font("Monospaced", Font.BOLD, 11)
        background      = SF.bg
        addTab("📊  Overview",       buildOverviewTab())
        addTab("⚠   Violations",     buildViolationsTab())
        addTab("📁  Files",          buildFilesTab())
        addTab("🤖  AI Fixes",       buildAIFixesTab())
        addTab("📈  Metrics",        buildMetricsTab())
        addTab("📋  Full Report",    buildFullReportTab())
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 1 — Overview
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildOverviewTab(): JComponent {
        val root = tabPanel()

        /* -- Score bars card -- */
        root.add(sectionLabel("LAYER QUALITY SCORES")); root.add(vgap(10))
        val barsCard = sfCard()
        barsCard.layout = BoxLayout(barsCard, BoxLayout.Y_AXIS)
        result.layer_scores.forEach { ls ->
            barsCard.add(scoreBarRow(ls.layer, ls.mean_score, ls.quality_emoji, ls.quality_label))
            barsCard.add(Box.createVerticalStrut(10))
        }
        barsCard.add(JSeparator().apply {
            foreground = SF.border; maximumSize = Dimension(Int.MAX_VALUE, 1)
        })
        barsCard.add(Box.createVerticalStrut(10))
        barsCard.add(scoreBarRow("Overall", result.overall_score, "", result.overall_label, bold = true))
        root.add(barsCard); root.add(vgap(20))

        /* -- Key metric tiles -- */
        root.add(sectionLabel("KEY METRICS")); root.add(vgap(10))
        val tileData = listOf(
            Triple("📁", result.total_files_analyzed.toString(),                     "Files Analyzed"),
            Triple("⚠",  result.total_violations.toString(),                         "Violations"),
            Triple("🔍", result.files_with_violations.toString(),                   "Files w/ Issues"),
            Triple("✅", result.clean_files.size.toString(),                         "Clean Files"),
            Triple("📏", result.avg_loc.roundToInt().toString(),                     "Avg LOC"),
            Triple("📈", "${result.projected_score_after_fixes.roundToInt()}/100",   "Projected Score")
        )
        val tileRow = JPanel(GridLayout(1, 6, 8, 0)).apply {
            isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 72); alignmentX = Component.LEFT_ALIGNMENT
        }
        tileData.forEach { (icon, value, label) ->
            val tile = sfCard(extraPad = Insets(10, 12, 10, 12))
            tile.layout = BoxLayout(tile, BoxLayout.Y_AXIS)
            tile.add(JLabel("$icon  $value").apply {
                font = Font("Monospaced", Font.BOLD, 14); alignmentX = Component.LEFT_ALIGNMENT
            })
            tile.add(Box.createVerticalStrut(2))
            tile.add(JLabel(label).apply {
                font = Font("Monospaced", Font.PLAIN, 9); foreground = SF.textMuted; alignmentX = Component.LEFT_ALIGNMENT
            })
            tileRow.add(tile)
        }
        root.add(tileRow); root.add(vgap(20))

        /* -- Legend -- */
        root.add(sectionLabel("QUALITY SCALE")); root.add(vgap(10))
        val legendCard = sfCard()
        legendCard.layout = GridLayout(1, 5, 8, 0)
        listOf(
            Triple("90–100", "Excellent",  SF.green),
            Triple("75–89",  "Good",       SF.green),
            Triple("60–74",  "Fair",       SF.amber),
            Triple("40–59",  "Poor",       SF.orange),
            Triple("0–39",   "Critical",   SF.red)
        ).forEach { (range, lbl, color) ->
            val cell = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }
            cell.add(JLabel("● $range").apply {
                font = Font("Monospaced", Font.BOLD, 11); foreground = color; alignmentX = Component.LEFT_ALIGNMENT
            })
            cell.add(JLabel(lbl).apply {
                font = Font("Monospaced", Font.PLAIN, 9); foreground = SF.textSecondary; alignmentX = Component.LEFT_ALIGNMENT
            })
            legendCard.add(cell)
        }
        root.add(legendCard); root.add(Box.createVerticalGlue())
        return scrolled(root)
    }

    private fun scoreBarRow(name: String, score: Double, emoji: String, label: String, bold: Boolean = false): JPanel {
        val row = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 22); alignmentX = Component.LEFT_ALIGNMENT
        }
        val nameLabel = JLabel("${emoji.ifBlank { "  " }} ${name.replaceFirstChar { it.uppercase() }}").apply {
            font = if (bold) Font("Monospaced", Font.BOLD, 11) else Font("Monospaced", Font.PLAIN, 11)
            preferredSize = Dimension(160, 18)
        }
        val bar = ScoreBar(score).apply { preferredSize = Dimension(0, 8) }
        val scoreLabel = JLabel("${score.roundToInt()}/100  $label").apply {
            font = if (bold) Font("Monospaced", Font.BOLD, 11) else Font("Monospaced", Font.PLAIN, 11)
            foreground = when { score >= 75 -> SF.green; score >= 60 -> SF.amber; else -> SF.red }
            preferredSize = Dimension(130, 18); horizontalAlignment = SwingConstants.RIGHT
        }
        row.add(nameLabel, BorderLayout.WEST)
        row.add(bar,       BorderLayout.CENTER)
        row.add(scoreLabel,BorderLayout.EAST)
        return row
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 2 — Violations
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildViolationsTab(): JComponent {
        val root = tabPanel()
        if (result.anti_patterns.isEmpty()) {
            root.add(emptyState("✅", "No Violations Detected",
                "Your ${humanArchName(result.architecture_pattern)} project follows all architectural best practices."))
        } else {
            listOf("CRITICAL" to SF.red, "HIGH" to SF.orange, "MEDIUM" to SF.amber, "LOW" to SF.green)
                .forEach { (sev, color) ->
                    val group = result.anti_patterns.filter { it.severity == sev }
                    if (group.isEmpty()) return@forEach
                    val icon = when(sev) { "CRITICAL" -> "🔴"; "HIGH" -> "🟠"; "MEDIUM" -> "🟡"; else -> "🔵" }
                    root.add(sectionLabel("$icon  $sev  (${group.size} issue${if(group.size>1)"s" else ""})"))
                    root.add(vgap(8))
                    group.forEach { ap -> root.add(violationCard(ap, color)); root.add(vgap(8)) }
                    root.add(vgap(10))
                }
        }
        root.add(Box.createVerticalGlue())
        return scrolled(root)
    }

    private fun violationCard(ap: AntiPatternDetail, accent: Color): JPanel {
        val card = stripeCard(accent)
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; border = JBUI.Borders.empty(12, 14, 12, 14)
        }

        /* Title row */
        val titleRow = JPanel(BorderLayout(8, 0)).apply { isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 24) }
        val titleText = ap.type.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        titleRow.add(JLabel(titleText).apply {
            font = Font("Monospaced", Font.BOLD, 12); foreground = SF.textPrimary
        }, BorderLayout.WEST)
        val badges = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        badges.add(Badge(ap.severity, accent))
        badges.add(Badge("${(ap.confidence * 100).roundToInt()}% confidence",
            SF.overlay, SF.textSecondary))
        titleRow.add(badges, BorderLayout.EAST)
        body.add(titleRow); body.add(vgap(8))

        /* Meta chips */
        val metaRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 22)
        }
        metaRow.add(chip("Layer: ${ap.affected_layer}"))
        metaRow.add(chip("${ap.files.size} file${if (ap.files.size > 1) "s" else ""}"))
        body.add(metaRow); body.add(vgap(8))

        /* Problem / Fix */
        body.add(infoRow("Problem", ap.description))
        body.add(vgap(4))
        body.add(infoRow("Fix", ap.recommendation))
        body.add(vgap(8))

        /* Affected files */
        val fileStr = ap.files.take(4).joinToString("   ·   ") +
            if (ap.files.size > 4) "   +${ap.files.size - 4} more" else ""
        body.add(JLabel("📄  $fileStr").apply {
            font = Font("Monospaced", Font.PLAIN, 10); foreground = SF.textMuted; alignmentX = Component.LEFT_ALIGNMENT
        })

        card.add(body, BorderLayout.CENTER)
        return card
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 3 — Files
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildFilesTab(): JComponent {
        val cols = arrayOf("File", "Layer", "Score", "Status", "Issues")
        val rows = result.files.sortedBy { it.quality_score }.map { f ->
            arrayOf(
                f.file_name,
                f.layer.replaceFirstChar { it.uppercase() },
                "${f.quality_score.roundToInt()}/100",
                "${f.quality_emoji}  ${f.quality_label}",
                if (f.issues.isEmpty()) "✅  Clean"
                else "<html>" + f.issues.joinToString("<br>") + "</html>"
            )
        }.toTypedArray()

        val table = object : javax.swing.JTable(rows, cols) {
            override fun isCellEditable(r: Int, c: Int) = false
        }.apply {
            font = Font("Monospaced", Font.PLAIN, 12)
            // No fixed rowHeight — set per-row dynamically below
            showHorizontalLines = true; showVerticalLines = false; gridColor = SF.border
            background = SF.surface
            tableHeader.apply {
                font = Font("Monospaced", Font.BOLD, 12); background = SF.surfaceAlt
                border = BorderFactory.createMatteBorder(0, 0, 1, 0, SF.border)
            }
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            columnModel.getColumn(0).preferredWidth = 210
            columnModel.getColumn(1).preferredWidth = 90
            columnModel.getColumn(2).preferredWidth = 70
            columnModel.getColumn(3).preferredWidth = 110
            columnModel.getColumn(4).preferredWidth = 320
        }

        table.setDefaultRenderer(Object::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: javax.swing.JTable, v: Any?, sel: Boolean, foc: Boolean, row: Int, col: Int
            ): Component {
                val c = super.getTableCellRendererComponent(t, v, sel, foc, row, col) as JLabel
                c.border  = JBUI.Borders.empty(6, 8)
                c.font    = Font("Monospaced", Font.PLAIN, 12)
                c.verticalAlignment = SwingConstants.TOP   // top-align so multi-line issues read top-down
                if (!sel) {
                    val score = rows[row][2].removeSuffix("/100").toIntOrNull() ?: 50
                    c.background = when {
                        score >= 75 -> SF.greenBgRow
                        score >= 60 -> SF.surface
                        else        -> SF.redBgRow
                    }
                }
                return c
            }
        })

        // Dynamically size each row to fit its rendered content (handles multi-line HTML issues)
        for (row in 0 until table.rowCount) {
            var maxHeight = 32   // minimum row height
            for (col in 0 until table.columnCount) {
                val comp = table.prepareRenderer(table.getCellRenderer(row, col), row, col)
                maxHeight = maxOf(maxHeight, comp.preferredSize.height + 10)
            }
            table.setRowHeight(row, maxHeight)
        }

        val outer = JPanel(BorderLayout(0, 0)).apply {
            background = SF.bg; border = JBUI.Borders.empty(14, 16)
        }
        outer.add(JBScrollPane(table).apply {
            border     = RoundBorder(SF.RADIUS, SF.border)
            background = SF.surface
        }, BorderLayout.CENTER)

        val summaryBar = JPanel(FlowLayout(FlowLayout.LEFT, 14, 4)).apply { isOpaque = false }
        listOf(
            "Total: ${result.files.size}"                              to SF.textSecondary,
            "🟢  Good: ${result.files.count { it.quality_score >= 75 }}" to SF.green,
            "🔴  Critical: ${result.files.count { it.quality_score < 40 }}" to SF.red,
            "✅  Clean: ${result.clean_files.size}"                    to SF.green
        ).forEach { (txt, col) ->
            summaryBar.add(JLabel(txt).apply {
                font = Font("Monospaced", Font.PLAIN, 11); foreground = col
            })
        }
        outer.add(summaryBar, BorderLayout.SOUTH)
        return outer
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 4 — AI Fixes
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildAIFixesTab(): JComponent {
        val root = tabPanel()
        if (fixes == null || fixes.suggestions.isEmpty()) {
            root.add(emptyState(
                if (fixes == null) "⏳" else "✅",
                if (fixes == null) "Gemini Not Available" else "No Fixes Needed",
                if (fixes == null)
                    "Add GEMINI_API_KEY to the ML service .env file and restart the service."
                else
                    "No violations were detected — your project is clean!"
            ))
        } else {
            root.add(sectionLabel("🤖  GEMINI AI FIX SUGGESTIONS  (${fixes.suggestions.size} fixes)"))
            root.add(vgap(12))
            fixes.suggestions.sortedByDescending { it.impact_points }.forEach { fix ->
                root.add(fixCard(fix)); root.add(vgap(10))
            }
        }
        root.add(Box.createVerticalGlue())
        return scrolled(root)
    }

    private fun fixCard(fix: FixSuggestion): JPanel {
        val accent = if (fix.ai_powered) SF.purple else SF.textMuted
        val card   = stripeCard(accent)
        val body   = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; border = JBUI.Borders.empty(12, 14)
        }

        /* Title row */
        val titleRow = JPanel(BorderLayout(8, 0)).apply { isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 24) }
        val title = fix.anti_pattern.replace("_", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        titleRow.add(JLabel(title).apply {
            font = Font("Monospaced", Font.BOLD, 12); foreground = SF.textPrimary
        }, BorderLayout.WEST)
        val badges = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val (sB, sF) = when (fix.severity) {
            "CRITICAL" -> SF.red   to Color.WHITE
            "HIGH"     -> SF.orange to Color.WHITE
            "MEDIUM"   -> SF.amber  to Color.WHITE
            else       -> SF.green  to Color.WHITE
        }
        badges.add(Badge(fix.severity, sB, sF))
        badges.add(Badge(if (fix.ai_powered) "🤖  Gemini AI" else "📖  Static", accent))
        badges.add(Badge("−${fix.impact_points} pts", SF.overlay, SF.textSecondary))
        titleRow.add(badges, BorderLayout.EAST)
        body.add(titleRow); body.add(vgap(6))

        /* Meta chips */
        val meta = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, 22)
        }
        meta.add(chip("Layer: ${fix.layer}"))
        fix.files.take(3).forEach { meta.add(chip(it)) }
        if (fix.files.size > 3) meta.add(chip("+${fix.files.size - 3} more"))
        body.add(meta)

        if (fix.problem.isNotBlank()) { body.add(vgap(8)); body.add(infoRow("Problem", fix.problem)) }
        if (fix.recommendation.isNotBlank()) { body.add(vgap(4)); body.add(infoRow("💡  Recommendation", fix.recommendation)) }

        if (fix.before_code.isNotBlank()) {
            body.add(vgap(10))
            body.add(JLabel("Example:").apply {
                font = Font("Monospaced", Font.BOLD, 10); foreground = SF.textMuted; alignmentX = Component.LEFT_ALIGNMENT
            })
            body.add(vgap(4))
            body.add(codeBlock("// ❌  BEFORE\n${fix.before_code}\n\n// ✅  AFTER\n${fix.after_code}"))
        }
        if (fix.gemini_fix.isNotBlank()) {
            body.add(vgap(10))
            body.add(JLabel(if (fix.ai_powered) "🤖  Gemini AI Analysis:" else "📖  Fix Guidance:").apply {
                font = Font("Monospaced", Font.BOLD, 10); foreground = accent; alignmentX = Component.LEFT_ALIGNMENT
            })
            body.add(vgap(4))
            body.add(codeBlock(fix.gemini_fix))
        }

        card.add(body, BorderLayout.CENTER)
        return card
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 5 — Metrics
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildMetricsTab(): JComponent {
        val root = tabPanel()

        root.add(sectionLabel("PROJECT HEALTH")); root.add(vgap(10))
        root.add(metricTable(listOf(
            "Architecture"             to humanArchName(result.architecture_pattern),
            "Files Analyzed"           to result.total_files_analyzed.toString(),
            "Files with Violations"    to result.files_with_violations.toString(),
            "Total Issues Found"       to result.total_violations.toString(),
            "Clean Files"              to result.clean_files.size.toString(),
            "Average LOC"              to result.avg_loc.roundToInt().toString(),
            "Avg Cross-Layer Deps"     to "%.2f".format(result.avg_cross_layer_deps),
            "Projected Score"          to "${result.projected_score_after_fixes.roundToInt()}/100"
        )))
        root.add(vgap(20))

        root.add(sectionLabel("LAYER SCORES")); root.add(vgap(10))
        root.add(metricTable(result.layer_scores.map { ls ->
            ls.layer.replaceFirstChar { it.uppercase() } to
                "${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji}  ${ls.quality_label}  (${ls.file_count} files)"
        }))
        root.add(vgap(20))

        root.add(sectionLabel("VIOLATIONS BY SEVERITY")); root.add(vgap(10))
        val sevRows = listOf("CRITICAL","HIGH","MEDIUM","LOW").mapNotNull { sev ->
            val c = result.anti_patterns.count { it.severity == sev }
            if (c > 0) "$sev" to "$c violation${if(c>1)"s" else ""}" else null
        }
        if (sevRows.isEmpty()) {
            root.add(JLabel("✅  No violations found.").apply {
                font = Font("Monospaced", Font.PLAIN, 11); foreground = SF.green; alignmentX = Component.LEFT_ALIGNMENT
            })
        } else root.add(metricTable(sevRows))

        root.add(Box.createVerticalGlue())
        return scrolled(root)
    }

    private fun metricTable(rows: List<Pair<String, String>>): JPanel {
        val panel = JPanel(GridLayout(rows.size, 2, 0, 0)).apply {
            isOpaque = false; alignmentX = Component.LEFT_ALIGNMENT
            border   = RoundBorder(SF.RADIUS, SF.border)
            maximumSize = Dimension(Int.MAX_VALUE, rows.size * 30)
        }
        rows.forEachIndexed { i, (k, v) ->
            val bg = if (i % 2 == 0) SF.surface else SF.surfaceAlt
            panel.add(JLabel("   $k").apply {
                font = Font("Monospaced", Font.PLAIN, 11); foreground = SF.textSecondary
                background = bg; isOpaque = true; border = JBUI.Borders.empty(4, 8)
            })
            panel.add(JLabel("$v   ").apply {
                font = Font("Monospaced", Font.BOLD, 11); foreground = SF.textPrimary
                background = bg; isOpaque = true; horizontalAlignment = SwingConstants.RIGHT
                border = JBUI.Borders.empty(4, 8)
            })
        }
        return panel
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  TAB 6 — Full Text Report
    // ══════════════════════════════════════════════════════════════════════════

    private fun buildFullReportTab(): JComponent {
        val area = JBTextArea(buildReportText()).apply {
            isEditable = false; font = Font("Monospaced", Font.PLAIN, 11)
            background = SF.codeBg; foreground = SF.codeFg
            lineWrap = false; border = JBUI.Borders.empty(14)
        }
        return JBScrollPane(area).apply { border = JBUI.Borders.empty() }
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private fun buildFooter(frame: JFrame): JPanel {
        val panel = JPanel(BorderLayout(0, 0)).apply {
            background = SF.headerBg
            border     = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SF.headerBorder),
                JBUI.Borders.empty(10, 20)
            )
        }
        panel.add(JLabel("SpringForge v2.1  ·  ML + Gemini AI  ·  ${humanArchName(result.architecture_pattern)}").apply {
            font = Font("Monospaced", Font.PLAIN, 10); foreground = Color(0x6E7681)
        }, BorderLayout.WEST)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }

        val printBtn = footerButton("🖨   Print Report", Color(0x21262D), Color(0xC9D1D9))
        val closeBtn = footerButton("✕   Close",         Color(0x6E1C1C), Color(0xFFA198))
        printBtn.addActionListener { printReport(frame) }
        closeBtn.addActionListener { frame.dispose() }
        right.add(printBtn); right.add(closeBtn)
        panel.add(right, BorderLayout.EAST)
        return panel
    }

    private fun footerButton(text: String, bg: Color, fg: Color) = JButton(text).apply {
        font = Font("Monospaced", Font.BOLD, 11); background = bg; foreground = fg
        isFocusPainted = false; isContentAreaFilled = true; isOpaque = true
        border = JBUI.Borders.empty(6, 14); cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // ── Print ─────────────────────────────────────────────────────────────────

    private fun printReport(frame: JFrame) {
        val job  = PrinterJob.getPrinterJob()
        job.setJobName("SpringForge Quality Report")
        val text = buildReportText()
        job.setPrintable(object : Printable {
            override fun print(g: Graphics, pf: PageFormat, page: Int): Int {
                val g2 = g as Graphics2D; g2.font = Font("Monospaced", Font.PLAIN, 8)
                val fm  = g2.fontMetrics; val lh  = fm.height
                val x   = pf.imageableX.toFloat(); val y0 = pf.imageableY.toFloat()
                val cpl = (pf.imageableWidth / fm.charWidth('M')).toInt()
                val lpp = (pf.imageableHeight / lh).toInt()
                val lines = mutableListOf<String>()
                text.lines().forEach { line ->
                    if (line.length <= cpl) lines.add(line)
                    else { var s = 0; while (s < line.length) { lines.add(line.substring(s, minOf(s+cpl,line.length))); s += cpl } }
                }
                val start = page * lpp; if (start >= lines.size) return Printable.NO_SUCH_PAGE
                g2.color = Color.BLACK; var yPos = y0 + lh
                for (i in start until minOf(start + lpp, lines.size)) { g2.drawString(lines[i], x, yPos); yPos += lh }
                return Printable.PAGE_EXISTS
            }
        })
        if (job.printDialog()) try { job.print() }
        catch (ex: PrinterException) {
            JOptionPane.showMessageDialog(frame, "Print failed:\n${ex.message}", "Print Error", JOptionPane.ERROR_MESSAGE)
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Standard tab body panel. */
    private fun tabPanel(): JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false; border = JBUI.Borders.empty(18, 22)
    }

    /** Vertical gap component. */
    private fun vgap(n: Int): Component = Box.createVerticalStrut(n)

    /** Key + value label row. */
    private fun infoRow(key: String, value: String): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false; maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        row.add(JLabel("$key:").apply {
            font = Font("Monospaced", Font.BOLD, 11); foreground = SF.textSecondary
        })
        row.add(JLabel("<html><body style='width:520px;font-family:monospace;font-size:11px'>$value</body></html>").apply {
            foreground = SF.textPrimary
        })
        return row
    }

    /** Monospaced code block panel. */
    private fun codeBlock(code: String): JPanel {
        val area = JTextArea(code).apply {
            isEditable = false; font = Font("Monospaced", Font.PLAIN, 10)
            background = SF.codeBg; foreground = SF.codeFg
            lineWrap = true; wrapStyleWord = true; border = JBUI.Borders.empty(8, 10)
        }
        return JPanel(BorderLayout()).apply {
            isOpaque = false; border = RoundBorder(SF.RADIUS_S, SF.border)
            add(area, BorderLayout.CENTER)
            alignmentX = Component.LEFT_ALIGNMENT; maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }

    /** Centered empty-state block for tabs with no data. */
    private fun emptyState(icon: String, title: String, subtitle: String): JPanel {
        val p = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); isOpaque = false }
        p.add(Box.createVerticalStrut(50))
        p.add(JLabel(icon).apply { font = Font("Monospaced", Font.PLAIN, 34); alignmentX = Component.CENTER_ALIGNMENT })
        p.add(Box.createVerticalStrut(10))
        p.add(JLabel(title).apply {
            font = Font("Monospaced", Font.BOLD, 14); alignmentX = Component.CENTER_ALIGNMENT; foreground = SF.textPrimary
        })
        p.add(Box.createVerticalStrut(6))
        p.add(JLabel("<html><div style='text-align:center;width:340px'>$subtitle</div></html>").apply {
            alignmentX = Component.CENTER_ALIGNMENT; foreground = SF.textSecondary
        })
        return p
    }

    /** Full text report for the Full Report tab and printing. */
    private fun buildReportText(): String {
        val sb = StringBuilder()
        sb.appendLine("╔══════════════════════════════════════════════════════════════════╗")
        sb.appendLine("║       SPRINGFORGE CODE QUALITY ANALYSIS REPORT                  ║")
        sb.appendLine("╚══════════════════════════════════════════════════════════════════╝")
        sb.appendLine()
        sb.appendLine("Architecture : ${humanArchName(result.architecture_pattern)}")
        sb.appendLine("Date         : ${result.analysis_date}")
        sb.appendLine("Files        : ${result.total_files_analyzed}")
        sb.appendLine("Score        : ${result.overall_display}")
        sb.appendLine("Violations   : ${result.total_violations}")
        sb.appendLine("AI Fixes     : ${fixes?.total_fixes ?: 0}")
        sb.appendLine()
        sb.appendLine("── LAYER SCORES ──────────────────────────────────────────────────────")
        result.layer_scores.forEach { ls ->
            val bar = "█".repeat((ls.mean_score / 5).roundToInt().coerceIn(0,20)) +
                      "░".repeat(20 - (ls.mean_score / 5).roundToInt().coerceIn(0,20))
            sb.appendLine("  ${ls.layer.padEnd(16)} $bar  ${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji} ${ls.quality_label}")
        }
        sb.appendLine()
        sb.appendLine("── VIOLATIONS ────────────────────────────────────────────────────────")
        if (result.anti_patterns.isEmpty()) sb.appendLine("  ✅  No violations detected.")
        else listOf("CRITICAL","HIGH","MEDIUM","LOW").forEach { sev ->
            result.anti_patterns.filter { it.severity == sev }.forEach { ap ->
                sb.appendLine()
                sb.appendLine("  [${ap.severity}] ${ap.type.replace("_"," ").uppercase()}")
                sb.appendLine("  Layer      : ${ap.affected_layer}")
                sb.appendLine("  Confidence : ${(ap.confidence*100).roundToInt()}%")
                sb.appendLine("  Files      : ${ap.files.joinToString(", ")}")
                sb.appendLine("  Problem    : ${ap.description}")
                sb.appendLine("  Fix        : ${ap.recommendation}")
            }
        }
        sb.appendLine()
        sb.appendLine("── FILE DETAILS ──────────────────────────────────────────────────────")
        result.files.sortedBy { it.quality_score }.forEach { f ->
            sb.appendLine("  ${f.file_name.padEnd(42)} ${f.quality_display}")
            f.issues.forEach { issue -> sb.appendLine("    ·  $issue") }
        }
        sb.appendLine()
        sb.appendLine("── CLEAN FILES ───────────────────────────────────────────────────────")
        result.clean_files.forEach { sb.appendLine("  ✅  $it") }
        fixes?.suggestions?.let { sgs ->
            if (sgs.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("── AI FIX SUGGESTIONS ────────────────────────────────────────────────")
                sgs.forEach { fix ->
                    sb.appendLine()
                    sb.appendLine("  [${fix.severity}] ${fix.anti_pattern.replace("_"," ").uppercase()}")
                    sb.appendLine("  Impact : -${fix.impact_points} pts  |  ${if (fix.ai_powered) "Gemini AI" else "Static"}")
                    if (fix.recommendation.isNotBlank()) sb.appendLine("  💡  ${fix.recommendation}")
                    if (fix.gemini_fix.isNotBlank()) fix.gemini_fix.lines().forEach { sb.appendLine("     $it") }
                    if (fix.before_code.isNotBlank()) {
                        sb.appendLine("  // ❌  BEFORE")
                        fix.before_code.lines().forEach { sb.appendLine("     $it") }
                        sb.appendLine("  // ✅  AFTER")
                        fix.after_code.lines().forEach  { sb.appendLine("     $it") }
                    }
                }
            }
        }
        sb.appendLine()
        sb.appendLine("${"━".repeat(70)}")
        sb.appendLine("  Generated by SpringForge Code Quality Analyzer v2.1")
        if (fixes != null) sb.appendLine("  AI fixes powered by Google Gemini 🤖")
        return sb.toString()
    }

    private fun humanArchName(apiKey: String): String = when (apiKey.lowercase()) {
        "clean_architecture" -> "Clean Architecture"
        "hexagonal"          -> "Hexagonal"
        "mvc"                -> "MVC"
        else                 -> "Layered"
    }
}