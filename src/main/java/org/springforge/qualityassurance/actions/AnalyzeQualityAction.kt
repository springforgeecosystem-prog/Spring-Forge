// AnalyzeQualityAction.kt - FIXED VERSION
package org.springforge.qualityassurance.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.network.MLServiceClient
import org.springforge.qualityassurance.toolwindow.QualityToolWindowFactory
import org.springforge.qualityassurance.ui.ArchitectureSelectDialog

class AnalyzeQualityAction : AnAction("Analyze Code Quality") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        println("🟦 Starting Enhanced Quality Analysis...")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpringForge")
            .createNotification("Starting code quality analysis...", NotificationType.INFORMATION)
            .notify(project)

        // Architecture selection dialog
        val dialog = ArchitectureSelectDialog()
        if (!dialog.showAndGet()) return

        val architecture = dialog.getSelectedArchitecture()
        println("🟦 Selected architecture: $architecture")

        // Run analysis in background task
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Analyzing Project Code Quality...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Extracting features from Java files..."
                    indicator.fraction = 0.2

                    // ✅ FIX: Extract features inside ReadAction
                    val fileFeatures = ReadAction.compute<List<org.springforge.qualityassurance.model.FileFeatureModel>, Throwable> {
                        PsiFeatureExtractor.extractAllFiles(project, architecture)
                    }

                    println("🟦 Extracted features from ${fileFeatures.size} files")

                    if (fileFeatures.isEmpty()) {
                        showError(project, "No Java files found in project")
                        return
                    }

                    indicator.text = "Sending to ML service for analysis..."
                    indicator.fraction = 0.5

                    // Call ML service (no PSI access here, safe without ReadAction)
                    val result = MLServiceClient.analyzeProject(fileFeatures)

                    indicator.text = "Generating report..."
                    indicator.fraction = 0.8

                    println("🟩 Analysis complete: ${result.total_violations} violations found")

                    // Show results in tool window
                    val panel = QualityToolWindowFactory.getPanel(project)
                    panel?.showEnhancedResults(result)

                    indicator.fraction = 1.0

                    // Show summary notification
                    val notificationType = if (result.total_violations == 0) {
                        NotificationType.INFORMATION
                    } else if (result.anti_patterns.any { it.severity == "CRITICAL" }) {
                        NotificationType.ERROR
                    } else {
                        NotificationType.WARNING
                    }

                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("SpringForge")
                        .createNotification(
                            "Analysis Complete",
                            result.summary,
                            notificationType
                        )
                        .notify(project)

                } catch (ex: Exception) {
                    showError(project, "Error during analysis: ${ex.message}")
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun showError(project: com.intellij.openapi.project.Project, message: String) {
        println("❌ $message")

        val panel = QualityToolWindowFactory.getPanel(project)
        panel?.showMessage("❌ ERROR\n\n$message")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpringForge")
            .createNotification("Analysis Error", message, NotificationType.ERROR)
            .notify(project)
    }
}