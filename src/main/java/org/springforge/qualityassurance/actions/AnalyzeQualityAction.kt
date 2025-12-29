package org.springforge.qualityassurance.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.springforge.qualityassurance.analysis.PsiFeatureExtractor
import org.springforge.qualityassurance.network.MLServiceClient
import org.springforge.qualityassurance.toolwindow.QualityToolWindowFactory
import org.springforge.qualityassurance.ui.ArchitectureSelectDialog

class AnalyzeQualityAction : AnAction("Analyze Code Quality") {

    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project ?: return

        println("🟦 Starting Quality Analysis...")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SpringForge")
            .createNotification("AnalyzeQualityAction Triggered", NotificationType.INFORMATION)
            .notify(project)

        // Architecture dialog
        val dialog = ArchitectureSelectDialog()
        if (!dialog.showAndGet()) return

        val architecture = dialog.getSelectedArchitecture()
        println("🟦 Selected architecture: $architecture")

        // Extract features
        val features = PsiFeatureExtractor.extract(project, architecture)
        println("🟦 Extracted Features: $features")

        // Get ToolWindow panel SAFELY
        val panel = QualityToolWindowFactory.getPanel(project)
        panel?.showMessage("Extracted Features:\n$features")

        try {
            val result = MLServiceClient.predict(features)
            println("🟩 ML Prediction: ${result.anti_pattern}")

            panel?.showMessage("Detected Anti-Pattern:\n${result.anti_pattern}")

            NotificationGroupManager.getInstance()
                .getNotificationGroup("SpringForge")
                .createNotification("Anti Pattern: ${result.anti_pattern}", NotificationType.INFORMATION)
                .notify(project)

        } catch (ex: Exception) {

            val error = "Error calling ML Service: ${ex.message}"
            println("❌ $error")
            ex.printStackTrace()

            panel?.showMessage(error)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("SpringForge")
                .createNotification(error, NotificationType.ERROR)
                .notify(project)
        }
    }
}
