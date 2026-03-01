package org.springforge.cicdassistant.explainability.analyzers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.explainability.ExplainabilityAnalyzer
import org.springforge.cicdassistant.explainability.ExplainabilityInsight
import org.springforge.cicdassistant.explainability.ExplainabilityResult
import org.springforge.cicdassistant.explainability.InsightCategory

class GitHubActionsExplainer : ExplainabilityAnalyzer {

    override fun getName(): String = "GitHub Actions Explainer"

    override fun canAnalyze(filePath: String): Boolean {
        // Normalise to forward slashes so this works on Windows (backslash paths)
        val normalized = filePath.replace('\\', '/')
        return normalized.contains(".github/workflows") &&
            (filePath.endsWith(".yml") || filePath.endsWith(".yaml"))
    }

    override fun analyze(filePath: String, content: String): ExplainabilityResult {
        val start = System.currentTimeMillis()
        val client = BedrockClient()
        return try {
            val rawJson = client.explainArtifact(buildPrompt(content))
            val insights = parseInsights(rawJson, filePath)
            ExplainabilityResult(
                insights = insights,
                filesAnalyzed = 1,
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            client.close()
        }
    }

    private fun buildPrompt(content: String): String = """
        You are an expert DevOps educator explaining a GitHub Actions workflow to a Spring Boot developer.

        Analyze the following GitHub Actions workflow YAML and produce structured JSON explanations.
        Cover every major section: the on-trigger block, each job definition, the runner selection,
        and each named step (checkout, setup-java, dependency caching, build, test, docker build,
        docker push, deployment steps). Each named step should ideally be its own insight.

        RULES:
        1. Treat each named step (uses:/run:) as a candidate for its own insight.
        2. Group the on-trigger block as a single insight (section_name: "Trigger Configuration").
        3. line_start and line_end are 1-indexed. Set to null for file-level insights.
        4. category must be exactly one of: SECURITY, PERFORMANCE, BUILD, CONFIGURATION, RELIABILITY, DESIGN
        5. Return ONLY valid JSON — no markdown, no code fences, no explanatory text before or after.
        6. Each explanation must be 2–5 sentences explaining WHY this step or configuration exists.
        7. recommendations may be an empty array [] if the section already follows best practices.

        CATEGORY GUIDE:
        - SECURITY: permissions, secrets usage, OIDC authentication, image signing
        - PERFORMANCE: caching strategies (Maven/Gradle cache), parallel jobs, conditional steps
        - BUILD: compile, test, package, docker build steps
        - CONFIGURATION: environment variables, matrix strategy, runner selection
        - RELIABILITY: retry logic, timeout settings, artifact upload for debugging
        - DESIGN: overall workflow structure, job dependencies (needs:), branching strategy

        Return this exact JSON schema:
        {"insights":[{"category":"string","section_name":"string","title":"string","explanation":"string","line_start":integer_or_null,"line_end":integer_or_null,"recommendations":["string"],"priority":integer}]}

        GitHub Actions workflow content:
        $content
    """.trimIndent()

    private fun parseInsights(rawJson: String, filePath: String): List<ExplainabilityInsight> {
        val cleaned = rawJson
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            val mapper = jacksonObjectMapper()
            val root = mapper.readValue<Map<String, Any>>(cleaned)
            val arr = root["insights"] as? List<*> ?: return emptyList()
            arr.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val cat = runCatching {
                    InsightCategory.valueOf((m["category"] as? String ?: "DESIGN").uppercase())
                }.getOrDefault(InsightCategory.DESIGN)
                ExplainabilityInsight(
                    category        = cat,
                    sectionName     = m["section_name"] as? String ?: "General",
                    title           = m["title"] as? String ?: "Insight",
                    explanation     = m["explanation"] as? String ?: "",
                    filePath        = filePath,
                    lineStart       = (m["line_start"] as? Number)?.toInt(),
                    lineEnd         = (m["line_end"] as? Number)?.toInt(),
                    recommendations = (m["recommendations"] as? List<*>)
                                        ?.filterIsInstance<String>() ?: emptyList(),
                    priority        = (m["priority"] as? Number)?.toInt() ?: 0
                )
            }
        } catch (e: Exception) {
            println("[GitHubActionsExplainer] JSON parse error: ${e.message}")
            emptyList()
        }
    }
}
