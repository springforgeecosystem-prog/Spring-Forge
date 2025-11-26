package org.springforge.cicdassistant.config

import software.amazon.awssdk.regions.Region
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

    // AWS Configuration
    object AWS {
        val region: Region
            get() = Region.of(getEnv("AWS_REGION", "us-east-1"))

        val accessKeyId: String?
            get() = getEnv("AWS_ACCESS_KEY_ID")

        val secretAccessKey: String?
            get() = getEnv("AWS_SECRET_ACCESS_KEY")

        val sessionToken: String?
            get() = getEnv("AWS_SESSION_TOKEN")
    }

    // Claude Configuration
    object Claude {
        val modelId: String
            get() = getEnv("CLAUDE_MODEL_ID", "us.anthropic.claude-opus-4-20250514-v1:0")
                ?: "us.anthropic.claude-opus-4-20250514-v1:0"

        val maxTokens: Int
            get() = getEnv("CLAUDE_MAX_TOKENS", "4000")?.toIntOrNull() ?: 4000

        val anthropicVersion: String
            get() = getEnv("CLAUDE_ANTHROPIC_VERSION", "bedrock-2023-05-31")
                ?: "bedrock-2023-05-31"
    }

    /**
     * Validate that all required environment variables are set
     * @throws IllegalStateException if required variables are missing
     */
    fun validate() {
        val missing = mutableListOf<String>()

        if (AWS.accessKeyId.isNullOrBlank()) missing.add("AWS_ACCESS_KEY_ID")
        if (AWS.secretAccessKey.isNullOrBlank()) missing.add("AWS_SECRET_ACCESS_KEY")

        if (missing.isNotEmpty()) {
            val errorMessage = buildString {
                appendLine("Missing required environment variables: ${missing.joinToString(", ")}")
                appendLine()
                appendLine("Debugging info:")
                appendLine("  Current directory: ${System.getProperty("user.dir")}")
                appendLine("  User home: ${System.getProperty("user.home")}")
                appendLine("  Loaded ${envVariables.size} variables from .env file")
                appendLine()
                appendLine("Solutions:")
                appendLine("  1. Copy .env file to: ${System.getProperty("user.home")}\\.springforge.env")
                appendLine("  2. Or set system environment variables")
                appendLine("  3. Or ensure .env exists at: D:\\SLIIT\\SpringForge\\Spring-Forge\\.env")
            }
            throw IllegalStateException(errorMessage)
        }
    }

    /**
     * Check if configuration is available
     */
    fun isConfigured(): Boolean {
        return !AWS.accessKeyId.isNullOrBlank() && !AWS.secretAccessKey.isNullOrBlank()
    }

    /**
     * Print configuration status (without exposing sensitive data)
     */
    fun printStatus() {
        println("=== SpringForge Configuration ===")
        println("AWS Region: ${AWS.region}")
        println("AWS Access Key: ${if (AWS.accessKeyId.isNullOrBlank()) "NOT SET" else "SET (${AWS.accessKeyId?.take(8)}...)"}")
        println("AWS Secret Key: ${if (AWS.secretAccessKey.isNullOrBlank()) "NOT SET" else "SET"}")
        println("AWS Session Token: ${if (AWS.sessionToken.isNullOrBlank()) "NOT SET" else "SET"}")
        println("Claude Model: ${Claude.modelId}")
        println("Max Tokens: ${Claude.maxTokens}")
        println("Anthropic Version: ${Claude.anthropicVersion}")
        println("================================")
    }
}

