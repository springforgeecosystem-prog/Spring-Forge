package org.springforge.cicdassistant.audit.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.springforge.cicdassistant.audit.AuditEvent
import org.springforge.cicdassistant.audit.AuditEventType
import org.springforge.cicdassistant.audit.AuditService
import java.awt.*
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Audit Dashboard Panel — auto-refreshing summary cards + events table + PDF export.
 */
class AuditDashboardPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val service = AuditService.getInstance(project)

    // ── Summary cards ─────────────────────────────────────────────────────────
    // Row 1: CI/CD Assistant
    private val genCard  = SummaryCard("GENERATIONS",   JBColor(Color(0x005CC5), Color(0x64B5F6)))
    private val valCard  = SummaryCard("VALIDATIONS",   JBColor(Color(0xD73A49), Color(0xEF5350)))
    private val explCard = SummaryCard("EXPLAINABILITY",JBColor(Color(0x6F42C1), Color(0xCE93D8)))
    // Row 2: Other modules
    private val codeGenCard = SummaryCard("CODE GEN",     JBColor(Color(0x0A7E07), Color(0x66BB6A)))
    private val qualCard    = SummaryCard("QUALITY SCAN", JBColor(Color(0xE36209), Color(0xFFA726)))
    private val runtimeCard = SummaryCard("RUNTIME",      JBColor(Color(0x005B99), Color(0x4FC3F7)))

    // ── Table ─────────────────────────────────────────────────────────────────
    private val tableModel = EventsTableModel()
    private val table = JTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        rowHeight = 22
        font = JBUI.Fonts.label(11f)
        isOpaque = false
        fillsViewportHeight = true
        intercellSpacing = Dimension(0, 0)
        setShowGrid(false)
        columnModel.getColumn(6).cellRenderer = StatusCellRenderer()
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private val statusLabel = JBLabel().apply {
        font       = JBUI.Fonts.label(10f)
        foreground = JBColor.GRAY
        border     = JBUI.Borders.empty(0, 6)
    }

    private var cachedEvents: List<AuditEvent> = emptyList()

    init {
        border = JBUI.Borders.empty(10)
        setupUI()
        // Register auto-refresh: whenever AuditService writes a row, refresh immediately
        service.onNewEvent = { refresh() }
        refresh()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupUI() {
        val summaryRow = JPanel(GridLayout(2, 3, 10, 8)).apply {
            isOpaque = false
            border   = JBUI.Borders.emptyBottom(12)
            add(genCard); add(valCard); add(explCard)
            add(codeGenCard); add(qualCard); add(runtimeCard)
        }

        table.tableHeader.apply {
            font       = JBUI.Fonts.label(10f).asBold()
            background = JBColor(Color(0xF1F5F9), Color(0x2B2D30))
            foreground = JBColor(Color(0x444444), Color(0xAAAAAA))
            reorderingAllowed = false
        }

        val tableScroll = JBScrollPane(table).apply {
            border = BorderFactory.createLineBorder(JBColor.border(), 1)
        }

        val refreshButton = JButton("Refresh").apply {
            font = JBUI.Fonts.label(11f); isFocusPainted = false
            addActionListener { refresh() }
        }
        val exportButton = JButton("Export PDF").apply {
            font = JBUI.Fonts.label(11f); isFocusPainted = false
            addActionListener { exportPdf() }
        }
        val clearButton = JButton("Clear Log").apply {
            font       = JBUI.Fonts.label(11f); isFocusPainted = false
            foreground = JBColor(Color(0xD73A49), Color(0xEF5350))
            addActionListener { onClear() }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            isOpaque = false
            border   = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border())
            add(refreshButton); add(exportButton); add(clearButton)
            add(Box.createHorizontalStrut(12)); add(statusLabel)
        }

        val titleBar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border   = JBUI.Borders.emptyBottom(8)
            add(JBLabel("Audit Log").apply { font = JBUI.Fonts.label(12f).asBold() }, BorderLayout.WEST)
            add(JBLabel("Recent 100 events — auto-updates after each action").apply {
                font = JBUI.Fonts.label(10f); foreground = JBColor.GRAY
            }, BorderLayout.EAST)
        }

        val centerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(titleBar,    BorderLayout.NORTH)
            add(tableScroll, BorderLayout.CENTER)
        }

        add(summaryRow,  BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
        add(buttonRow,   BorderLayout.SOUTH)
        updateStatus()
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Data refresh
    // ══════════════════════════════════════════════════════════════════════════

    fun refresh() {
        if (!service.isConfigured()) { updateStatus(); return }
        ApplicationManager.getApplication().executeOnPooledThread {
            val events = service.getRecentEvents(100)
            val stats  = service.getSummaryStats()
            SwingUtilities.invokeLater {
                cachedEvents = events
                tableModel.setEvents(events)
                updateCards(stats)
                updateStatus()
            }
        }
    }

    private fun updateCards(stats: Map<AuditEventType, Triple<Int, Int, Double>>) {
        fun fmt(t: Triple<Int, Int, Double>?): Triple<String, String, String> {
            if (t == null || t.first == 0) return Triple("0 total", "—", "—")
            val pct = if (t.first > 0) (t.second * 100 / t.first) else 0
            val avg = if (t.third >= 1000) "%.1f s".format(t.third / 1000.0) else "${t.third.toInt()} ms"
            return Triple("${t.first} total", "$pct% success", "avg $avg")
        }
        genCard.update(fmt(stats[AuditEventType.GENERATION]))
        valCard.update(fmt(stats[AuditEventType.VALIDATION]))
        explCard.update(fmt(stats[AuditEventType.EXPLAINABILITY]))
        codeGenCard.update(fmt(stats[AuditEventType.CODE_GENERATION]))
        qualCard.update(fmt(stats[AuditEventType.QUALITY_SCAN]))
        runtimeCard.update(fmt(stats[AuditEventType.RUNTIME_ANALYSIS]))
    }

    private fun updateStatus() {
        if (!service.isConfigured()) {
            statusLabel.text       = "Not configured — add POSTGRES_* keys to .env"
            statusLabel.foreground = JBColor(Color(0xD73A49), Color(0xEF5350))
        } else {
            statusLabel.text       = "Connected · auto-refreshing"
            statusLabel.foreground = JBColor.GRAY
        }
    }

    private fun onClear() {
        val ok = Messages.showYesNoDialog(
            project, "Delete all audit log entries? This cannot be undone.",
            "Clear Audit Log", Messages.getWarningIcon()
        )
        if (ok == Messages.YES) {
            ApplicationManager.getApplication().executeOnPooledThread {
                service.clearAllEvents()
                SwingUtilities.invokeLater { refresh() }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PDF Export
    // ══════════════════════════════════════════════════════════════════════════

    private fun exportPdf() {
        if (cachedEvents.isEmpty()) {
            Messages.showInfoMessage(project, "No audit events to export.", "Nothing to Export")
            return
        }
        val fc = JFileChooser().apply {
            dialogTitle  = "Export Audit Log as PDF"
            selectedFile = File("SpringForge-Audit-Log.pdf")
            fileFilter = object : javax.swing.filechooser.FileFilter() {
                override fun accept(f: File) = f.isDirectory || f.name.endsWith(".pdf")
                override fun getDescription() = "PDF Document (*.pdf)"
            }
        }
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val out = fc.selectedFile.let { if (it.name.endsWith(".pdf")) it else File(it.absolutePath + ".pdf") }
        try {
            val html = buildHtmlReport(cachedEvents)
            out.outputStream().use { os ->
                PdfRendererBuilder().useFastMode().withHtmlContent(html, null).toStream(os).run()
            }
            Messages.showInfoMessage(project, "Report exported to:\n${out.absolutePath}", "Export Successful")
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to export PDF:\n${e.message}", "Export Failed")
        }
    }

    private fun buildHtmlReport(events: List<AuditEvent>): String {
        val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        val logoSrc: String = runCatching {
            this::class.java.classLoader.getResourceAsStream("icons/springforge.png")
                ?.readBytes()?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) } ?: ""
        }.getOrDefault("")

        val genEvents     = events.filter { it.eventType == AuditEventType.GENERATION }
        val valEvents     = events.filter { it.eventType == AuditEventType.VALIDATION }
        val explEvents    = events.filter { it.eventType == AuditEventType.EXPLAINABILITY }
        val codeGenEvents = events.filter { it.eventType == AuditEventType.CODE_GENERATION }
        val qualEvents    = events.filter { it.eventType == AuditEventType.QUALITY_SCAN }
        val runtimeEvents = events.filter { it.eventType == AuditEventType.RUNTIME_ANALYSIS }
        fun successPct(list: List<AuditEvent>) =
            if (list.isEmpty()) "—" else "${list.count { it.success } * 100 / list.size}%"

        return buildString {
            appendLine("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>")
            appendLine("<style>")
            appendLine("""
@page { size: A4 landscape; margin: 1.2cm 1.5cm 1.5cm 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Arial, Helvetica, sans-serif; font-size: 9.5pt; color: #24292E; margin: 0; padding: 0; }
.hdr { background-color: #1E3A5F; padding: 14pt 20pt; width: 100%; }
.hdr td { vertical-align: middle; }
.hdr-title { font-size: 15pt; font-weight: bold; color: #FFFFFF; }
.hdr-sub   { font-size: 9pt; color: #B0C4DE; margin-top: 3pt; }
.hdr-meta  { font-size: 8pt; color: #90A4AE; margin-top: 5pt; }
.hdr-brand { font-size: 7pt; color: #B0C4DE; font-weight: bold; text-align: right; }
.sec-title { font-size: 11pt; font-weight: bold; color: #1E3A5F;
             border-bottom: 2pt solid #CBD5E1; padding-bottom: 4pt; margin: 14pt 0 8pt 0; }
.stats-tbl { width: 100%; border-collapse: collapse; margin: 8pt 0; font-size: 9pt; }
.stats-tbl th { background: #1E3A5F; color: #fff; padding: 5pt 10pt; text-align: center; }
.stats-tbl th.lbl { text-align: left; }
.stats-tbl td { padding: 4pt 10pt; border: 1pt solid #E2E8F0; text-align: center; }
.stats-tbl td.lbl { text-align: left; font-weight: bold; }
.events-tbl { width: 100%; border-collapse: collapse; font-size: 8.5pt; }
.events-tbl th { background: #F1F5F9; color: #444; padding: 5pt 8pt;
                 text-align: left; border-bottom: 2pt solid #CBD5E1; font-size: 8pt; }
.events-tbl td { padding: 4pt 8pt; border-bottom: 1pt solid #F1F5F9; vertical-align: middle; }
.events-tbl tr:nth-child(even) td { background: #FAFBFC; }
.badge-gen  { color: #005CC5; font-weight: bold; }
.badge-val  { color: #D73A49; font-weight: bold; }
.badge-expl { color: #6F42C1; font-weight: bold; }
.ok   { color: #28A745; font-weight: bold; }
.err  { color: #D73A49; font-weight: bold; }
.warn { color: #E36209; }
.footer { text-align: center; color: #94A3B8; font-size: 7.5pt; font-style: italic;
          padding-top: 10pt; border-top: 1pt solid #E2E8F0; margin-top: 18pt; }
            """.trimIndent())
            appendLine("</style></head><body>")

            // ── Header ────────────────────────────────────────────────────────
            appendLine("<table class=\"hdr\" cellspacing=\"0\" cellpadding=\"0\"><tr>")
            if (logoSrc.isNotEmpty())
                appendLine("  <td style=\"width:48pt;padding-right:12pt;\"><img src=\"$logoSrc\" style=\"height:40pt;width:40pt;\"/></td>")
            appendLine("  <td>")
            appendLine("    <div class=\"hdr-title\">SpringForge Audit Log Report</div>")
            appendLine("    <div class=\"hdr-sub\">Traceability &amp; Compliance Report &#8212; SpringForge Audit System</div>")
            appendLine("    <div class=\"hdr-meta\">Generated: ${timeFmt.format(java.time.Instant.now())} &#160;&#8226;&#160; ${events.size} event${if (events.size != 1) "s" else ""}</div>")
            appendLine("  </td>")
            appendLine("  <td style=\"width:80pt;vertical-align:top;padding-left:12pt;\">")
            appendLine("    <div class=\"hdr-brand\">SPRINGFORGE<br/>Audit Engine</div></td>")
            appendLine("</tr></table>")

            // ── Summary stats ─────────────────────────────────────────────────
            appendLine("<div class=\"sec-title\">Summary</div>")
            appendLine("<table class=\"stats-tbl\" cellspacing=\"0\">")
            appendLine("  <tr><th class=\"lbl\">Module</th><th>Total Events</th><th>Successes</th><th>Failures</th><th>Success Rate</th></tr>")
            listOf(
                Triple("CI/CD Generation",    genEvents,      "#005CC5"),
                Triple("CI/CD Validation",    valEvents,      "#D73A49"),
                Triple("CI/CD Explainability",explEvents,     "#6F42C1"),
                Triple("Code Generation",     codeGenEvents,  "#0A7E07"),
                Triple("Quality Scan",        qualEvents,     "#E36209"),
                Triple("Runtime Analysis",    runtimeEvents,  "#005B99")
            ).forEach { (name, evts, hex) ->
                val ok  = evts.count { it.success }
                val fail= evts.size - ok
                appendLine("  <tr>")
                appendLine("    <td class=\"lbl\" style=\"color:$hex;\">$name</td>")
                appendLine("    <td>${evts.size}</td><td>$ok</td><td>${fail}</td>")
                appendLine("    <td>${successPct(evts)}</td></tr>")
            }
            appendLine("  <tr style=\"background:#EFF6FF;font-weight:bold;border-top:2pt solid #2563EB;\">")
            val totalOk = events.count { it.success }
            appendLine("    <td class=\"lbl\">TOTAL</td><td>${events.size}</td><td>$totalOk</td>")
            appendLine("    <td>${events.size - totalOk}</td>")
            appendLine("    <td>${if (events.isEmpty()) "—" else "${totalOk * 100 / events.size}%"}</td></tr>")
            appendLine("</table>")

            // ── Events table ──────────────────────────────────────────────────
            appendLine("<div class=\"sec-title\">Event Log (most recent first)</div>")
            appendLine("<table class=\"events-tbl\" cellspacing=\"0\">")
            appendLine("  <tr><th>Time</th><th>Type</th><th>Source</th><th>Artifacts</th><th>Files</th><th>Duration</th><th>Status</th><th>Details</th></tr>")
            events.forEach { e ->
                val timeStr = timeFmt.format(e.createdAt)
                val typeHex = when(e.eventType) {
                    AuditEventType.GENERATION     -> "#005CC5"
                    AuditEventType.VALIDATION     -> "#D73A49"
                    AuditEventType.EXPLAINABILITY -> "#6F42C1"
                    AuditEventType.CODE_GENERATION -> "#0A7E07"
                    AuditEventType.QUALITY_SCAN    -> "#E36209"
                    AuditEventType.RUNTIME_ANALYSIS -> "#005B99"
                }
                val statusHtml = when {
                    !e.success -> "<span class=\"err\">&#10008; Error</span>"
                    e.eventType == AuditEventType.VALIDATION && e.issuesError > 0 ->
                        "<span class=\"err\">&#9888; ${e.issuesError} err</span>"
                    e.eventType == AuditEventType.VALIDATION && e.issuesWarn > 0 ->
                        "<span class=\"warn\">&#9888; ${e.issuesWarn} warn</span>"
                    e.eventType == AuditEventType.QUALITY_SCAN && e.issuesError > 0 ->
                        "<span class=\"err\">&#9888; ${e.issuesError} critical</span>"
                    e.eventType == AuditEventType.QUALITY_SCAN && e.issuesWarn > 0 ->
                        "<span class=\"warn\">&#9888; ${e.issuesWarn} violations</span>"
                    else -> "<span class=\"ok\">&#10004; OK</span>"
                }
                val detailHtml = when (e.eventType) {
                    AuditEventType.VALIDATION ->
                        "${e.issuesError}E / ${e.issuesWarn}W / ${e.issuesInfo}I"
                    AuditEventType.EXPLAINABILITY ->
                        "${e.insightCount} insight${if (e.insightCount != 1) "s" else ""}"
                    AuditEventType.CODE_GENERATION ->
                        "${e.insightCount} written, ${e.issuesWarn} skipped"
                    AuditEventType.QUALITY_SCAN ->
                        "${e.filesCount} files, ${e.issuesWarn} violations (${e.insightCount} AI fixes)"
                    AuditEventType.RUNTIME_ANALYSIS ->
                        if (e.success) "Analysis complete" else e.errorMsg?.take(60) ?: "Failed"
                    else -> e.errorMsg?.take(60) ?: "—"
                }
                val dur = if (e.durationMs >= 1000) "%.1fs".format(e.durationMs / 1000.0)
                          else "${e.durationMs}ms"
                appendLine("  <tr>")
                appendLine("    <td style=\"white-space:nowrap;\">$timeStr</td>")
                appendLine("    <td><span style=\"color:$typeHex;font-weight:bold;\">${e.eventType.label}</span></td>")
                appendLine("    <td>${e.sourceType ?: "—"}</td>")
                appendLine("    <td>${e.artifacts?.replace(",", ", ") ?: "—"}</td>")
                appendLine("    <td style=\"text-align:center;\">${if (e.filesCount > 0) e.filesCount.toString() else "—"}</td>")
                appendLine("    <td style=\"text-align:right;\">$dur</td>")
                appendLine("    <td>$statusHtml</td>")
                appendLine("    <td>${escHtml(detailHtml)}</td>")
                appendLine("  </tr>")
            }
            appendLine("</table>")

            appendLine("<div class=\"footer\">Generated by SpringForge Audit Engine &#8226; Powered by Claude AI &#8226; github.com/springforgeecosystem-prog</div>")
            appendLine("</body></html>")
        }
    }

    private fun escHtml(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

    // ══════════════════════════════════════════════════════════════════════════
    //  Summary card widget
    // ══════════════════════════════════════════════════════════════════════════

    private class SummaryCard(title: String, accent: Color) : JPanel(GridLayout(4, 1, 0, 2)) {
        private val totalLabel   = JBLabel("0 total")
        private val successLabel = JBLabel("—")
        private val avgLabel     = JBLabel("—")
        init {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
                    BorderFactory.createLineBorder(JBColor.border(), 1)),
                JBUI.Borders.empty(8, 12, 8, 12))
            background = UIUtil.getPanelBackground()
            val titleLabel = JBLabel(title).apply { font = JBUI.Fonts.label(9f).asBold(); foreground = accent }
            totalLabel.font   = JBUI.Fonts.label(13f).asBold()
            successLabel.font = JBUI.Fonts.label(10f); successLabel.foreground = JBColor.GRAY
            avgLabel.font     = JBUI.Fonts.label(10f); avgLabel.foreground     = JBColor.GRAY
            add(titleLabel); add(totalLabel); add(successLabel); add(avgLabel)
        }
        fun update(data: Triple<String, String, String>) {
            totalLabel.text = data.first; successLabel.text = data.second; avgLabel.text = data.third
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Table model
    // ══════════════════════════════════════════════════════════════════════════

    private class EventsTableModel : AbstractTableModel() {
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        private val cols = arrayOf("Time","Type","Source","Artifacts","Files","Duration","Status")
        private var rows: List<AuditEvent> = emptyList()
        fun setEvents(events: List<AuditEvent>) { rows = events; fireTableDataChanged() }
        override fun getRowCount()    = rows.size
        override fun getColumnCount() = cols.size
        override fun getColumnName(c: Int) = cols[c]
        override fun isCellEditable(r: Int, c: Int) = false
        override fun getValueAt(row: Int, col: Int): Any {
            val e = rows[row]
            return when (col) {
                0 -> timeFmt.format(e.createdAt)
                1 -> e.eventType.label
                2 -> e.sourceType ?: "—"
                3 -> e.artifacts?.replace(",", ", ") ?: "—"
                4 -> if (e.filesCount > 0) e.filesCount.toString() else "—"
                5 -> if (e.durationMs >= 1000) "%.1f s".format(e.durationMs / 1000.0) else "${e.durationMs} ms"
                6 -> e
                else -> ""
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Status cell renderer
    // ══════════════════════════════════════════════════════════════════════════

    private class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean,
            hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val event = value as? AuditEvent
            val label = if (event == null) "" else when {
                !event.success -> "❌ Error"
                event.eventType == AuditEventType.VALIDATION && event.issuesError > 0 ->
                    "⚠ ${event.issuesError} err"
                event.eventType == AuditEventType.VALIDATION && event.issuesWarn > 0 ->
                    "⚠ ${event.issuesWarn} warn"
                event.eventType == AuditEventType.QUALITY_SCAN && event.issuesError > 0 ->
                    "⚠ ${event.issuesError} critical"
                event.eventType == AuditEventType.QUALITY_SCAN && event.issuesWarn > 0 ->
                    "⚠ ${event.issuesWarn} violations"
                else -> "✅ OK"
            }
            val comp = super.getTableCellRendererComponent(table, label, isSelected, hasFocus, row, column)
            if (!isSelected && event != null) {
                foreground = when {
                    !event.success -> JBColor(Color(0xD73A49), Color(0xEF5350))
                    event.eventType == AuditEventType.VALIDATION && event.issuesError > 0 ->
                        JBColor(Color(0xD73A49), Color(0xEF5350))
                    event.eventType == AuditEventType.VALIDATION && event.issuesWarn > 0 ->
                        JBColor(Color(0xE36209), Color(0xFFA726))
                    else -> JBColor(Color(0x28A745), Color(0x4CAF50))
                }
            }
            return comp
        }
    }
}
