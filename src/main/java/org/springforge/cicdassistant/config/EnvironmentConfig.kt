package org.springforge.cicdassistant.config

import java.io.File

/**
 * Configuration manager for environment variables
 * Loads configuration from .env file or system environment
 */
object EnvironmentConfig {

    private val envVariables: Map<String, String> by lazy {
        loadEnvFile()
    }

    /**
     * Load environment variables from .env file
     * Tries multiple locations to work in both development and sandbox environments
     */
    private fun loadEnvFile(): Map<String, String> {
        val envMap = mutableMapOf<String, String>()

        // Try multiple locations
        val possibleLocations = listOf(
            File(System.getProperty("user.dir"), ".env"),  // Current directory
            File(System.getProperty("user.home"), ".springforge.env"),  // User home
            File("D:\\SLIIT\\SpringForge\\Spring-Forge", ".env"),  // Absolute path to project
            File(".env")  // Relative to execution
        )

        var foundFile: File? = null
        for (location in possibleLocations) {
            if (location.exists() && location.isFile) {
                foundFile = location
                break
            }
        }

        if (foundFile != null) {
            try {
                println("Loading .env from: ${foundFile.absolutePath}")
                foundFile.readLines().forEach { line ->
                    val trimmedLine = line.trim()
                    // Skip comments and empty lines
                    if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                        val parts = trimmedLine.split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            envMap[key] = value
                        }
                    }
                }
                println("Loaded ${envMap.size} environment variables from .env file")
            } catch (e: Exception) {
                println("Warning: Could not read .env file: ${e.message}")
            }
        } else {
            println("Warning: .env file not found in any of the expected locations:")
            possibleLocations.forEach { println("  - ${it.absolutePath}") }
            println("Falling back to system environment variables only")
        }

        return envMap
    }

    /**
     * Get environment variable from .env or system environment
     */
    private fun getEnv(key: String, defaultValue: String? = null): String? {
        return envVariables[key] ?: System.getenv(key) ?: defaultValue
    }

    // SpringForge API Gateway Configuration
    object Api {
        val url: String?
            get() = getEnv("SPRINGFORGE_API_URL")

        val apiKey: String?
            get() = getEnv("SPRINGFORGE_API_KEY")

        fun isConfigured(): Boolean = !url.isNullOrBlank()
    }

    // Claude Configuration
    object Claude {
        val modelId: String
            get() = getEnv("CLAUDE_MODEL_ID", "us.anthropic.claude-sonnet-4-20250514-v1:0")
                ?: "us.anthropic.claude-sonnet-4-20250514-v1:0"

        val maxTokens: Int
            get() = getEnv("CLAUDE_MAX_TOKENS", "4000")?.toIntOrNull() ?: 4000

        val anthropicVersion: String
            get() = getEnv("CLAUDE_ANTHROPIC_VERSION", "bedrock-2023-05-31")
                ?: "bedrock-2023-05-31"
    }

    // GitHub MCP Server Configuration
    object GitHub {
        val personalAccessToken: String?
            get() = getEnv("GITHUB_PERSONAL_ACCESS_TOKEN")

        val host: String
            get() = getEnv("GITHUB_HOST", "https://github.com") ?: "https://github.com"

        val mcpToolsets: String
            get() = getEnv("GITHUB_TOOLSETS", "context,repos,issues,pull_requests,code_security")
                ?: "context,repos,issues,pull_requests,code_security"

        val readOnly: Boolean
            get() = getEnv("GITHUB_READ_ONLY", "true")?.toBoolean() ?: true

        /**
         * Check if GitHub is configured with a valid PAT
         */
        fun isConfigured(): Boolean = !personalAccessToken.isNullOrBlank()
    }

    // PostgreSQL Audit Log Configuration (optional)
    object Postgres {
        val host: String?
            get() = getEnv("POSTGRES_HOST")

        val port: Int
            get() = getEnv("POSTGRES_PORT", "5432")?.toIntOrNull() ?: 5432

        val database: String?
            get() = getEnv("POSTGRES_DB")

        val user: String?
            get() = getEnv("POSTGRES_USER")

        val password: String?
            get() = getEnv("POSTGRES_PASSWORD")

        fun isConfigured(): Boolean =
            !host.isNullOrBlank() && !database.isNullOrBlank() &&
            !user.isNullOrBlank() && !password.isNullOrBlank()
    }

    /**
     * Validate that all required environment variables are set
     * @throws IllegalStateException if required variables are missing
     */
    fun validate() {
        if (Api.url.isNullOrBlank()) {
            val errorMessage = buildString {
                appendLine("Missing required environment variable: SPRINGFORGE_API_URL")
                appendLine()
                appendLine("Debugging info:")
                appendLine("  Current directory: ${System.getProperty("user.dir")}")
                appendLine("  User home: ${System.getProperty("user.home")}")
                appendLine("  Loaded ${envVariables.size} variables from .env file")
                appendLine()
                appendLine("Solutions:")
                appendLine("  1. Add SPRINGFORGE_API_URL=<your API Gateway URL> to your .env file")
                appendLine("  2. Or set SPRINGFORGE_API_URL as a system environment variable")
            }
            throw IllegalStateException(errorMessage)
        }
    }

    /**
     * Check if configuration is available
     */
    fun isConfigured(): Boolean = Api.isConfigured()

    /**
     * Print configuration status (without exposing sensitive data)
     */
    fun printStatus() {
        println("=== SpringForge Configuration ===")
        println("API URL: ${if (Api.isConfigured()) Api.url else "NOT SET"}")
        println("API Key: ${if (Api.apiKey.isNullOrBlank()) "NOT SET (open access)" else "SET"}")
        println("Claude Model: ${Claude.modelId}")
        println("Max Tokens: ${Claude.maxTokens}")
        println("Anthropic Version: ${Claude.anthropicVersion}")
        println()
        println("GitHub MCP Server:")
        println("  PAT: ${if (GitHub.isConfigured()) "SET (${GitHub.personalAccessToken?.take(8)}...)" else "NOT SET"}")
        println("  Host: ${GitHub.host}")
        println("  Toolsets: ${GitHub.mcpToolsets}")
        println("  Read-Only: ${GitHub.readOnly}")
        println("================================")
    }
}

