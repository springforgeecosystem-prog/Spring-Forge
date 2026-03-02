// AnalyzeQualityAction.kt
package org.springforge.qualityassurance.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.network.MLServiceClient
import org.springforge.qualityassurance.toolwindow.QualityToolWindowFactory
import org.springforge.qualityassurance.ui.ArchitectureSelectDialog

class AnalyzeQualityAction : AnAction("Analyze Code Quality") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        println("🟦 Starting Combined Quality Analysis...")

        sendNotification(project, "Starting SpringForge code quality analysis...",
            NotificationType.INFORMATION)

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
                try {
                    indicator.text = "Scanning Java files..."
                    indicator.fraction = 0.15

                    val fileFeatures = ReadAction.compute<List<FileFeatureModel>, Throwable> {
                        PsiFeatureExtractor.extractAllFiles(project, architecture)
                    }

                    println("🟦 Extracted ${fileFeatures.size} files")

                    if (fileFeatures.isEmpty()) {
                        showError(
                            project,
                            "No Java files found in project.\n" +
                            "Make sure the project contains Spring Boot Java source files."
                        )
                        return
                    }

                    indicator.text = "Running ML analysis (Anti-Pattern + Quality Score)..."
                    indicator.fraction = 0.45

                    val result = MLServiceClient.analyzeProjectFull(fileFeatures)

                    indicator.text = "Generating report..."
                    indicator.fraction = 0.85

                    val panel = QualityToolWindowFactory.getPanel(project)
                    panel?.showCombinedResults(result)

                    indicator.fraction = 1.0

                    println("🟩 Done — overall: ${result.overall_display}, violations: ${result.total_violations}")

                    val notifType = when {
                        result.anti_patterns.any { it.severity == "CRITICAL" } -> NotificationType.ERROR
                        result.total_violations > 0 -> NotificationType.WARNING
                        else -> NotificationType.INFORMATION
                    }

                    sendNotification(
                        project,
                        "Analysis Complete — ${result.overall_display} | " +
                        "${result.total_violations} violations in ${result.total_files_analyzed} files",
                        notifType
                    )

                } catch (ex: Exception) {
                    showError(
                        project,
                        "Analysis failed: ${ex.message}\n\n" +
                        "Make sure the ML Service is running on port 8081.\n" +
                        "Start it with: uvicorn app.main:app --port 8081 --reload"
                    )
                    ex.printStackTrace()
                }
            }
        })
    }

    private fun showError(project: Project, message: String) {
        println("❌ $message")
        val panel = QualityToolWindowFactory.getPanel(project)
        panel?.showMessage("❌ ANALYSIS ERROR\n\n$message")
        sendNotification(project, message, NotificationType.ERROR)
    }

    private fun sendNotification(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpringForge")
            .createNotification(message, type)
            .notify(project)
    }
}