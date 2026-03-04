package org.springforge.cicdassistant.config

/**
 * Configuration manager for environment variables.
 *
 * The .env file from the plugin development root is bundled INTO the JAR
 * at build time (via processResources in build.gradle.kts). This means
 * the plugin always carries its own config — no searching open projects,
 * no walking up directory trees.
 *
 * Resolution order (first non-null wins):
 *   1. System environment variable  (allows runtime overrides / CI)
 *   2. Value from bundled .env resource inside the JAR
 *   3. Provided default value
 */
object EnvironmentConfig {

    /**
     * Variables loaded from the .env file bundled inside the JAR.
     */
    private val bundledEnvVariables: Map<String, String> by lazy {
        loadBundledEnv()
    }

    /**
     * Load .env from the classpath (bundled into the JAR by Gradle).
     */
    private fun loadBundledEnv(): Map<String, String> {
        val envMap = mutableMapOf<String, String>()
        try {
            val stream = EnvironmentConfig::class.java.classLoader.getResourceAsStream(".env")
            if (stream != null) {
                stream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                envMap[parts[0].trim()] = parts[1].trim()
                            }
                        }
                    }
                }
                println("[SpringForge] Loaded ${envMap.size} variables from bundled .env")
            } else {
                System.err.println("[SpringForge] No bundled .env found in JAR resources.")
                System.err.println("[SpringForge] Make sure .env exists in project root when building.")
            }
        } catch (e: Exception) {
            System.err.println("[SpringForge] Failed to read bundled .env: ${e.message}")
        }
        return envMap
    }

    /**
     * Resolve an environment variable.
     * Priority: System env → bundled .env → default.
     */
    private fun getEnv(key: String, defaultValue: String? = null): String? {
        return System.getenv(key)
            ?: bundledEnvVariables[key]
            ?: defaultValue
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
                appendLine("  Bundled .env variables loaded: ${bundledEnvVariables.size}")
                appendLine()
                appendLine("Solutions:")
                appendLine("  1. Make sure .env exists in the plugin project root before building")
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

