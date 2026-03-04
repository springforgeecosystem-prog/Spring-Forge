package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import org.springforge.codegeneration.analysis.ExistingEntityExtractor
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

        val validated = YamlValidator.validateAndFix(parsed.data!!)
        val yamlModel = when (validated) {
            is ValidationResult.AutoFixed -> validated.fixedModel
            is ValidationResult.Valid -> parsed.data!!
            is ValidationResult.Invalid -> {
                notify(project, validated.errors.joinToString("\n") { it.message }, NotificationType.ERROR)
                return
            }
        }

        // Run analysis + prompt build in background
        object : Task.Backgroundable(project, "SpringForge: Building Prompt...", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing project structure..."
                indicator.fraction = 0.2
                val analysis = ProjectContextBuilder.build(project)

                indicator.text = "Scanning existing entities..."
                indicator.fraction = 0.5
                val existingEntities = try {
                    val extractor = ExistingEntityExtractor(baseDir)
                    val result = extractor.extract()
                    if (result.isEmpty) null else result
                } catch (_: Exception) { null }

                indicator.text = "Building prompt..."
                indicator.fraction = 0.7
                val prompt = PromptBuilder.buildPrompt(yamlModel, analysis, existingEntities)

                val promptFile = File(baseDir, "springforge_prompt.txt")
                promptFile.writeText(prompt)

                indicator.fraction = 1.0

                // Open in Editor on EDT
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(promptFile)
                    if (vFile != null) {
                        FileEditorManager.getInstance(project).openFile(vFile, true)
                    }
                    notify(project, "Prompt generated at springforge_prompt.txt", NotificationType.INFORMATION)
                }
            }
        }.queue()
    }

    private fun notify(project: com.intellij.openapi.project.Project, msg: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("SpringForge", "Prompt Builder", msg, type),
            project
        )
    }
}
