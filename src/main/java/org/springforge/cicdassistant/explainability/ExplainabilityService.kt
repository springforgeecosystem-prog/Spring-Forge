package org.springforge.cicdassistant.explainability

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.springforge.cicdassistant.explainability.analyzers.DockerComposeExplainer
import org.springforge.cicdassistant.explainability.analyzers.DockerfileExplainer
import org.springforge.cicdassistant.explainability.analyzers.GitHubActionsExplainer
import java.time.Instant

/**
 * Service that orchestrates AI-powered explainability analysis of CI/CD artifacts.
 * Mirrors the ValidationService pattern — registered as a light IntelliJ project service
 * via @Service annotation (no plugin.xml entry required).
 */
@Service(Service.Level.PROJECT)
class ExplainabilityService(private val project: Project) {

    private val analyzers: List<ExplainabilityAnalyzer> = listOf(
        DockerfileExplainer(),
        DockerComposeExplainer(),
        GitHubActionsExplainer()
    )

    /**
     * Primary entry point — explains in-memory generated artifacts.
     * Mirrors ValidationService.validateGeneratedArtifacts().
     */
    fun explainGeneratedArtifacts(
        dockerfile: String?,
        dockerCompose: String?,
        githubWorkflow: String?,
        dockerfilePath: String? = null,
        dockerComposePath: String? = null,
        githubWorkflowPath: String? = null
    ): ExplainabilityResult {
        val files = mutableMapOf<String, String>()
        dockerfile?.let     { files[dockerfilePath     ?: "Dockerfile"]                  = it }
        dockerCompose?.let  { files[dockerComposePath  ?: "docker-compose.yml"]          = it }
        githubWorkflow?.let { files[githubWorkflowPath ?: ".github/workflows/build.yml"] = it }
        return explainFiles(files)
    }

    /**
     * Explains multiple files by routing each to the appropriate analyzer.
     */
    fun explainFiles(files: Map<String, String>): ExplainabilityResult {
        val start = Instant.now()
        val allInsights = mutableListOf<ExplainabilityInsight>()
        var totalMs = 0L

        files.forEach { (path, content) ->
            if (content.isNotBlank()) {
                val analyzer = analyzers.find { it.canAnalyze(path) }
                if (analyzer != null) {
                    val result = analyzer.analyze(path, content)
                    allInsights.addAll(result.insights)
                    totalMs += result.durationMs
                }
            }
        }

        return ExplainabilityResult(
            insights = allInsights,
            timestamp = start,
            filesAnalyzed = files.size,
            durationMs = totalMs
        )
    }

    fun hasAnalyzerFor(filePath: String): Boolean =
        analyzers.any { it.canAnalyze(filePath) }

    companion object {
        fun getInstance(project: Project): ExplainabilityService =
            project.getService(ExplainabilityService::class.java)
    }
}
