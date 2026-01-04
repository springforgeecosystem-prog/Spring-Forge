package org.springforge.runtimeanalysis.service

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.springforge.runtimeanalysis.collector.ErrorCollector
import org.springforge.runtimeanalysis.network.NetworkClient

object RuntimeAnalysisService {

    private val log = Logger.getInstance(RuntimeAnalysisService::class.java)

    fun analyze(
            project: Project,
            stacktrace: String,
            onResult: (String) -> Unit,
            onError: (String) -> Unit
    ) {
        val payload = ErrorCollector.buildErrorPayload(stacktrace, project)
        val json = Gson().toJson(payload)

        ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, "Analyzing with SpringForge") {

                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        try {
                            val response = NetworkClient.analyzeError(json)

                            ApplicationManager.getApplication().invokeLater {
                                onResult(response.answer)
                            }

                        } catch (ex: Exception) {
                            log.error("SpringForge analysis failed", ex)

                            ApplicationManager.getApplication().invokeLater {
                                onError(ex.message ?: "Analysis failed")
                            }
                        }
                    }
                }
        )
    }
}
