package org.springforge.runtimeanalysis.actions

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.springforge.runtimeanalysis.collector.ErrorCollector

class AnalyzeErrorAction : AnAction("Analyze with SpringForge") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
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

        println("=== SPRINGFORGE PAYLOAD ===")
        println(json)

        Messages.showInfoMessage(
            project,
            "Payload generated. Check IDE logs.",
            "SpringForge"
        )
    }
}
