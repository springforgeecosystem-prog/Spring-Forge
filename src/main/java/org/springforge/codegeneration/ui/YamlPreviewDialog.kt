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

        // -------------------------
        // PROJECT DETAILS
        // -------------------------
        sb.appendLine("PROJECT DETAILS")
        sb.appendLine("----------------")
        sb.appendLine("Project Name : ${model.project?.name ?: "N/A"}")
        sb.appendLine("Language     : ${model.project?.language ?: "N/A"}")
        sb.appendLine("Framework    : ${model.project?.framework ?: "N/A"}")
        sb.appendLine()

        // -------------------------
        // ENTITIES
        // -------------------------
        sb.appendLine("ENTITIES (${model.entities.size})")
        sb.appendLine("----------------")

        model.entities.forEach { entity ->
            sb.appendLine("• Entity: ${entity.name}")

            if (entity.table_name != null) {
                sb.appendLine("  Table Name: ${entity.table_name}")
            }

            if (entity.annotations.isNotEmpty()) {
                sb.appendLine("  Annotations:")
                entity.annotations.forEach { ann ->
                    sb.appendLine("     • $ann")
                }
            }

            sb.appendLine("  Fields (${entity.fields.size}):")
            entity.fields.forEach { field ->
                sb.appendLine("     - ${field.name}: ${field.type}")

                if (field.constraints.isNotEmpty()) {
                    sb.appendLine("         Constraints:")
                    field.constraints.forEach { (k, v) ->
                        sb.appendLine("            • $k = $v")
                    }
                }

                if (field.annotations.isNotEmpty()) {
                    sb.appendLine("         Annotations:")
                    field.annotations.forEach { ann ->
                        sb.appendLine("            • $ann")
                    }
                }
            }

            sb.appendLine()
        }

        // -------------------------
        // RELATIONSHIPS
        // -------------------------
        if (model.relationships.isNotEmpty()) {
            sb.appendLine("RELATIONSHIPS (${model.relationships.size})")
            sb.appendLine("----------------")

            model.relationships.forEach { rel ->
                sb.appendLine("• ${rel.from} → ${rel.to} (${rel.type})")

                if (rel.mapped_by != null) {
                    sb.appendLine("    mapped_by: ${rel.mapped_by}")
                }

                if (rel.annotations.isNotEmpty()) {
                    sb.appendLine("    Annotations:")
                    rel.annotations.forEach { ann ->
                        sb.appendLine("        • $ann")
                    }
                }

                sb.appendLine()
            }
        }

        Messages.showInfoMessage(project, sb.toString(), "Parsed input.yml")
    }
}
