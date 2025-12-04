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
    }

    /**
     * Packages a ProjectInfo into MCP-formatted context.
     * Converts the existing ProjectInfo structure to the standardized MCP format
     * that LLMs can consume efficiently.
     *
     * @param projectInfo The analyzed project information from ProjectAnalyzerService
     * @return MCPContext ready for serialization and transmission to Bedrock
     */
    fun packageContext(projectInfo: ProjectInfo): MCPContext {
        return MCPContext(
            metadata = buildMetadata(),
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
     * Builds MCPMetadata with current timestamp and version info.
     */
    private fun buildMetadata(): MCPMetadata {
        return MCPMetadata(
            analysisTimestamp = Instant.now().toString(),
            pluginVersion = "1.0-SNAPSHOT",
            mcpVersion = "1.0"
        )
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
}
