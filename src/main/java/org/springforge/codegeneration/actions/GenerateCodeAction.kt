package org.springforge.codegeneration.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import org.springforge.codegeneration.analysis.ExistingEntityExtractor
import org.springforge.codegeneration.analysis.ProjectContextBuilder
import org.springforge.codegeneration.parser.InputModel
import org.springforge.codegeneration.parser.YamlParser
import org.springforge.codegeneration.parser.YamlWriter
import org.springforge.codegeneration.service.*
import org.springforge.cicdassistant.audit.AuditService
import org.springforge.codegeneration.validation.ValidationResult
import org.springforge.codegeneration.validation.YamlValidator
import java.io.File

/**
 * Main action: parse input.yml → analyze project → build prompt → call Gemini → write files.
 */
class GenerateCodeAction : AnAction("Generate Code") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val baseDir = project.basePath ?: return

        // ── 1. READ & PARSE input.yml ──────────────────────────────────────
        val yamlFile = File(baseDir, "input.yml")
        if (!yamlFile.exists()) {
            notifyError(project, "input.yml not found in project root. Create it first.")
            return
        }

        val text = yamlFile.readText()
        val result = YamlParser.parse(text)

        if (!result.isValid) {
            notifyError(project, "YAML parse error: ${result.errorMessage}")
            return
        }

        val model = result.data!!

        // ── 2. VALIDATE & AUTO-FIX ────────────────────────────────────────
        val validation = YamlValidator.validateAndFix(model)

        val yamlModel: InputModel = when (validation) {
            is ValidationResult.Invalid -> {
                val msg = validation.errors.joinToString("\n") { it.message }
                notifyError(project, msg)
                return
            }
            is ValidationResult.AutoFixed -> {
                validation.warnings.forEach { w ->
                    notify(project, "Auto-fix: $w", NotificationType.WARNING)
                }
                // Write the fixed YAML back to disk
                val fixedYaml = YamlWriter.write(validation.fixedModel)
                yamlFile.writeText(fixedYaml)
                validation.fixedModel
            }
            is ValidationResult.Valid -> model
        }

        // ── 3. RUN FULL PIPELINE IN BACKGROUND ───────────────────────────
        object : Task.Backgroundable(project, "SpringForge: Generating Code...", true) {
            override fun run(indicator: ProgressIndicator) {
                val startMs = System.currentTimeMillis()
                try {
                    // 3a. Analyze project context
                    indicator.text = "Analyzing project structure..."
                    indicator.fraction = 0.1
                    val analysis = ProjectContextBuilder.build(project)

                    // 3a-2. Extract existing entities from the project
                    indicator.text = "Scanning existing entities..."
                    indicator.fraction = 0.15
                    val existingEntities = try {
                        val extractor = ExistingEntityExtractor(baseDir)
                        val result = extractor.extract()
                        if (result.isEmpty) null else result
                    } catch (_: Exception) { null }

                    // 3b. Build optimized prompt (with existing entity context)
                    indicator.text = "Building prompt..."
                    indicator.fraction = 0.2
                    val prompt = PromptBuilder.buildPrompt(yamlModel, analysis, existingEntities)

                    // // Save prompt for debugging / inspection
                    // val promptFile = File(baseDir, "springforge_prompt.txt")
                    // promptFile.writeText(prompt)

                    // 3c. Call Gemini LLM
                    indicator.text = "Calling Gemini LLM (this may take a minute)..."
                    indicator.fraction = 0.3
                    val gemini = GeminiClient(apiKey = GeminiClient.resolveApiKey(baseDir))
                    val rawResponse = gemini.generate(prompt)

                    // // Save raw response for debugging
                    // val responseFile = File(baseDir, "springforge_response.txt")
                    // responseFile.writeText(rawResponse)

                    // 3d. Parse LLM response into files
                    indicator.text = "Parsing generated code..."
                    indicator.fraction = 0.7
                    val allParsedFiles = CodeFileParser.parse(rawResponse)

                    // Filter out build files — LLMs hallucinate dependency names,
                    // so we NEVER let the LLM modify pom.xml / build.gradle.
                    // Dependencies are handled programmatically by BuildFileUpdater.
                    val buildFileNames = setOf("pom.xml", "build.gradle", "build.gradle.kts")
                    val generatedFiles = allParsedFiles.filter { gf ->
                        val fileName = gf.relativePath.substringAfterLast('/').lowercase()
                        fileName !in buildFileNames
                    }

                    if (generatedFiles.isEmpty()) {
                        notify(
                            project,
                            "LLM returned no parseable files.",
                            NotificationType.ERROR
                        )
                        return
                    }

                    // 3e. Write Java source files to disk
                    indicator.text = "Writing ${generatedFiles.size} files..."
                    indicator.fraction = 0.8
                    val writeResult = CodeWriter.writeAll(baseDir, generatedFiles, overwrite = false)

                    // 3f. Programmatically add missing dependencies to pom.xml / build.gradle
                    //     (no LLM involvement — zero hallucination risk)
                    indicator.text = "Updating build dependencies..."
                    indicator.fraction = 0.88
                    val missingDeps = PromptBuilder.determineMissingDependencies(analysis)
                    val depResult = BuildFileUpdater.addMissingDependencies(baseDir, missingDeps)

                    // 3g. Refresh VFS so IntelliJ sees new files
                    indicator.text = "Refreshing project..."
                    indicator.fraction = 0.95
                    CodeWriter.refreshProjectFiles(project, baseDir)

                    // ── 4. REPORT RESULTS ─────────────────────────────────
                    val summary = buildSummary(writeResult, generatedFiles.size, depResult)
                    notify(project, summary, NotificationType.INFORMATION)

                    // Publish results to sidebar panel
                    val genResult = GenerationResult(
                        totalFromLLM = generatedFiles.size,
                        written = writeResult.written,
                        skipped = writeResult.skipped,
                        errors = writeResult.errors,
                        addedDependencies = depResult.addedDependencies,
                        depError = depResult.error,
                        buildFile = depResult.buildFile
                    )
                    GenerationResultService.getInstance(project).publish(genResult)
                    AuditService.getInstance(project).logCodeGeneration(
                        filesGenerated = generatedFiles.size,
                        filesWritten   = writeResult.written.size,
                        filesSkipped   = writeResult.skipped.size,
                        fileErrors     = writeResult.errors.size,
                        durationMs     = System.currentTimeMillis() - startMs,
                        success        = true
                    )

                    // // Open the prompt file for reference
                    // ApplicationManager.getApplication().invokeLater {
                    //     val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(promptFile)
                    //     if (vf != null) {
                    //         FileEditorManager.getInstance(project).openFile(vf, false)
                    //     }
                    // }

                } catch (ex: IllegalStateException) {
                    // API key missing
                    notify(project, ex.message ?: "Configuration error", NotificationType.ERROR)
                    AuditService.getInstance(project).logCodeGeneration(
                        filesGenerated = 0, filesWritten = 0, filesSkipped = 0, fileErrors = 0,
                        durationMs = System.currentTimeMillis() - startMs,
                        success    = false, errorMsg = ex.message
                    )
                } catch (ex: Exception) {
                    notify(
                        project,
                        "Code generation failed: ${ex.message}",
                        NotificationType.ERROR
                    )
                    AuditService.getInstance(project).logCodeGeneration(
                        filesGenerated = 0, filesWritten = 0, filesSkipped = 0, fileErrors = 0,
                        durationMs = System.currentTimeMillis() - startMs,
                        success    = false, errorMsg = ex.message
                    )
                }
            }
        }.queue()
    }

    private fun buildSummary(
        result: CodeWriteResult,
        total: Int,
        depResult: BuildFileUpdater.UpdateResult
    ): String {
        val sb = StringBuilder()
        sb.appendLine("SpringForge Code Generation Complete!")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Total files from LLM : $total")
        sb.appendLine("Written              : ${result.written.size}")
        if (result.skipped.isNotEmpty()) {
            sb.appendLine("Skipped (existing)   : ${result.skipped.size}")
            result.skipped.forEach { sb.appendLine("  ⏭ $it") }
        }
        if (result.errors.isNotEmpty()) {
            sb.appendLine("Errors               : ${result.errors.size}")
            result.errors.forEach { (path, err) -> sb.appendLine("  ✗ $path → $err") }
        }
        if (depResult.addedDependencies.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Dependencies added to ${depResult.buildFile}:")
            depResult.addedDependencies.forEach { sb.appendLine("  📦 $it") }
        }
        if (depResult.error != null) {
            sb.appendLine("⚠ Dependency update error: ${depResult.error}")
        }
        if (result.written.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Generated files:")
            result.written.forEach { sb.appendLine("  ✓ $it") }
        }
        return sb.toString()
    }

    private fun notifyError(project: Project, msg: String) {
        notify(project, msg, NotificationType.ERROR)
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("SpringForge", "Code Generation", msg, type),
            project
        )
    }
}
