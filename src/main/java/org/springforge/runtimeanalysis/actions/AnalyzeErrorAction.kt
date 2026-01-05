package org.springforge.runtimeanalysis.actions

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import org.springforge.runtimeanalysis.collector.ErrorCollector
import org.springforge.runtimeanalysis.network.NetworkClient
import org.springforge.runtimeanalysis.ui.AnalysisResultDialog
import org.springforge.runtimeanalysis.ui.SpringForgeNotifier

class AnalyzeErrorAction : AnAction("Analyze with SpringForge") {

    private val log = Logger.getInstance(AnalyzeErrorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(
                    project,
                    "Please select a stacktrace to analyze.",
                    "SpringForge"
            )
            return
        }

        val payload = ErrorCollector.buildErrorPayload(selectedText, project)
        val json = Gson().toJson(payload)

        // 🔔 Non-blocking notification
        SpringForgeNotifier.info(project, "Analyzing error with SpringForge AI…")

        ProgressManager.getInstance().run(object :
                Task.Backgroundable(project, "Analyzing with SpringForge") {

            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    //indicator.text = "Sending error to SpringForge AI..."

                    val response = NetworkClient.analyzeError(json)

                    ApplicationManager.getApplication().invokeLater {
                        AnalysisResultDialog(project, response.answer).show()
                    }

                } catch (ex: Exception) {
                    log.error("SpringForge request failed", ex)

                    ApplicationManager.getApplication().invokeLater {
                        SpringForgeNotifier.error(
                                project,
                                ex.message ?: "SpringForge analysis failed"
                        )
                    }
                }
            }
        })
    }

}

