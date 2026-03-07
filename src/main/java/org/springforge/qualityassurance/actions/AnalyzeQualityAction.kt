// src/main/java/org/springforge/qualityassurance/actions/AnalyzeQualityAction.kt
// REPLACE your existing AnalyzeQualityAction.kt with this file
package org.springforge.qualityassurance.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.springforge.cicdassistant.audit.AuditService
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.ProjectFixResult
import org.springforge.qualityassurance.network.MLServiceClient
import org.springforge.qualityassurance.toolwindow.QualityToolWindowFactory
import org.springforge.qualityassurance.toolwindow.QualityToolWindowPanel
import org.springforge.qualityassurance.ui.ArchitectureSelectDialog

class AnalyzeQualityAction : AnAction("Analyze Code Quality") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        println("🟦 Starting Combined Quality Analysis...")
        sendNotification(project, "Starting SpringForge code quality analysis...", NotificationType.INFORMATION)

        val dialog = ArchitectureSelectDialog()
        if (!dialog.showAndGet()) return
        val architecture = dialog.getSelectedArchitecture()
        println("🟦 Architecture: $architecture")

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "SpringForge: Analyzing Code Quality...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val startMs = System.currentTimeMillis()
                try {
                    // ── STEP 1: Ensure tool window is visible FIRST ───────────
                    // This must happen on EDT before we try to get the panel
                    showToolWindow(project)

                    // ── STEP 2: Extract PSI features ──────────────────────────
                    indicator.text    = "Scanning Java files..."
                    indicator.fraction = 0.10

                    val fileFeatures = ReadAction.compute<List<FileFeatureModel>, Throwable> {
                        PsiFeatureExtractor.extractAllFiles(project, architecture)
                    }

                    println("🟦 Extracted ${fileFeatures.size} files")

                    if (fileFeatures.isEmpty()) {
                        showError(project, "No Java files found in project.\nMake sure the project contains Spring Boot Java source files.")
                        return
                    }

                    // ── STEP 3: Run ML models ─────────────────────────────────
                    indicator.text     = "Running ML analysis (Anti-Pattern + Quality Score)..."
                    indicator.fraction = 0.40

                    val analysisResult = MLServiceClient.analyzeProjectFull(fileFeatures)

                    // ── STEP 4: Show initial report immediately on EDT ─────────
                    indicator.text     = "Generating report..."
                    indicator.fraction = 0.65

                    updatePanel(project) { panel ->
                        panel.showCombinedResults(analysisResult, fixResult = null)
                    }

                    println("🟩 Initial report shown — overall: ${analysisResult.overall_display}, violations: ${analysisResult.total_violations}")

                    // ── STEP 5: Call Gemini for AI fix suggestions ────────────
                    // If LLM validation was performed, fix_suggestions are already inline —
                    // skip the separate /generate-fixes call.
                    var aiFixCount = 0
                    if (analysisResult.anti_patterns.isNotEmpty() && !analysisResult.llm_enhanced) {
                        indicator.text     = "🤖 Generating AI fix suggestions (Gemini)..."
                        indicator.fraction = 0.80

                        try {
                            val sourceMap = fileFeatures.associate { it.file_name to (it.source_code ?: "") }
                                .filterValues { it.isNotBlank() }
                                .ifEmpty { null }
                            val fixResult = MLServiceClient.generateProjectFixes(analysisResult, sourceMap)
                            aiFixCount = fixResult.total_fixes
                            indicator.fraction = 0.95

                            // Update the panel on EDT with AI fixes
                            updatePanel(project) { panel ->
                                panel.showCombinedResults(analysisResult, fixResult)
                            }

                            println("🟩 AI fixes added — ${fixResult.total_fixes} suggestions, powered: ${fixResult.suggestions.count { it.ai_powered }}")

                        } catch (geminiEx: Exception) {
                            println("⚠️ Gemini unavailable: ${geminiEx.message}")
                            updatePanel(project) { panel ->
                                panel.showCombinedResults(
                                    analysisResult,
                                    fixResult      = null,
                                    geminiWarning  = "AI fix suggestions unavailable: ${geminiEx.message}"
                                )
                            }
                        }
                    } else if (analysisResult.llm_enhanced) {
                        aiFixCount = analysisResult.fix_suggestions.size
                        println("🟩 LLM-enhanced: ${analysisResult.fix_suggestions.size} inline fixes, ${analysisResult.false_positives_filtered} false positives filtered")
                    }

                    indicator.fraction = 1.0

                    // ── STEP 6: Send completion notification ──────────────────
                    val notifType = when {
                        analysisResult.anti_patterns.any { it.severity == "CRITICAL" } -> NotificationType.ERROR
                        analysisResult.total_violations > 0                            -> NotificationType.WARNING
                        else                                                            -> NotificationType.INFORMATION
                    }
                    sendNotification(
                        project,
                        "Analysis Complete — ${analysisResult.overall_display} | ${analysisResult.total_violations} violations in ${analysisResult.total_files_analyzed} files",
                        notifType
                    )

                    AuditService.getInstance(project).logQualityScan(
                        filesAnalyzed      = analysisResult.total_files_analyzed,
                        totalViolations    = analysisResult.total_violations,
                        criticalViolations = analysisResult.anti_patterns.count { it.severity == "CRITICAL" },
                        aiFixCount         = aiFixCount,
                        architecture       = architecture,
                        durationMs         = System.currentTimeMillis() - startMs,
                        success            = true
                    )

                } catch (ex: Exception) {
                    showError(
                        project,
                        "Analysis failed: ${ex.message}\n\nMake sure the ML Service is running on port 8081.\nStart it with: uvicorn app.main:app --port 8081 --reload"
                    )
                    AuditService.getInstance(project).logQualityScan(
                        filesAnalyzed = 0, totalViolations = 0, criticalViolations = 0,
                        aiFixCount = 0, architecture = architecture,
                        durationMs = System.currentTimeMillis() - startMs,
                        success = false, errorMsg = ex.message
                    )
                    ex.printStackTrace()
                }
            }
        })
    }

    /**
     * Ensures the SpringForgeQuality tool window is visible.
     * Must activate on EDT so the panel exists when we later call getPanel().
     */
    private fun showToolWindow(project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            try {
                val toolWindowManager = ToolWindowManager.getInstance(project)
                val tw = toolWindowManager.getToolWindow("SpringForgeQuality")
                tw?.show()
                tw?.activate(null)
            } catch (ex: Exception) {
                println("⚠️ Could not show tool window: ${ex.message}")
            }
        }
        // Small pause to let the tool window render before we write to it
        Thread.sleep(300)
    }

    /**
     * Runs the given block on the EDT (Event Dispatch Thread) with a valid panel.
     * This is the correct way to update Swing UI from a background thread in IntelliJ.
     *
     * Uses invokeAndWait so the background thread waits for the UI update to finish
     * before proceeding to the next step (prevents race conditions).
     */
    private fun updatePanel(project: Project, block: (QualityToolWindowPanel) -> Unit) {
        ApplicationManager.getApplication().invokeAndWait {
            try {
                val panel = getPanel(project)
                if (panel != null) {
                    block(panel)
                } else {
                    println("⚠️ Panel is null — tool window may not be visible yet")
                }
            } catch (ex: Exception) {
                println("⚠️ Error updating panel: ${ex.message}")
            }
        }
    }

    /**
     * Gets the QualityToolWindowPanel, trying multiple strategies.
     * The standard QualityToolWindowFactory.getPanel() can return null if the
     * content hasn't been selected. This tries harder.
     */
    private fun getPanel(project: Project): QualityToolWindowPanel? {
        // Strategy 1: Standard approach via factory
        val panel = QualityToolWindowFactory.getPanel(project)
        if (panel != null) return panel

        // Strategy 2: Get directly from tool window manager
        return try {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val tw = toolWindowManager.getToolWindow("SpringForgeQuality") ?: return null
            val contentManager = tw.contentManager
            // Try selected content first, then first content
            val content = contentManager.selectedContent ?: contentManager.getContent(0) ?: return null
            content.component as? QualityToolWindowPanel
        } catch (ex: Exception) {
            println("⚠️ Could not get panel via ToolWindowManager: ${ex.message}")
            null
        }
    }

    private fun showError(project: Project, message: String) {
        println("❌ $message")
        updatePanel(project) { panel ->
            panel.showMessage("❌ ANALYSIS ERROR\n\n$message")
        }
        sendNotification(project, message, NotificationType.ERROR)
    }

    private fun sendNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpringForge")
            .createNotification(message, type)
            .notify(project)
    }
}