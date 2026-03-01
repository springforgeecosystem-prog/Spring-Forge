package org.springforge.cicdassistant.explainability.analyzers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.explainability.ExplainabilityAnalyzer
import org.springforge.cicdassistant.explainability.ExplainabilityInsight
import org.springforge.cicdassistant.explainability.ExplainabilityResult
import org.springforge.cicdassistant.explainability.InsightCategory

class DockerComposeExplainer : ExplainabilityAnalyzer {

    override fun getName(): String = "Docker Compose Explainer"

    override fun canAnalyze(filePath: String): Boolean =
        filePath.endsWith("docker-compose.yml") ||
        filePath.endsWith("docker-compose.yaml") ||
        filePath.contains("docker-compose")

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
        You are an expert DevOps educator explaining a docker-compose.yml to a Spring Boot developer.

        Analyze the following docker-compose file and produce structured JSON explanations.
        For each logical section (version declaration, each service block, build config,
        image references, port mappings, environment variables, volumes, networks,
        depends_on, restart policies, health checks), provide an educational insight.

        RULES:
        1. Treat each top-level service as a separate insight group — do NOT mix services.
        2. line_start and line_end are 1-indexed. Set to null for file-level insights.
        3. category must be exactly one of: SECURITY, PERFORMANCE, BUILD, CONFIGURATION, RELIABILITY, DESIGN
        4. Return ONLY valid JSON — no markdown, no code fences, no explanatory text before or after.
        5. Each explanation must be 2–5 sentences explaining WHY this configuration choice was made.
        6. recommendations may be an empty array [] if the section already follows best practices.

        CATEGORY GUIDE:
        - SECURITY: secret management, non-root user, read-only volumes, network isolation
        - PERFORMANCE: resource limits, restart policies, caching volumes
        - BUILD: build context, dockerfile reference, build args
        - CONFIGURATION: ports, env vars, volume mounts, labels, service names, networks
        - RELIABILITY: health checks, restart policies, depends_on conditions, resource limits
        - DESIGN: overall service architecture, service decomposition, naming conventions

        Return this exact JSON schema:
        {"insights":[{"category":"string","section_name":"string","title":"string","explanation":"string","line_start":integer_or_null,"line_end":integer_or_null,"recommendations":["string"],"priority":integer}]}

        docker-compose.yml content:
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
            println("[DockerComposeExplainer] JSON parse error: ${e.message}")
            emptyList()
        }
    }
}
