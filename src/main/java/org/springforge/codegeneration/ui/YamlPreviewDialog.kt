package org.springforge.codegeneration.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.springforge.codegeneration.parser.InputModel

class YamlPreviewDialog(
    private val project: Project,
    private val model: InputModel
) {

    fun show() {
        val sb = StringBuilder()

        sb.appendLine("┌──────────────────────────────────────────────")
        sb.appendLine("│ Parsed input.yml")
        sb.appendLine("└──────────────────────────────────────────────")
        sb.appendLine()
        sb.appendLine("PROJECT DETAILS")
        sb.appendLine("----------------")
        sb.appendLine("Project Name : ${model.projectName}")
        sb.appendLine("Package Root : ${model.packageRoot}")
        sb.appendLine("Architecture : ${model.architecture ?: "Not specified"}")
        sb.appendLine()

        sb.appendLine("ENTITIES (${model.entities.size})")
        sb.appendLine("----------------")

        model.entities.forEach { entity ->
            sb.appendLine("• Entity: ${entity.name}")
            sb.appendLine("  Fields (${entity.fields.size}):")

            entity.fields.forEach { field ->
                sb.appendLine("    - ${field.name}: ${field.type}")

                if (field.annotations.isNotEmpty()) {
                    sb.appendLine("        Annotations:")
                    field.annotations.forEach { ann ->
                        sb.appendLine("           • $ann")
                    }
                }

                if (field.constraints.isNotEmpty()) {
                    sb.appendLine("        Constraints:")
                    field.constraints.forEach { (k, v) ->
                        sb.appendLine("           • $k = $v")
                    }
                }
            }

            if (entity.relationships.isNotEmpty()) {
                sb.appendLine("  Relationships:")
                entity.relationships.forEach { rel ->
                    sb.appendLine("    - ${rel.type} → ${rel.targetEntity}")
                }
            }

            sb.appendLine()
        }

        Messages.showInfoMessage(project, sb.toString(), "Parsed input.yml")
    }
}