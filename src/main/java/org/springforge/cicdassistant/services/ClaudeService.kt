package org.springforge.cicdassistant.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springforge.cicdassistant.config.EnvironmentConfig
import org.springforge.cicdassistant.prompts.DockerfilePromptBuilder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Service for interacting with Claude via the SpringForge API Gateway proxy.
 * Users need only SPRINGFORGE_API_URL — no AWS credentials required.
 */
class ClaudeService {

    private val objectMapper = jacksonObjectMapper()
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val dockerfilePromptBuilder = DockerfilePromptBuilder()

    init {
        EnvironmentConfig.validate()
    }

    // ── Core HTTP call ────────────────────────────────────────────────────────

    private fun postToGateway(payload: Map<String, Any>): String {
        val url = EnvironmentConfig.Api.url
            ?: throw ClaudeServiceException("SPRINGFORGE_API_URL not configured in .env")

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))

        EnvironmentConfig.Api.apiKey?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("x-api-key", it) }

        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            val errMsg = try {
                @Suppress("UNCHECKED_CAST")
                (objectMapper.readValue(response.body(), Map::class.java) as Map<String, Any>)["error"]
                    ?: response.body()
            } catch (_: Exception) { response.body() }
            throw ClaudeServiceException("Gateway error ${response.statusCode()}: $errMsg")
        }
        return response.body()
    }

    // ── Public generation methods ─────────────────────────────────────────────

    fun generateDockerfileFromAnalysis(projectInfo: ProjectAnalyzerService.ProjectInfo): String {
        val intelligentPrompt = dockerfilePromptBuilder.buildPrompt(projectInfo)
        val prefill = if (projectInfo.architectureType == "INTELLIJ_PLUGIN")
            buildIntellijPluginPrefill(projectInfo)
        else
            buildSpringBootPrefill(projectInfo)
        return callClaudeWithPrefill(intelligentPrompt, prefill)
    }

    private fun buildIntellijPluginPrefill(projectInfo: ProjectAnalyzerService.ProjectInfo): String =
        """# Stage 1: Build Plugin
FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder

# Install bash and dos2unix for line ending conversion
RUN apk add --no-cache bash dos2unix

# Set working directory
WORKDIR /build

# Copy Gradle wrapper files
COPY gradlew ./
COPY gradlew.bat ./
COPY gradle/ ./gradle/

# Copy build configuration
COPY build.gradle.kts ./
COPY settings.gradle.kts ./

# Copy source code
COPY src/ ./src/

# Fix line endings and make Gradle wrapper executable
RUN dos2unix gradlew && chmod +x ./gradlew

# Build the plugin
RUN""".trimEnd()

    private fun buildSpringBootPrefill(projectInfo: ProjectAnalyzerService.ProjectInfo): String =
        """# Stage 1: Build
FROM eclipse-temurin:${projectInfo.javaVersion}-jdk-alpine AS builder

# Set working directory
WORKDIR /build""".trimEnd()

    @Deprecated("Use generateDockerfileFromAnalysis() with AST-analyzed ProjectInfo")
    fun generateDockerfile(projectInfo: String): String = callClaude(
        "You are an expert DevOps engineer. Generate a production-ready multi-stage Dockerfile for: $projectInfo. Return ONLY the Dockerfile content."
    )

    fun generateGitHubActionsWorkflow(projectInfo: String): String = callClaude(
        "You are a CI/CD expert. Generate a GitHub Actions workflow for: $projectInfo. Return ONLY the YAML content."
    )

    fun generateDockerCompose(projectInfo: String): String = callClaude(
        "You are a Docker expert. Generate a docker-compose.yml for: $projectInfo. Return ONLY the YAML content."
    )

    fun generateKubernetesManifests(projectInfo: String): String = callClaude(
        "You are a Kubernetes expert. Generate deployment manifests for: $projectInfo. Return all manifests separated by '---'."
    )

    fun generateGitIgnore(buildTool: String): String = callClaude(
        "Generate a comprehensive .gitignore for a Spring Boot project using $buildTool. Return ONLY the .gitignore content."
    )

    fun analyzeDockerfile(dockerfileContent: String): String = callClaude(
        "Analyze this Dockerfile for security issues and best practice violations:\n```\n$dockerfileContent\n```\nProvide structured analysis with severity levels."
    )

    // ── Core Claude invocation ────────────────────────────────────────────────

    fun callClaude(prompt: String, maxTokens: Int = EnvironmentConfig.Claude.maxTokens): String {
        return try {
            val payload = mapOf(
                "modelId"           to EnvironmentConfig.Claude.modelId,
                "anthropic_version" to EnvironmentConfig.Claude.anthropicVersion,
                "max_tokens"        to maxTokens,
                "messages"          to listOf(mapOf("role" to "user", "content" to prompt))
            )
            val responseBody = postToGateway(payload)
            objectMapper.readTree(responseBody)["content"][0]["text"].asText()
        } catch (e: ClaudeServiceException) { throw e }
        catch (e: Exception) {
            throw ClaudeServiceException("Failed to call Claude API: ${e.message}", e)
        }
    }

    fun callClaudeWithPrefill(
        prompt: String,
        prefill: String,
        maxTokens: Int = EnvironmentConfig.Claude.maxTokens
    ): String {
        return try {
            val payload = mapOf(
                "modelId"           to EnvironmentConfig.Claude.modelId,
                "anthropic_version" to EnvironmentConfig.Claude.anthropicVersion,
                "max_tokens"        to maxTokens,
                "temperature"       to 0.0,
                "messages"          to listOf(
                    mapOf("role" to "user",      "content" to prompt),
                    mapOf("role" to "assistant", "content" to prefill)
                )
            )
            val responseBody = postToGateway(payload)
            prefill + objectMapper.readTree(responseBody)["content"][0]["text"].asText()
        } catch (e: ClaudeServiceException) { throw e }
        catch (e: Exception) {
            throw ClaudeServiceException("Failed to call Claude API with prefill: ${e.message}", e)
        }
    }

    fun testConnection(): Boolean = try {
        callClaude("Respond with just 'OK'", maxTokens = 10).contains("OK", ignoreCase = true)
    } catch (_: Exception) { false }

    fun close() { /* HttpClient manages its own lifecycle */ }
}

/**
 * Custom exception for Claude service errors
 */
class ClaudeServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)
