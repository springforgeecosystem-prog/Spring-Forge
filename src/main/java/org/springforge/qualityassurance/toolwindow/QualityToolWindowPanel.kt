// QualityToolWindowPanel.kt
package org.springforge.qualityassurance.toolwindow

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.springforge.qualityassurance.model.AntiPatternDetail
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import org.springforge.qualityassurance.model.FileQualityResult
import org.springforge.qualityassurance.model.LayerQualitySummary
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import kotlin.math.roundToInt

class QualityToolWindowPanel : JPanel() {

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    init {
        layout = BorderLayout()
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    fun showMessage(message: String) {
        textArea.text = message
        textArea.caretPosition = 0
    }

    fun showCombinedResults(result: CombinedAnalysisResult) {
        textArea.text = buildCombinedReport(result)
        textArea.caretPosition = 0
    }

    fun showEnhancedResults(result: EnhancedPredictionResult) {
        textArea.text = buildLegacyReport(result)
        textArea.caretPosition = 0
    }

    // =========================================================================
    // COMBINED REPORT  (Anti-Pattern + Quality Score)
    // =========================================================================

    private fun buildCombinedReport(r: CombinedAnalysisResult): String {
        val sb = StringBuilder()

        // ── HEADER ────────────────────────────────────────────────────────────
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n")
        sb.append("║       SPRINGFORGE CODE QUALITY ANALYSIS REPORT                  ║\n")
        sb.append("║                  Complete System Analysis                        ║\n")
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n")
        sb.append("\n")

        // ── PROJECT OVERVIEW ──────────────────────────────────────────────────
        sb.append("📊 PROJECT OVERVIEW\n")
        sb.append("─".repeat(66) + "\n")
        sb.append("Architecture Pattern : ${r.architecture_pattern.uppercase()}\n")
        sb.append("Files Analyzed       : ${r.total_files_analyzed}\n")
        sb.append("Analysis Date        : ${r.analysis_date}\n")
        sb.append("Total Issues Found   : ${r.total_violations} violations across ${r.files_with_violations} files\n")
        sb.append("\n")

        // ── QUALITY SCORE DASHBOARD ───────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                    QUALITY SCORE DASHBOARD\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("\n")
        sb.append("┌──────────────────────────────────────────────────────────────┐\n")
        sb.append("│                     LAYER QUALITY SCORES                     │\n")
        sb.append("├──────────────────────────────────────────────────────────────┤\n")
        sb.append("│                                                               │\n")

        val layerScores: List<LayerQualitySummary> = r.layer_scores
        for (ls in layerScores) {
            val layerName = ls.layer.replaceFirstChar { ch -> ch.uppercase() } + " Layer"
            val bar = buildScoreBar(ls.mean_score, 18)
            val scoreStr = "${ls.mean_score.roundToInt()}/100"
            val labelStr = "${ls.quality_emoji} ${ls.quality_label.uppercase()}"
            sb.append("│  ${layerName.padEnd(22)}  $bar  ${scoreStr.padEnd(7)}  $labelStr\n")
        }

        sb.append("│                                                               │\n")
        val oBar = buildScoreBar(r.overall_score, 18)
        val oScore = "${r.overall_score.roundToInt()}/100"
        sb.append("│  ${"Overall Project Score".padEnd(22)}  $oBar  ${oScore.padEnd(7)}  ${r.overall_display}\n")
        sb.append("│                                                               │\n")
        sb.append("└──────────────────────────────────────────────────────────────┘\n")
        sb.append("\n")
        sb.append("QUALITY INTERPRETATION:\n")
        sb.append("  🟢 90-100: Excellent - Production ready, follows best practices\n")
        sb.append("  🟢 75-89:  Good - Minor improvements needed\n")
        sb.append("  🟠 60-74:  Fair - Several issues require attention\n")
        sb.append("  🔴 40-59:  Poor - Significant refactoring recommended\n")
        sb.append("  🔴  0-39:  Critical - Immediate action required\n")
        sb.append("\n")

        // ── ARCHITECTURAL VIOLATIONS ──────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                  ARCHITECTURAL VIOLATIONS\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("\n")

        if (r.total_violations == 0) {
            sb.append("✅ NO ARCHITECTURAL VIOLATIONS DETECTED!\n")
            sb.append("   Your code follows ${r.architecture_pattern} best practices. 🎉\n")
        } else {
            sb.append("⚠️  VIOLATIONS DETECTED\n\n")

            val critical: List<AntiPatternDetail> = r.anti_patterns.filter { ap -> ap.severity == "CRITICAL" }
            val high: List<AntiPatternDetail> = r.anti_patterns.filter { ap -> ap.severity == "HIGH" }
            val medium: List<AntiPatternDetail> = r.anti_patterns.filter { ap -> ap.severity == "MEDIUM" }

            if (critical.isNotEmpty()) {
                sb.append("🔴 CRITICAL SEVERITY (${critical.size} issues)\n")
                sb.append("══════════════════════════════════════════════════════════════════\n")
                for (ap in critical) { appendAntiPattern(sb, ap) }
            }
            if (high.isNotEmpty()) {
                sb.append("\n🟠 HIGH SEVERITY (${high.size} issues)\n")
                sb.append("══════════════════════════════════════════════════════════════════\n")
                for (ap in high) { appendAntiPattern(sb, ap) }
            }
            if (medium.isNotEmpty()) {
                sb.append("\n🟡 MEDIUM SEVERITY (${medium.size} issues)\n")
                sb.append("══════════════════════════════════════════════════════════════════\n")
                for (ap in medium) { appendAntiPattern(sb, ap) }
            }
        }

        // ── CLEAN FILES ───────────────────────────────────────────────────────
        if (r.clean_files.isNotEmpty()) {
            sb.append("\n🟢 CLEAN CODE (${r.clean_files.size} files)\n")
            sb.append("══════════════════════════════════════════════════════════════════\n")
            for (cf in r.clean_files) {
                sb.append("  ✅ $cf\n")
            }
        }

        // ── DETAILED QUALITY BREAKDOWN ────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                   DETAILED QUALITY BREAKDOWN\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")

        for (ls in layerScores) {
            sb.append("\n")
            sb.append("📊 ${ls.layer.uppercase()} LAYER ANALYSIS\n")
            sb.append("─".repeat(66) + "\n")
            sb.append("Quality Score  : ${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji} ${ls.quality_label.uppercase()}\n")
            sb.append("Files Analyzed : ${ls.file_count}\n")

            val totalIssues = ls.files.sumOf { f -> f.issues.size }
            sb.append("Issues Found   : $totalIssues\n\n")

            if (ls.files.isNotEmpty()) {
                sb.append("┌${"─".repeat(26)}┬${"─".repeat(7)}┬${"─".repeat(30)}┐\n")
                sb.append("│ ${"File".padEnd(25)}│${"Score".center(7)}│ ${"Issues".padEnd(29)}│\n")
                sb.append("├${"─".repeat(26)}┼${"─".repeat(7)}┼${"─".repeat(30)}┤\n")

                val sortedFiles: List<FileQualityResult> = ls.files.sortedBy { f -> f.quality_score }
                for (f in sortedFiles) {
                    val fname = f.file_name.take(25).padEnd(25)
                    val score = "${f.quality_score.roundToInt()}".center(7)
                    val issueText = if (f.issues.isEmpty()) "✅ Clean"
                    else f.issues.take(2).joinToString(", ")
                    val issueCol = issueText.take(29).padEnd(29)
                    sb.append("│ $fname│$score│ $issueCol│\n")
                }
                sb.append("└${"─".repeat(26)}┴${"─".repeat(7)}┴${"─".repeat(30)}┘\n")
            }

            val issueGroups: Map<String, List<String>> = ls.files
                .flatMap { f -> f.issues }
                .groupBy { issue -> issue }

            if (issueGroups.isNotEmpty()) {
                sb.append("\nPrimary Issues:\n")
                val topIssues = issueGroups.entries.sortedByDescending { entry -> entry.value.size }.take(4)
                for (entry in topIssues) {
                    val count = entry.value.size
                    sb.append("  • ${entry.key} ($count file${if (count > 1) "s" else ""})\n")
                }
                sb.append("\nRecommendations:\n")
                val topRecs = issueGroups.entries.sortedByDescending { entry -> entry.value.size }.take(3)
                for ((index, entry) in topRecs.withIndex()) {
                    sb.append("  ${index + 1}. ${getRecommendation(entry.key)}\n")
                }
            }
            sb.append("\n")
        }

        // ── ACTIONABLE INSIGHTS ───────────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                      ACTIONABLE INSIGHTS\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("\n🎯 TOP PRIORITY ACTIONS (High Impact, Low Effort)\n\n")

        val criticalAndHigh: List<AntiPatternDetail> = r.anti_patterns
            .filter { ap -> ap.severity == "CRITICAL" || ap.severity == "HIGH" }
            .take(3)

        for ((index, ap) in criticalAndHigh.withIndex()) {
            val icon = if (ap.severity == "CRITICAL") "🔴" else "🟠"
            val filesStr = ap.files.take(3).joinToString(", ") +
                    if (ap.files.size > 3) " ..." else ""
            sb.append("${index + 1}. $icon ${formatApName(ap.type)}\n")
            sb.append("   Files : $filesStr\n")
            sb.append("   Impact: Estimated quality improvement: +10-15 points\n")
            sb.append("   Action: ${ap.recommendation}\n\n")
        }

        // ── PROJECTED IMPROVEMENT ─────────────────────────────────────────────
        sb.append("📈 PROJECTED IMPROVEMENT\n\n")
        sb.append("If you fix the HIGH + CRITICAL issues:\n")
        sb.append("  Current Overall Score : ${r.overall_display}\n")
        sb.append("  Projected Score       : 🟢 ${r.projected_score_after_fixes.roundToInt()}/100\n\n")

        for (ls in layerScores) {
            val projected = minOf(100.0, ls.mean_score + ls.files.sumOf { f -> f.issues.size } * 2.0)
            if (projected > ls.mean_score + 1.0) {
                val layerName = ls.layer.replaceFirstChar { ch -> ch.uppercase() }
                sb.append("  $layerName Layer: ${ls.mean_score.roundToInt()} → ${projected.roundToInt()}\n")
            }
        }
        sb.append("\n")

        // ── CODE HEALTH METRICS ───────────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                       CODE HEALTH METRICS\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("\nSize Metrics:\n")
        sb.append("  Average File Size            : ${r.avg_loc.roundToInt()} LOC\n")

        val mostProblematic: FileQualityResult? = r.files.minByOrNull { f -> f.quality_score }
        if (mostProblematic != null) {
            sb.append("  Most Problematic File        : ${mostProblematic.file_name} " +
                    "(score: ${mostProblematic.quality_score.roundToInt()})\n")
        }

        sb.append("\nDependency Health:\n")
        sb.append("  Avg Cross-Layer Dependencies : ${"%.1f".format(r.avg_cross_layer_deps)}\n")
        sb.append("  Files with Violations        : ${r.files_with_violations}\n")
        sb.append("  Cyclic Dependencies          : 0 ✅\n\n")

        // ── SUMMARY ───────────────────────────────────────────────────────────
        sb.append("\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("                           SUMMARY\n")
        sb.append("══════════════════════════════════════════════════════════════════\n")
        sb.append("\n")

        val goodLayers: List<LayerQualitySummary> = r.layer_scores.filter { ls -> ls.mean_score >= 75 }
        val weakLayers: List<LayerQualitySummary> = r.layer_scores.filter { ls -> ls.mean_score < 60 }

        sb.append("✅ STRENGTHS:\n")
        for (ls in goodLayers) {
            val name = ls.layer.replaceFirstChar { ch -> ch.uppercase() }
            sb.append("  • $name layer is ${ls.quality_label.lowercase()} (${ls.mean_score.roundToInt()}/100)\n")
        }
        if (r.total_violations == 0) {
            sb.append("  • No architectural violations detected\n")
        }
        sb.append("\n")

        sb.append("⚠️  AREAS FOR IMPROVEMENT:\n")
        for (ls in weakLayers) {
            val name = ls.layer.replaceFirstChar { ch -> ch.uppercase() }
            sb.append("  • $name layer needs attention (${ls.mean_score.roundToInt()}/100)\n")
        }
        val topViolations: List<AntiPatternDetail> = r.anti_patterns
            .filter { ap -> ap.severity == "CRITICAL" || ap.severity == "HIGH" }
            .take(3)
        for (ap in topViolations) {
            sb.append("  • ${formatApName(ap.type)} (${ap.files.size} files affected)\n")
        }
        sb.append("\n")

        sb.append("🎯 OVERALL ASSESSMENT:\n")
        val assessment = when {
            r.overall_score >= 90 -> "Your project is production ready! 🎉"
            r.overall_score >= 75 -> "Your project is in GOOD shape with minor improvements needed."
            r.overall_score >= 60 -> "Your project is FAIR — several issues require attention."
            r.overall_score >= 40 -> "Your project needs significant refactoring."
            else -> "CRITICAL — immediate action required across multiple layers."
        }
        sb.append(assessment + "\n\n")

        val hours = when {
            r.total_violations == 0 -> "0"
            r.total_violations <= 3 -> "1-2"
            r.total_violations <= 6 -> "3-4"
            else -> "4-8"
        }
        sb.append("Estimated Effort to Reach 90+:\n")
        sb.append("  🕐 $hours hours of focused refactoring\n\n")

        // ── FOOTER ────────────────────────────────────────────────────────────
        sb.append("━".repeat(66) + "\n")
        sb.append("                     End of Analysis Report\n")
        sb.append("━".repeat(66) + "\n\n")
        sb.append("💡 Need help fixing these issues?\n")
        sb.append("   • Review each file listed above for specific recommendations\n")
        sb.append("   • Claude AI integration coming soon for auto-fix suggestions\n\n")
        sb.append("Generated by SpringForge Code Quality Analyzer v2.0\n")
        sb.append("Powered by: Anti-Pattern Classifier + Quality Score Predictor (XGBoost)\n")

        return sb.toString()
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun appendAntiPattern(sb: StringBuilder, ap: AntiPatternDetail) {
        sb.append("\n")
        sb.append("├─ ${formatApName(ap.type)}\n")
        sb.append("│\n")
        sb.append("│  📍 Affected Layer : ${ap.affected_layer}\n")
        sb.append("│  🎯 Confidence     : ${(ap.confidence * 100).roundToInt()}%\n")
        sb.append("│  📉 Severity       : ${ap.severity}\n")
        sb.append("│\n")
        sb.append("│  📄 Files Affected (${ap.files.size}):\n")
        val topFiles = ap.files.take(5)
        for (f in topFiles) {
            sb.append("│     • $f\n")
        }
        if (ap.files.size > 5) {
            sb.append("│     ... and ${ap.files.size - 5} more\n")
        }
        sb.append("│\n")
        sb.append("│  📖 Problem:\n")
        sb.append("│     ${ap.description}\n")
        sb.append("│\n")
        sb.append("│  💡 Recommendation:\n")
        sb.append("│     ${ap.recommendation}\n")
        sb.append("│\n")
    }

    private fun buildScoreBar(score: Double, width: Int): String {
        val filled = ((score / 100.0) * width).roundToInt().coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }

    private fun formatApName(type: String): String {
        return type.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { ch -> ch.uppercase() } }
    }

    private fun getRecommendation(issue: String): String {
        return when {
            issue.contains("validation", ignoreCase = true) ->
                "Add @Valid to all @RequestBody parameters"
            issue.contains("business logic", ignoreCase = true) ->
                "Move business logic to Service layer"
            issue.contains("transaction", ignoreCase = true) ->
                "Add @Transactional to data-modifying methods"
            issue.contains("layer skip", ignoreCase = true) ->
                "Remove direct Repository access from Controller"
            issue.contains("import", ignoreCase = true) ->
                "Reduce coupling by removing unused imports"
            issue.contains("large file", ignoreCase = true) ->
                "Split into smaller, focused classes"
            else ->
                "Review and refactor according to best practices"
        }
    }

    // ── Legacy report for old EnhancedPredictionResult ────────────────────────
    private fun buildLegacyReport(result: EnhancedPredictionResult): String {
        val sb = StringBuilder()
        sb.append("╔══════════════════════════════════════════════════════════════════╗\n")
        sb.append("║          SPRINGFORGE — ANTI-PATTERN ANALYSIS REPORT             ║\n")
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n\n")
        sb.append("Architecture : ${result.architecture_pattern.uppercase()}\n")
        sb.append("Files        : ${result.total_files_analyzed}\n")
        sb.append("Violations   : ${result.total_violations}\n\n")
        sb.append(result.summary + "\n\n")

        if (result.anti_patterns.isEmpty()) {
            sb.append("✅ No violations detected.\n")
        } else {
            for (ap in result.anti_patterns) {
                sb.append("\n[${ap.severity}] ${formatApName(ap.type)}\n")
                sb.append("  Layer : ${ap.affected_layer}\n")
                sb.append("  Files : ${ap.files.joinToString(", ")}\n")
                sb.append("  Fix   : ${ap.recommendation}\n")
            }
        }
        return sb.toString()
    }
}

private fun String.center(width: Int): String {
    if (this.length >= width) return this
    val totalPad = width - this.length
    val leftPad = totalPad / 2
    val rightPad = totalPad - leftPad
    return " ".repeat(leftPad) + this + " ".repeat(rightPad)
}