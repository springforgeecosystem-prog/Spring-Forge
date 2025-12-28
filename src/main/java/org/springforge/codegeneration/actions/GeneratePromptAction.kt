package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import org.springforge.codegeneration.analysis.ProjectContextBuilder
import org.springforge.codegeneration.parser.YamlParser
import org.springforge.codegeneration.service.PromptBuilder
import org.springforge.codegeneration.validation.ValidationResult
import org.springforge.codegeneration.validation.YamlValidator
import java.io.File

class GeneratePromptAction : AnAction("Generate Prompt", "Build prompt combining YAML + project analysis", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val baseDir = project.basePath ?: return

        val yamlFile = File(baseDir, "input.yml")
        if (!yamlFile.exists()) {
            notify(project, "input.yml not found", NotificationType.ERROR)
            return
        }

        val yamlText = yamlFile.readText()
        val parsed = YamlParser.parse(yamlText)

        if (!parsed.isValid) {
            notify(project, "YAML parse error: ${parsed.errorMessage}", NotificationType.ERROR)
            return
        }

        // VALIDATION HANDLING FIX
        val validated = YamlValidator.validateAndFix(parsed.data!!)
        val yamlModel = when (validated) {
            is ValidationResult.AutoFixed -> validated.fixedModel
            is ValidationResult.Valid -> parsed.data!!
            is ValidationResult.Invalid -> {
                notify(project, validated.errors.joinToString("\n") { it.message }, NotificationType.ERROR)
                return
            }
        }

        // Run static project analyzer
        val analysis = ProjectContextBuilder.build(project)

        // Build prompt
        val prompt = PromptBuilder.buildPrompt(yamlModel, analysis)

        // Save prompt to temporary file
        val promptFile = File(baseDir, "springforge_prompt.txt")
        promptFile.writeText(prompt)

        // Open in Editor
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(promptFile)
        if (vFile != null) {
            FileEditorManager.getInstance(project).openFile(vFile, true)
        }

        notify(project, "Prompt generated at springforge_prompt.txt", NotificationType.INFORMATION)
    }

    private fun notify(project: com.intellij.openapi.project.Project, msg: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("SpringForge", "Prompt Builder", msg, type),
            project
        )
    }
}
