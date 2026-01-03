package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.springforge.codegeneration.parser.YamlParser
import org.springforge.codegeneration.parser.YamlWriter
import org.springforge.codegeneration.ui.YamlPreviewDialog
import org.springforge.codegeneration.validation.ValidationResult
import org.springforge.codegeneration.validation.YamlValidator

class GenerateCodeAction : AnAction("Generate Code") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val base = project.baseDir ?: return

        val file: VirtualFile = base.findChild("input.yml") ?: base.createChildData(this, "input.yml")
        FileEditorManager.getInstance(project).openFile(file, true)

        val text = String(file.contentsToByteArray())
        val result = YamlParser.parse(text)

        if (!result.isValid) {
            notifyError(project, "YAML parse error: ${result.errorMessage}")
            return
        }

        val model = result.data!!
        val validation = YamlValidator.validateAndFix(model)

        when (validation) {

            is ValidationResult.Invalid -> {
                val msg = validation.errors.joinToString("\n") { it.message }
                notifyError(project, msg)
            }

            is ValidationResult.AutoFixed -> {

                validation.warnings.forEach {
                    Notifications.Bus.notify(
                        Notification("SpringForge", "Auto-fix applied", it, NotificationType.WARNING),
                        project
                    )
                }

                val fixedYaml = YamlWriter.write(validation.fixedModel)

                ApplicationManager.getApplication().runWriteAction {
                    file.getOutputStream(this).use { out ->
                        out.write(fixedYaml.toByteArray())
                    }
                }

                file.refresh(false, false)

                YamlPreviewDialog(project, validation.fixedModel).show()
            }

            is ValidationResult.Valid -> {
                YamlPreviewDialog(project, model).show()
            }
        }
    }

    private fun notifyError(project: Project, msg: String) {
        Notifications.Bus.notify(
            Notification("SpringForge", "YAML Validation Error", msg, NotificationType.ERROR),
            project
        )
    }
}
