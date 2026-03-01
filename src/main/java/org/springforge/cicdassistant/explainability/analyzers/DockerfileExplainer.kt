package org.springforge.cicdassistant.explainability.analyzers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springforge.cicdassistant.bedrock.BedrockClient
import org.springforge.cicdassistant.explainability.ExplainabilityAnalyzer
import org.springforge.cicdassistant.explainability.ExplainabilityInsight
import org.springforge.cicdassistant.explainability.ExplainabilityResult
import org.springforge.cicdassistant.explainability.InsightCategory

class DockerfileExplainer : ExplainabilityAnalyzer {

    override fun getName(): String = "Dockerfile Explainer"

    override fun canAnalyze(filePath: String): Boolean =
        filePath.endsWith("Dockerfile") || filePath.contains("Dockerfile")

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
        You are an expert DevOps educator explaining a Dockerfile to a Spring Boot developer.

        Analyze the following Dockerfile and produce structured JSON explanations.
        For each logical section (base image, dependency copy, build stage, runtime stage,
        user setup, HEALTHCHECK, EXPOSE, ENTRYPOINT, CMD, ENV, ARG, LABEL, etc.),
        provide an educational insight.

        RULES:
        1. Group consecutive related lines into one insight — do NOT produce one insight per line.
        2. line_start and line_end are 1-indexed. Set to null for file-level insights.
        3. category must be exactly one of: SECURITY, PERFORMANCE, BUILD, CONFIGURATION, RELIABILITY, DESIGN
        4. Return ONLY valid JSON — no markdown, no code fences, no explanatory text before or after.
        5. Each explanation must be 2–5 sentences of educational content explaining WHY this choice was made.
        6. recommendations may be an empty array [] if the section already follows best practices.

        CATEGORY GUIDE:
        - SECURITY: non-root users, secret management, image hardening, file permissions
        - PERFORMANCE: multi-stage builds, layer caching, image size optimization, JVM tuning
        - BUILD: build tool commands, dependency fetching, artifact compilation
        - CONFIGURATION: ports, environment variables, volume mounts, labels, working directory
        - RELIABILITY: health checks, signal handling, process management
        - DESIGN: overall architecture decisions, stage naming, base image selection rationale

        Return this exact JSON schema:
        {"insights":[{"category":"string","section_name":"string","title":"string","explanation":"string","line_start":integer_or_null,"line_end":integer_or_null,"recommendations":["string"],"priority":integer}]}

        Dockerfile content:
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
            println("[DockerfileExplainer] JSON parse error: ${e.message}")
            emptyList()
        }
    }
}
