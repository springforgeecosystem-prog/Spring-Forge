package org.springforge.cicdassistant.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springforge.cicdassistant.mcp.models.*
import org.springforge.cicdassistant.parsers.CodeStructure
import org.springforge.cicdassistant.services.ProjectAnalyzerService.BuildTool
import org.springforge.cicdassistant.services.ProjectAnalyzerService.ProjectInfo
import java.time.Instant

/**
 * MCPClient packages Spring Boot project context into MCP (Model Context Protocol) format
 * for consumption by AWS Bedrock/Claude.
 *
 * Core responsibilities:
 * 1. Convert ProjectInfo to MCPContext
 * 2. Serialize to JSON for Bedrock API
 * 3. Validate context size against token limits
 * 4. Estimate token count
 *
 * Token Limits:
 * - Claude 4 Sonnet: 200K context window
 * - Target: 180K tokens (90% of limit for safety)
 * - Approximation: 4 characters ≈ 1 token
 */
class MCPClient {

    private val objectMapper = jacksonObjectMapper()
    
    companion object {
        /**
         * Maximum token count for Claude 4 Sonnet (200K window, use 90% for safety)
         */
        const val MAX_TOKEN_COUNT = 180_000
        
        /**
         * Approximation: 4 characters per token (Claude tokenization)
         */
        const val CHARS_PER_TOKEN = 4
        
        /**
         * Placeholder for filtered sensitive data
         */
        const val FILTERED_PLACEHOLDER = "***FILTERED***"
        
        /**
         * Sensitive key patterns to detect in configuration properties
         */
        private val SENSITIVE_KEY_PATTERNS = listOf(
            Regex("password", RegexOption.IGNORE_CASE),
            Regex("passwd", RegexOption.IGNORE_CASE),
            Regex("pwd", RegexOption.IGNORE_CASE),
            Regex("secret", RegexOption.IGNORE_CASE),
            Regex("api[_-]?key", RegexOption.IGNORE_CASE),
            Regex("access[_-]?key", RegexOption.IGNORE_CASE),
            Regex("private[_-]?key", RegexOption.IGNORE_CASE),
            Regex("token", RegexOption.IGNORE_CASE),
            Regex("auth", RegexOption.IGNORE_CASE),
            Regex("credentials?", RegexOption.IGNORE_CASE),
            Regex("jwt", RegexOption.IGNORE_CASE),
            Regex("bearer", RegexOption.IGNORE_CASE),
            Regex("oauth", RegexOption.IGNORE_CASE),
            Regex("aws[_-]?secret", RegexOption.IGNORE_CASE),
            Regex("aws[_-]?access", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Packages a ProjectInfo into MCP-formatted context.
     * Converts the existing ProjectInfo structure to the standardized MCP format
     * that LLMs can consume efficiently.
     *
     * @param projectInfo The analyzed project information from ProjectAnalyzerService
     * @param projectPath Path to the project root (for git default branch detection)
     * @return MCPContext ready for serialization and transmission to Bedrock
     */
    fun packageContext(projectInfo: ProjectInfo, projectPath: String? = null): MCPContext {
        return MCPContext(
            metadata = buildMetadata(projectInfo, projectPath),
            project = buildMCPProject(projectInfo),
            configuration = buildMCPConfiguration(projectInfo),
            codeStructure = buildMCPCodeStructure(projectInfo.codeStructure)
        )
    }

    /**
     * Serializes MCPContext to JSON string for AWS Bedrock API.
     * Uses Jackson with pretty printing for readability.
     *
     * @param mcpContext The MCP context to serialize
     * @return JSON string ready for Bedrock API
     */
    fun serializeForBedrock(mcpContext: MCPContext): String {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mcpContext)
    }

    /**
     * Filters sensitive data from MCPContext before sending to Bedrock.
     * Removes passwords, API keys, tokens, and other credentials.
     *
     * @param mcpContext The context to filter
     * @return Filtered MCPContext with sensitive data removed
     */
    fun filterSensitiveData(mcpContext: MCPContext): MCPContext {
        val filteredConfiguration = mcpContext.configuration?.let { config ->
            MCPConfiguration(
                serverPort = config.serverPort,
                datasourceUrl = sanitizeDatasourceUrl(config.datasourceUrl),
                datasourceDriver = config.datasourceDriver,
                datasourceUsername = if (config.datasourceUsername != null) FILTERED_PLACEHOLDER else null,
                datasourcePassword = if (config.datasourcePassword != null) FILTERED_PLACEHOLDER else null,
                activeProfiles = config.activeProfiles,
                additionalProperties = filterAdditionalProperties(config.additionalProperties)
            )
        }
        
        return mcpContext.copy(
            configuration = filteredConfiguration
        )
    }

    /**
     * Validates that the context size is within Claude 4's token limits.
     * Uses 4 chars ≈ 1 token approximation.
     *
     * @param mcpContext The context to validate
     * @return true if within limits (≤180K tokens), false otherwise
     */
    fun validateContextSize(mcpContext: MCPContext): Boolean {
        val tokenCount = mcpContext.estimateTokenCount()
        return tokenCount <= MAX_TOKEN_COUNT
    }

    /**
     * Gets detailed size information about the context.
     *
     * @param mcpContext The context to analyze
     * @return Map with size metrics (chars, tokens, percentage of limit)
     */
    fun getContextSizeInfo(mcpContext: MCPContext): Map<String, Any> {
        val chars = mcpContext.estimateSizeInCharacters()
        val tokens = mcpContext.estimateTokenCount()
        val percentage = (tokens.toDouble() / MAX_TOKEN_COUNT * 100).toInt()
        
        return mapOf(
            "characters" to chars,
            "estimated_tokens" to tokens,
            "max_tokens" to MAX_TOKEN_COUNT,
            "percentage_used" to percentage,
            "within_limit" to (tokens <= MAX_TOKEN_COUNT)
        )
    }

    // ============ Private Helper Methods ============

    /**
     * Builds MCPMetadata with current timestamp, version info, and architecture type detection.
     */
    private fun buildMetadata(projectInfo: ProjectInfo, projectPath: String?): MCPMetadata {
        // Detect architecture type from ProjectInfo
        val architectureType = when (projectInfo.architectureType) {
            "INTELLIJ_PLUGIN" -> "INTELLIJ_PLUGIN"
            else -> "SPRING_BOOT"
        }

        // Detect default branch from git repository (if path provided)
        val defaultBranch = if (projectPath != null) {
            detectDefaultBranch(projectPath)
        } else {
            "main" // Fallback if no path provided
        }

        return MCPMetadata(
            analysisTimestamp = Instant.now().toString(),
            pluginVersion = "1.0-SNAPSHOT",
            mcpVersion = "1.0",
            architectureType = architectureType,
            defaultBranch = defaultBranch
        )
    }

    /**
     * Detects the default branch from a local git repository.
     * Tries to get the default branch from the remote origin HEAD.
     * Falls back to "main" if detection fails or git is not initialized.
     */
    private fun detectDefaultBranch(projectPath: String): String {
        return try {
            val process = ProcessBuilder(
                "git", "symbolic-ref", "refs/remotes/origin/HEAD"
            ).directory(java.io.File(projectPath))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0 && output.isNotEmpty()) {
                // Output format: refs/remotes/origin/main
                output.substringAfterLast("/")
            } else {
                "main" // Default fallback
            }
        } catch (e: Exception) {
            "main" // Fallback if git command fails
        }
    }

    /**
     * Converts ProjectInfo to MCPProject.
     */
    private fun buildMCPProject(projectInfo: ProjectInfo): MCPProject {
        return MCPProject(
            name = projectInfo.projectName,
            groupId = projectInfo.packageName,
            version = "1.0.0", // ProjectInfo doesn't have version, use default
            buildTool = projectInfo.buildTool.name.lowercase(),
            javaVersion = projectInfo.javaVersion,
            springBootVersion = projectInfo.springBootVersion,
            databaseType = projectInfo.databaseType,
            dependencies = projectInfo.dependencies,
            modules = emptyList() // Not extracted yet in ProjectInfo
        )
    }

    /**
     * Builds MCPConfiguration from ProjectInfo.
     * Extracts server port and database configuration.
     */
    private fun buildMCPConfiguration(projectInfo: ProjectInfo): MCPConfiguration {
        return MCPConfiguration(
            serverPort = projectInfo.port.toString(),
            datasourceUrl = if (projectInfo.hasDatabase) buildDatasourceUrl(projectInfo) else null,
            datasourceDriver = if (projectInfo.hasDatabase) buildDatasourceDriver(projectInfo.databaseType) else null,
            datasourceUsername = null, // Not extracted in ProjectInfo
            datasourcePassword = null, // Not extracted in ProjectInfo (and shouldn't be)
            activeProfiles = emptyList(), // Not extracted yet in ProjectInfo
            additionalProperties = buildAdditionalProperties(projectInfo)
        )
    }

    /**
     * Constructs a sample datasource URL based on database type.
     */
    private fun buildDatasourceUrl(projectInfo: ProjectInfo): String? {
        return when (projectInfo.databaseType?.lowercase()) {
            "postgresql" -> "jdbc:postgresql://localhost:5432/${projectInfo.projectName.lowercase()}"
            "mysql" -> "jdbc:mysql://localhost:3306/${projectInfo.projectName.lowercase()}"
            "mongodb" -> "mongodb://localhost:27017/${projectInfo.projectName.lowercase()}"
            "h2" -> "jdbc:h2:mem:${projectInfo.projectName.lowercase()}"
            else -> null
        }
    }

    /**
     * Returns the JDBC driver class name for the database type.
     */
    private fun buildDatasourceDriver(databaseType: String?): String? {
        return when (databaseType?.lowercase()) {
            "postgresql" -> "org.postgresql.Driver"
            "mysql" -> "com.mysql.cj.jdbc.Driver"
            "h2" -> "org.h2.Driver"
            else -> null
        }
    }

    /**
     * Builds additional configuration properties from ProjectInfo flags.
     */
    private fun buildAdditionalProperties(projectInfo: ProjectInfo): Map<String, String> {
        val props = mutableMapOf<String, String>()
        
        if (projectInfo.hasRedis) {
            props["spring.redis.host"] = "localhost"
            props["spring.redis.port"] = "6379"
        }
        
        if (projectInfo.hasRabbitMQ) {
            props["spring.rabbitmq.host"] = "localhost"
            props["spring.rabbitmq.port"] = "5672"
        }
        
        if (projectInfo.hasKafka) {
            props["spring.kafka.bootstrap-servers"] = "localhost:9092"
        }
        
        return props
    }

    /**
     * Converts CodeStructure to MCPCodeStructure.
     * Extracts controllers, services, repositories, and REST endpoints.
     */
    private fun buildMCPCodeStructure(codeStructure: CodeStructure?): MCPCodeStructure? {
        if (codeStructure == null) return null
        
        return MCPCodeStructure(
            controllers = codeStructure.restControllers.map { it.className },
            services = codeStructure.services.map { it.className },
            repositories = codeStructure.repositories.map { it.className },
            entities = codeStructure.entities.map { it.className },
            restEndpoints = codeStructure.restControllers.flatMap { controller ->
                controller.endpoints.map { endpoint ->
                    MCPCodeStructure.RestEndpoint(
                        httpMethod = endpoint.method.name,
                        path = "${controller.basePath}${endpoint.path}",
                        controller = controller.className,
                        method = endpoint.path.substringAfterLast("/")
                    )
                }
            }
        )
    }

    /**
     * Sanitizes datasource URLs by removing embedded credentials.
     * Example: jdbc:postgresql://user:pass@localhost:5432/db 
     *       -> jdbc:postgresql://localhost:5432/db
     *
     * @param url The datasource URL to sanitize
     * @return Sanitized URL with credentials removed
     */
    private fun sanitizeDatasourceUrl(url: String?): String? {
        if (url == null) return null
        
        // Pattern to match user:pass@ in JDBC URLs
        val credentialPattern = Regex("://([^:]+):([^@]+)@")
        
        return if (credentialPattern.containsMatchIn(url)) {
            credentialPattern.replace(url, "://$FILTERED_PLACEHOLDER@")
        } else {
            url
        }
    }

    /**
     * Filters sensitive data from additional properties map.
     * Checks property keys against sensitive patterns and replaces values.
     *
     * @param properties The properties map to filter
     * @return Filtered properties map
     */
    private fun filterAdditionalProperties(properties: Map<String, String>): Map<String, String> {
        return properties.mapValues { (key, value) ->
            if (isSensitiveKey(key)) {
                FILTERED_PLACEHOLDER
            } else {
                value
            }
        }
    }

    /**
     * Checks if a property key matches any sensitive patterns.
     *
     * @param key The property key to check
     * @return true if the key is sensitive, false otherwise
     */
    private fun isSensitiveKey(key: String): Boolean {
        return SENSITIVE_KEY_PATTERNS.any { pattern ->
            pattern.containsMatchIn(key)
        }
    }
}
