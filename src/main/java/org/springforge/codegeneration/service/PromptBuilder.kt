package org.springforge.codegeneration.service

import org.springforge.codegeneration.analysis.ProjectAnalysisResult
import org.springforge.codegeneration.parser.InputModel

object PromptBuilder {

    fun buildPrompt(
        yamlModel: InputModel,
        analysis: ProjectAnalysisResult
    ): String {

        val sb = StringBuilder()
        val layers = analysis.layers.map { it.lowercase() }.toSet()

        val persistenceLayer = when {
            layers.contains("dao") -> "dao"
            layers.contains("repository") -> "repository"
            else -> null
        }

        val modelLayer = when {
            layers.contains("model") -> "model"
            layers.contains("dto") -> "dto"
            else -> null
        }

        sb.appendLine("### SpringForge – Full Context Code Generation Prompt\n")

        sb.appendLine("## 1. Project Context (Automatically Analyzed)")
        sb.appendLine("Detected Architecture: ${analysis.detectedArchitecture}")
        sb.appendLine("Base Package: ${analysis.basePackage}")
        sb.appendLine("Existing Folder Structure / Layers:")
        analysis.layers.forEach { sb.appendLine(" - $it") }

        sb.appendLine("Naming Conventions:")
        analysis.namingConventions.forEach { (k, v) ->
            sb.appendLine(" - $k → $v")
        }
        sb.appendLine()

        sb.appendLine("## 2. User Intent (From input.yml)")
        sb.appendLine("Project Name: ${yamlModel.project?.name}")
        sb.appendLine("Language: ${yamlModel.project?.language}")
        sb.appendLine("Framework: ${yamlModel.project?.framework}\n")

        sb.appendLine("## 3. Domain Model")
        yamlModel.entities.forEach { entity ->
            sb.appendLine(" - Entity: ${entity.name}")
            entity.table_name?.let { sb.appendLine("   Table: $it") }
            sb.appendLine("   Fields:")
            entity.fields.forEach {
                sb.appendLine("     • ${it.name}: ${it.type} (${it.constraints})")
            }
            sb.appendLine()
        }

        if (yamlModel.relationships.isNotEmpty()) {
            sb.appendLine("### Relationships")
            yamlModel.relationships.forEach {
                sb.appendLine(" - ${it.from} → ${it.to} (${it.type})")
            }
            sb.appendLine()
        }

        sb.appendLine("## 4. Required Code Scaffolding")
        sb.appendLine("Generate the following layers for ALL entities:")
        sb.appendLine(" - entity")
        persistenceLayer?.let { sb.appendLine(" - $it") }
        sb.appendLine(" - service")
        sb.appendLine(" - controller")
        modelLayer?.let { sb.appendLine(" - $it") }
        sb.appendLine()

        sb.appendLine("## 5. Architectural Constraints")
        sb.appendLine("* Follow the existing project folder structure exactly")
        persistenceLayer?.let { sb.appendLine("* Use '$it' as persistence layer") }
        modelLayer?.let { sb.appendLine("* Use '$it' for request/response models") }
        sb.appendLine("* Do NOT introduce new layers")
        sb.appendLine("* Do NOT rename existing layers")
        sb.appendLine("* Place generated code under base package: ${analysis.basePackage}")
        sb.appendLine("* Apply detected naming conventions consistently")
        sb.appendLine("* Wire layers correctly (Controller → Service → $persistenceLayer → Entity)")
        sb.appendLine()

        sb.appendLine("## 6. Final Instruction")
        sb.appendLine(
            "Generate a complete, production-ready Spring Boot scaffolding " +
                    "based on the YAML-defined domain model, strictly aligned with the " +
                    "existing project architecture and conventions."
        )

        return sb.toString()
    }
}
