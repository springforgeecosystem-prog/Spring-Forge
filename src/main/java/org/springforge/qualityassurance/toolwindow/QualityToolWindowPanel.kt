// QualityToolWindowPanel.kt - ENHANCED VERSION
package org.springforge.qualityassurance.toolwindow

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JPanel

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
    }

    fun showEnhancedResults(result: EnhancedPredictionResult) {
        val output = buildString {
            appendLine("╔════════════════════════════════════════════════════════════════╗")
            appendLine("║          SPRINGFORGE CODE QUALITY ANALYSIS REPORT              ║")
            appendLine("╚════════════════════════════════════════════════════════════════╝")
            appendLine()

            // Summary section
            appendLine("📊 SUMMARY")
            appendLine("─".repeat(64))
            appendLine("Architecture Pattern: ${result.architecture_pattern.uppercase()}")
            appendLine("Files Analyzed: ${result.total_files_analyzed}")
            appendLine("Total Violations: ${result.total_violations}")
            appendLine()

            if (result.total_violations == 0) {
                appendLine("✅ NO ARCHITECTURE VIOLATIONS DETECTED!")
                appendLine()
                appendLine("Your code follows ${result.architecture_pattern} architectural")
                appendLine("best practices. Great job! 🎉")
            } else {
                appendLine("⚠️  ARCHITECTURE VIOLATIONS DETECTED")
                appendLine()

                // Group by severity
                val critical = result.anti_patterns.filter { it.severity == "CRITICAL" }
                val high = result.anti_patterns.filter { it.severity == "HIGH" }
                val medium = result.anti_patterns.filter { it.severity == "MEDIUM" }

                // Critical issues
                if (critical.isNotEmpty()) {
                    appendLine()
                    appendLine("🔴 CRITICAL SEVERITY (${critical.size})")
                    appendLine("═".repeat(64))
                    critical.forEach { ap -> appendAntiPattern(ap) }
                }

                // High severity issues
                if (high.isNotEmpty()) {
                    appendLine()
                    appendLine("🟠 HIGH SEVERITY (${high.size})")
                    appendLine("═".repeat(64))
                    high.forEach { ap -> appendAntiPattern(ap) }
                }

                // Medium severity issues
                if (medium.isNotEmpty()) {
                    appendLine()
                    appendLine("🟡 MEDIUM SEVERITY (${medium.size})")
                    appendLine("═".repeat(64))
                    medium.forEach { ap -> appendAntiPattern(ap) }
                }
            }

            appendLine()
            appendLine("─".repeat(64))
            appendLine("Analysis completed successfully ✓")
        }

        textArea.text = output
    }

    private fun StringBuilder.appendAntiPattern(ap: org.springforge.qualityassurance.model.AntiPatternDetail) {
        appendLine()
        appendLine("├─ ${formatAntiPatternName(ap.type)}")
        appendLine("│  ")
        appendLine("│  📍 Affected Layer: ${ap.affected_layer}")
        appendLine("│  🎯 Confidence: ${(ap.confidence * 100).toInt()}%")
        appendLine("│  ")
        appendLine("│  📄 Files (${ap.files.size}):")
        ap.files.take(5).forEach { file ->
            appendLine("│     • $file")
        }
        if (ap.files.size > 5) {
            appendLine("│     ... and ${ap.files.size - 5} more files")
        }
        appendLine("│  ")
        appendLine("│  📖 Description:")
        appendLine("│     ${ap.description}")
        appendLine("│  ")
        appendLine("│  💡 Recommendation:")
        appendLine("│     ${ap.recommendation}")
        appendLine("│  ")
    }

    private fun formatAntiPatternName(type: String): String {
        return type
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
    }
}