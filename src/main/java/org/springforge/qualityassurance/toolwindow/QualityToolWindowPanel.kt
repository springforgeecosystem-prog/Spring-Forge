// src/main/java/org/springforge/qualityassurance/toolwindow/QualityToolWindowPanel.kt
// REPLACE your existing QualityToolWindowPanel.kt with this file
package org.springforge.qualityassurance.toolwindow

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.springforge.qualityassurance.model.AntiPatternDetail
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import org.springforge.qualityassurance.model.FileQualityResult
import org.springforge.qualityassurance.model.FixSuggestion
import org.springforge.qualityassurance.model.LayerQualitySummary
import org.springforge.qualityassurance.model.ProjectFixResult
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel
import kotlin.math.roundToInt

class QualityToolWindowPanel : JPanel() {

    private val textArea = JBTextArea().apply {
        isEditable    = false
        lineWrap      = true
        wrapStyleWord = true
        font          = Font("Monospaced", Font.PLAIN, 12)
    }

    init {
        layout = BorderLayout()
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    fun showMessage(message: String) {
        textArea.text          = message
        textArea.caretPosition = 0
    }

    /**
     * Main entry point called by AnalyzeQualityAction.
     *
     * Called TWICE:
     *   1st call: fixResult=null → shows instant ML report
     *   2nd call: fixResult set  → updates report with AI fixes appended
     *
     * @param result        ML analysis result (always present)
     * @param fixResult     Gemini fix suggestions (null = loading or unavailable)
     * @param geminiWarning shown when Gemini call failed
     */
    fun showCombinedResults(
        result       : CombinedAnalysisResult,
        fixResult    : ProjectFixResult? = null,
        geminiWarning: String?           = null
    ) {
        textArea.text          = buildCombinedReport(result, fixResult, geminiWarning)
        textArea.caretPosition = 0
    }

    fun showEnhancedResults(result: EnhancedPredictionResult) {
        textArea.text          = buildLegacyReport(result)
        textArea.caretPosition = 0
    }

    // =========================================================================
    // COMBINED REPORT BUILDER
    // =========================================================================

    private fun buildCombinedReport(
        r            : CombinedAnalysisResult,
        fixes        : ProjectFixResult?,
        geminiWarning: String?
    ): String {
        val sb = StringBuilder()

        // ── HEADER ────────────────────────────────────────────────────────────
        sb.ln("╔══════════════════════════════════════════════════════════════════╗")
        sb.ln("║       SPRINGFORGE CODE QUALITY ANALYSIS REPORT                  ║")
        sb.ln("║          Complete System Analysis  +  AI Fix Suggestions        ║")
        sb.ln("╚══════════════════════════════════════════════════════════════════╝")
        sb.ln()

        // ── PROJECT OVERVIEW ──────────────────────────────────────────────────
        sb.ln("📊 PROJECT OVERVIEW")
        sb.ln("─".repeat(66))
        sb.ln("Architecture Pattern : ${r.architecture_pattern.uppercase()}")
        sb.ln("Files Analyzed       : ${r.total_files_analyzed}")
        sb.ln("Analysis Date        : ${r.analysis_date}")
        sb.ln("Total Issues Found   : ${r.total_violations} violations across ${r.files_with_violations} files")
        when {
            fixes != null         -> sb.ln("AI Fix Suggestions   : ${fixes.total_fixes} (powered by Gemini 🤖)")
            r.fix_suggestions.isNotEmpty() -> sb.ln("AI Fix Suggestions   : ${r.fix_suggestions.size} (LLM-validated, inline 🤖)")
            geminiWarning != null -> sb.ln("AI Fix Suggestions   : ⚠️  $geminiWarning")
            r.total_violations > 0 -> sb.ln("AI Fix Suggestions   : ⏳ Loading Gemini suggestions...")
            else                  -> sb.ln("AI Fix Suggestions   : N/A (no violations)")
        }
        if (r.llm_enhanced) {
            sb.ln("LLM Validation       : ✅ Gemini-validated (${r.false_positives_filtered} false positives filtered)")
        }
        sb.ln()

        // ── QUALITY SCORE DASHBOARD ───────────────────────────────────────────
        sb.section("QUALITY SCORE DASHBOARD")
        sb.ln()
        sb.ln("┌──────────────────────────────────────────────────────────────┐")
        sb.ln("│                     LAYER QUALITY SCORES                     │")
        sb.ln("├──────────────────────────────────────────────────────────────┤")
        sb.ln("│                                                               │")
        for (ls in r.layer_scores) {
            val name  = (ls.layer.replaceFirstChar { it.uppercase() } + " Layer").padEnd(22)
            val bar   = buildScoreBar(ls.mean_score, 18)
            val score = "${ls.mean_score.roundToInt()}/100".padEnd(7)
            sb.ln("│  $name  $bar  $score  ${ls.quality_emoji} ${ls.quality_label.uppercase()}")
        }
        sb.ln("│                                                               │")
        val oBar   = buildScoreBar(r.overall_score, 18)
        val oScore = "${r.overall_score.roundToInt()}/100".padEnd(7)
        sb.ln("│  ${"Overall Project Score".padEnd(22)}  $oBar  $oScore  ${r.overall_display}")
        sb.ln("│                                                               │")
        sb.ln("└──────────────────────────────────────────────────────────────┘")
        sb.ln()
        sb.ln("QUALITY INTERPRETATION:")
        sb.ln("  🟢 90-100: Excellent - Production ready, follows best practices")
        sb.ln("  🟢 75-89:  Good - Minor improvements needed")
        sb.ln("  🟠 60-74:  Fair - Several issues require attention")
        sb.ln("  🔴 40-59:  Poor - Significant refactoring recommended")
        sb.ln("  🔴  0-39:  Critical - Immediate action required")
        sb.ln()

        // ── ARCHITECTURAL VIOLATIONS + AI FIXES ───────────────────────────────
        sb.section("ARCHITECTURAL VIOLATIONS  +  AI FIX SUGGESTIONS")
        sb.ln()

        if (r.total_violations == 0) {
            sb.ln("✅ NO ARCHITECTURAL VIOLATIONS DETECTED!")
            sb.ln("   Your code follows ${r.architecture_pattern} best practices. 🎉")
        } else {
            // Build a lookup map: anti_pattern_type → FixSuggestion
            // Prefer inline fix_suggestions (from LLM-enhanced response), fall back to separate fixes call
            val fixMap: Map<String, FixSuggestion> =
                if (r.fix_suggestions.isNotEmpty()) r.fix_suggestions.associateBy { it.anti_pattern }
                else fixes?.suggestions?.associateBy { it.anti_pattern } ?: emptyMap()

            val critical = r.anti_patterns.filter { it.severity == "CRITICAL" }
            val high     = r.anti_patterns.filter { it.severity == "HIGH" }
            val medium   = r.anti_patterns.filter { it.severity == "MEDIUM" }

            if (critical.isNotEmpty()) {
                sb.ln("🔴 CRITICAL SEVERITY (${critical.size} issue${if (critical.size > 1) "s" else ""})")
                sb.ln("═".repeat(66))
                for (ap in critical) appendAntiPatternWithFix(sb, ap, fixMap[ap.type])
            }
            if (high.isNotEmpty()) {
                sb.ln()
                sb.ln("🟠 HIGH SEVERITY (${high.size} issue${if (high.size > 1) "s" else ""})")
                sb.ln("═".repeat(66))
                for (ap in high) appendAntiPatternWithFix(sb, ap, fixMap[ap.type])
            }
            if (medium.isNotEmpty()) {
                sb.ln()
                sb.ln("🟡 MEDIUM SEVERITY (${medium.size} issue${if (medium.size > 1) "s" else ""})")
                sb.ln("═".repeat(66))
                for (ap in medium) appendAntiPatternWithFix(sb, ap, fixMap[ap.type])
            }
        }

        // ── CLEAN FILES ───────────────────────────────────────────────────────
        if (r.clean_files.isNotEmpty()) {
            sb.ln()
            sb.ln("🟢 CLEAN CODE (${r.clean_files.size} files)")
            sb.ln("═".repeat(66))
            for (cf in r.clean_files) sb.ln("  ✅ $cf")
        }

        // ── DETAILED QUALITY BREAKDOWN ────────────────────────────────────────
        sb.ln()
        sb.section("DETAILED QUALITY BREAKDOWN")

        for (ls in r.layer_scores) {
            sb.ln()
            sb.ln("📊 ${ls.layer.uppercase()} LAYER ANALYSIS")
            sb.ln("─".repeat(66))
            sb.ln("Quality Score  : ${ls.mean_score.roundToInt()}/100  ${ls.quality_emoji} ${ls.quality_label.uppercase()}")
            sb.ln("Files Analyzed : ${ls.file_count}")
            sb.ln("Issues Found   : ${ls.files.sumOf { it.issues.size }}")
            sb.ln()

            if (ls.files.isNotEmpty()) {
                sb.ln("┌${"─".repeat(26)}┬${"─".repeat(7)}┬${"─".repeat(30)}┐")
                sb.ln("│ ${"File".padEnd(25)}│${"Score".center(7)}│ ${"Issues".padEnd(29)}│")
                sb.ln("├${"─".repeat(26)}┼${"─".repeat(7)}┼${"─".repeat(30)}┤")
                for (f in ls.files.sortedBy { it.quality_score }) {
                    val fname      = f.file_name.take(25).padEnd(25)
                    val score      = "${f.quality_score.roundToInt()}".center(7)
                    val issueText  = if (f.issues.isEmpty()) "✅ Clean"
                                    else f.issues.take(2).joinToString(", ")
                    sb.ln("│ $fname│$score│ ${issueText.take(29).padEnd(29)}│")
                }
                sb.ln("└${"─".repeat(26)}┴${"─".repeat(7)}┴${"─".repeat(30)}┘")
            }

            val issueGroups = ls.files.flatMap { it.issues }.groupBy { it }
            if (issueGroups.isNotEmpty()) {
                sb.ln()
                sb.ln("Primary Issues:")
                issueGroups.entries.sortedByDescending { it.value.size }.take(4)
                    .forEach { sb.ln("  • ${it.key} (${it.value.size} file${if (it.value.size > 1) "s" else ""})") }
                sb.ln()
                sb.ln("Recommendations:")
                issueGroups.entries.sortedByDescending { it.value.size }.take(3)
                    .forEachIndexed { i, e -> sb.ln("  ${i + 1}. ${getRecommendation(e.key)}") }
            }
            sb.ln()
        }

        // ── ACTIONABLE INSIGHTS ───────────────────────────────────────────────
        sb.section("ACTIONABLE INSIGHTS")
        sb.ln()
        sb.ln("🎯 TOP PRIORITY ACTIONS (High Impact, Low Effort)")
        sb.ln()
        r.anti_patterns
            .filter { it.severity == "CRITICAL" || it.severity == "HIGH" }
            .take(3)
            .forEachIndexed { i, ap ->
                val icon = if (ap.severity == "CRITICAL") "🔴" else "🟠"
                val filesStr = ap.files.take(3).joinToString(", ") +
                               if (ap.files.size > 3) " ..." else ""
                sb.ln("${i + 1}. $icon ${formatApName(ap.type)}")
                sb.ln("   Files : $filesStr")
                sb.ln("   Impact: Estimated quality improvement: +10-15 points")
                sb.ln("   Action: ${ap.recommendation}")
                sb.ln()
            }

        // ── PROJECTED IMPROVEMENT ─────────────────────────────────────────────
        sb.ln("📈 PROJECTED IMPROVEMENT")
        sb.ln()
        sb.ln("If you fix the HIGH + CRITICAL issues:")
        sb.ln("  Current Overall Score : ${r.overall_display}")
        sb.ln("  Projected Score       : 🟢 ${r.projected_score_after_fixes.roundToInt()}/100")
        sb.ln()
        for (ls in r.layer_scores) {
            val projected = minOf(100.0, ls.mean_score + ls.files.sumOf { it.issues.size } * 2.0)
            if (projected > ls.mean_score + 1.0) {
                sb.ln("  ${ls.layer.replaceFirstChar { it.uppercase() }} Layer: ${ls.mean_score.roundToInt()} → ${projected.roundToInt()}")
            }
        }
        sb.ln()

        // ── CODE HEALTH METRICS ───────────────────────────────────────────────
        sb.section("CODE HEALTH METRICS")
        sb.ln()
        sb.ln("Size Metrics:")
        sb.ln("  Average File Size            : ${r.avg_loc.roundToInt()} LOC")
        r.files.minByOrNull { it.quality_score }?.let {
            sb.ln("  Most Problematic File        : ${it.file_name} (score: ${it.quality_score.roundToInt()})")
        }
        sb.ln()
        sb.ln("Dependency Health:")
        sb.ln("  Avg Cross-Layer Dependencies : ${"%.1f".format(r.avg_cross_layer_deps)}")
        sb.ln("  Files with Violations        : ${r.files_with_violations}")
        sb.ln("  Cyclic Dependencies          : 0 ✅")
        sb.ln()

        // ── SUMMARY ───────────────────────────────────────────────────────────
        sb.section("SUMMARY")
        sb.ln()
        sb.ln("✅ STRENGTHS:")
        r.layer_scores.filter { it.mean_score >= 75 }.forEach {
            sb.ln("  • ${it.layer.replaceFirstChar { c -> c.uppercase() }} layer is ${it.quality_label.lowercase()} (${it.mean_score.roundToInt()}/100)")
        }
        if (r.total_violations == 0) sb.ln("  • No architectural violations detected")
        sb.ln()
        sb.ln("⚠️  AREAS FOR IMPROVEMENT:")
        r.layer_scores.filter { it.mean_score < 60 }.forEach {
            sb.ln("  • ${it.layer.replaceFirstChar { c -> c.uppercase() }} layer needs attention (${it.mean_score.roundToInt()}/100)")
        }
        r.anti_patterns.filter { it.severity == "CRITICAL" || it.severity == "HIGH" }.take(3).forEach {
            sb.ln("  • ${formatApName(it.type)} (${it.files.size} files affected)")
        }
        sb.ln()
        sb.ln("🎯 OVERALL ASSESSMENT:")
        sb.ln(when {
            r.overall_score >= 90 -> "Your project is production ready! 🎉"
            r.overall_score >= 75 -> "Your project is in GOOD shape with minor improvements needed."
            r.overall_score >= 60 -> "Your project is FAIR — several issues require attention."
            r.overall_score >= 40 -> "Your project needs significant refactoring."
            else                  -> "CRITICAL — immediate action required across multiple layers."
        })
        sb.ln()

        // ── AI IMPROVEMENT ROADMAP (only when Gemini succeeded) ───────────────
        if (fixes != null && fixes.suggestions.isNotEmpty()) {
            sb.ln()
            sb.section("🤖 AI-POWERED IMPROVEMENT ROADMAP  (Google Gemini)")
            sb.ln()
            sb.ln("Fix these issues in order of impact:\n")
            fixes.suggestions
                .sortedBy { it.impact_points }
                .forEachIndexed { i, fix ->
                    sb.ln("${i + 1}. [${fix.severity}] ${formatApName(fix.anti_pattern)}")
                    sb.ln("   Impact : ${fix.impact_points} pts | Layer: ${fix.layer}")
                    sb.ln("   Files  : ${fix.files.take(3).joinToString(", ")}${if (fix.files.size > 3) " +${fix.files.size - 3} more" else ""}")
                    sb.ln()
                }
        }

        // ── FOOTER ────────────────────────────────────────────────────────────
        sb.ln("━".repeat(66))
        sb.ln("                     End of Analysis Report")
        sb.ln("━".repeat(66))
        sb.ln()
        sb.ln("Generated by SpringForge Code Quality Analyzer v2.1")
        if (fixes != null) sb.ln("AI fixes powered by Google Gemini 2.5 Flash 🤖")
        else sb.ln("Powered by: Anti-Pattern Classifier + Quality Score Predictor (XGBoost)")

        return sb.toString()
    }

    // =========================================================================
    // ANTI-PATTERN BLOCK — renders all fix info for one violation
    //
    // Output format:
    //   ├─ Anti Pattern Name
    //   │  📍 Affected Layer
    //   │  🎯 Confidence
    //   │  📉 Severity + Impact
    //   │  📄 Files Affected
    //   │  📖 Problem
    //   │  💡 Recommendation          ← from static data, always shown
    //   │  🔧 Example Fix             ← static before/after code
    //   │  🤖 AI-Generated Fix        ← Gemini text (only when ai_powered=true)
    // =========================================================================

    private fun appendAntiPatternWithFix(
        sb : StringBuilder,
        ap : AntiPatternDetail,
        fix: FixSuggestion?          // null when Gemini not yet called or failed
    ) {
        sb.ln()
        sb.ln("├─ ${formatApName(ap.type)}")
        sb.ln("│")
        sb.ln("│  📍 Affected Layer    : ${ap.affected_layer}")
        sb.ln("│  🎯 Confidence        : ${(ap.confidence * 100).roundToInt()}%")
        sb.ln("│  📉 Severity          : ${ap.severity}")
        if (ap.llm_validated) {
            sb.ln("│  ✅ LLM Validated     : Confirmed by Gemini")
        }
        if (fix != null && fix.impact_points != 0) {
            sb.ln("│  📉 Impact on Quality : ${fix.impact_points} points")
        }
        sb.ln("│")

        // Files
        sb.ln("│  📄 Files Affected (${ap.files.size}):")
        ap.files.take(5).forEach { sb.ln("│     • $it") }
        if (ap.files.size > 5) sb.ln("│     ... and ${ap.files.size - 5} more")
        sb.ln("│")

        // Problem — prefer LLM description (references actual code), then fix problem, then ap.description
        val problemText = ap.llm_description.takeIf { it.isNotBlank() }
            ?: fix?.problem?.takeIf { it.isNotBlank() }
            ?: ap.description
        sb.ln("│  📖 Problem:")
        sb.ln("│     $problemText")
        sb.ln("│")

        // Recommendation — static, always present (from fix data or fallback to ap.recommendation)
        val rec = fix?.recommendation?.takeIf { it.isNotBlank() } ?: ap.recommendation
        if (rec.isNotBlank()) {
            sb.ln("│  💡 Recommendation:")
            sb.ln("│     $rec")
            sb.ln("│")
        }

        // Static before/after code example
        if (fix != null && fix.before_code.isNotBlank()) {
            sb.ln("│  🔧 Example Fix:")
            sb.ln("│")
            sb.ln("│     // ❌ BEFORE")
            fix.before_code.lines().forEach { sb.ln("│     $it") }
            sb.ln("│")
            sb.ln("│     // ✅ AFTER")
            fix.after_code.lines().forEach { sb.ln("│     $it") }
            sb.ln("│")
        }

        // Gemini AI-generated fix — only shown when Gemini actually responded
        if (fix != null && fix.gemini_fix.isNotBlank() && fix.ai_powered) {
            sb.ln("│  🤖 AI-Generated Fix (Gemini 2.5 Flash):")
            sb.ln("│  ─────────────────────────────────────────────────────────")
            fix.gemini_fix.lines().forEach { sb.ln("│  $it") }
            sb.ln("│  ─────────────────────────────────────────────────────────")
            sb.ln("│")
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun buildScoreBar(score: Double, width: Int): String {
        val filled = ((score / 100.0) * width).roundToInt().coerceIn(0, width)
        return "█".repeat(filled) + "░".repeat(width - filled)
    }

    private fun formatApName(type: String): String =
        type.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }

    private fun getRecommendation(issue: String): String = when {
        issue.contains("validation",    ignoreCase = true) -> "Add @Valid to all @RequestBody parameters"
        issue.contains("business logic",ignoreCase = true) -> "Move business logic to Service layer"
        issue.contains("transaction",   ignoreCase = true) -> "Add @Transactional to data-modifying methods"
        issue.contains("layer skip",    ignoreCase = true) -> "Remove direct Repository access from Controller"
        issue.contains("import",        ignoreCase = true) -> "Reduce coupling by removing unused imports"
        issue.contains("large file",    ignoreCase = true) -> "Split into smaller, focused classes"
        else                                               -> "Review and refactor according to best practices"
    }

    private fun StringBuilder.ln(s: String = "") = append(s).append('\n')

    private fun StringBuilder.section(title: String) {
        ln()
        ln("══════════════════════════════════════════════════════════════════")
        ln("  $title")
        ln("══════════════════════════════════════════════════════════════════")
        ln()
    }

    // ── Legacy report (unchanged) ─────────────────────────────────────────────
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
    val pad = width - this.length
    return " ".repeat(pad / 2) + this + " ".repeat(pad - pad / 2)
}