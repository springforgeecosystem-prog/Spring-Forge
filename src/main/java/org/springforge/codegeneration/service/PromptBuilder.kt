package org.springforge.codegeneration.service

import org.springforge.codegeneration.analysis.ProjectAnalysisResult
import org.springforge.codegeneration.parser.InputModel

object PromptBuilder {

    fun buildPrompt(
        yamlModel: InputModel,
        analysis: ProjectAnalysisResult
    ): String {

        val sb = StringBuilder()

        val detectedLayers = analysis.layers.map { it.lowercase() }.toSet()

        // Determine persistence layer naming
        val persistenceLayer = when {
            detectedLayers.contains("dao") -> "dao"
            detectedLayers.contains("repository") -> "repository"
            else -> "repository" // safe fallback
        }

        // Logical layers required to make YAML work
        val logicalLayers = listOf(
            "entity",
            persistenceLayer,
            "service",
            "controller",
            "dto"
        )

        sb.appendLine("### SpringForge – Full Context Code Generation Prompt")
        sb.appendLine()

        // ------------------------------------------------------------------
        // 1. PROJECT CONTEXT (CONSTRAINTS)
        // ------------------------------------------------------------------
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

        // ------------------------------------------------------------------
        // 2. USER INTENT (SOURCE OF TRUTH)
        // ------------------------------------------------------------------
        sb.appendLine("## 2. User Intent (From input.yml)")
        sb.appendLine("Project Name: ${yamlModel.project?.name ?: "N/A"}")
        sb.appendLine("Language: ${yamlModel.project?.language ?: "N/A"}")
        sb.appendLine("Framework: ${yamlModel.project?.framework ?: "N/A"}")
        sb.appendLine()

        // ------------------------------------------------------------------
        // 3. DOMAIN MODEL (WHAT TO GENERATE)
        // ------------------------------------------------------------------
        sb.appendLine("## 3. Domain Model")

        sb.appendLine("### Entities (${yamlModel.entities.size} total)")
        yamlModel.entities.forEach { entity ->
            sb.appendLine(" - Entity: ${entity.name}")
            entity.table_name?.let { sb.appendLine("   Table Name: $it") }

            if (entity.annotations.isNotEmpty()) {
                sb.appendLine("   Entity Annotations:")
                entity.annotations.forEach { sb.appendLine("     • $it") }
            }

            sb.appendLine("   Fields:")
            entity.fields.forEach { f ->
                sb.appendLine("     • ${f.name}: ${f.type}")
                if (f.constraints.isNotEmpty()) {
                    sb.appendLine("       Constraints: ${f.constraints}")
                }
            }
            sb.appendLine()
        }

        if (yamlModel.relationships.isNotEmpty()) {
            sb.appendLine("### Relationships")
            yamlModel.relationships.forEach { r ->
                sb.appendLine(" - ${r.from} → ${r.to} (${r.type})")
                r.mapped_by?.let { sb.appendLine("   mapped_by: $it") }
                if (r.annotations.isNotEmpty()) {
                    sb.appendLine("   Annotations:")
                    r.annotations.forEach { sb.appendLine("     • $it") }
                }
            }
            sb.appendLine()
        }

        // ------------------------------------------------------------------
        // 4. REQUIRED SCAFFOLDING (ARCHITECTURE-AWARE)
        // ------------------------------------------------------------------
        sb.appendLine("## 4. Required Code Scaffolding")

        sb.appendLine("Generate the following layers for ALL entities:")
        logicalLayers.forEach { sb.appendLine(" - $it") }
        sb.appendLine()

        // ------------------------------------------------------------------
        // 5. HARD CONSTRAINTS (VERY IMPORTANT FOR LLM)
        // ------------------------------------------------------------------
        sb.appendLine("## 5. Architectural Constraints")
        sb.appendLine("* Follow the existing project folder structure exactly")
        sb.appendLine("* Use '$persistenceLayer' as the persistence layer")
        sb.appendLine("* Do NOT introduce new architectural layers")
        sb.appendLine("* Do NOT rename existing layers")
        sb.appendLine("* Place generated code under base package: ${analysis.basePackage}")
        sb.appendLine("* Apply detected naming conventions consistently")
        sb.appendLine("* Ensure all layers are wired correctly (Controller → Service → $persistenceLayer → Entity)")
        sb.appendLine("* Respect relationships and ownership sides from YAML")
        sb.appendLine()

        // ------------------------------------------------------------------
        // 6. FINAL INSTRUCTION
        // ------------------------------------------------------------------
        sb.appendLine("## 6. Final Instruction")
        sb.appendLine(
            "Generate a complete, production-ready Spring Boot scaffolding " +
                    "based on the YAML-defined domain model, strictly aligned with the " +
                    "existing project architecture and conventions."
        )

        return sb.toString()
    }
}
