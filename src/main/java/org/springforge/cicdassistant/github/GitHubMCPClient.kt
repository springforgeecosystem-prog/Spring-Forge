package org.springforge.cicdassistant.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springforge.cicdassistant.github.mcp.GitHubMCPServerConnector
import org.springforge.cicdassistant.mcp.models.*
import java.time.Instant

/**
 * Extracts Spring Boot project context from GitHub repositories using GitHub MCP Server
 *
 * This client provides the same MCPContext output as ProjectAnalyzerService,
 * but for remote GitHub repositories instead of local projects.
 *
 * Usage:
 * ```kotlin
 * val connector = GitHubMCPServerConnector()
 * val client = GitHubMCPClient(connector)
 *
 * val context = runBlocking {
 *     client.analyzeGitHubRepository("https://github.com/spring-projects/spring-petclinic")
 * }
 *
 * // Use with BedrockClient (same as local projects!)
 * bedrockClient.generateDockerfile(context)
 * ```
 */
class GitHubMCPClient(
    private val connector: GitHubMCPServerConnector
) {
    // Current branch being analyzed (set by analyzeGitHubRepository)
    private var branch: String = "main"

    /**
     * Analyze a GitHub repository and return MCP context
     *
     * @param repoUrl GitHub repository URL (formats supported):
     *   - https://github.com/owner/repo
     *   - github.com/owner/repo
     *   - owner/repo
     * @param branch Branch name (default: "main")
     * @return MCPContext compatible with BedrockClient
     * @throws IllegalArgumentException if URL format is invalid
     * @throws Exception if analysis fails
     */
    suspend fun analyzeGitHubRepository(repoUrl: String, branchName: String = "main"): MCPContext = withContext(Dispatchers.IO) {
        val (owner, repo) = parseGitHubUrl(repoUrl)
        this@GitHubMCPClient.branch = branchName // Store branch for use in file operations

        println("\n=== Analyzing GitHub Repository ===")
        println("Owner: $owner")
        println("Repository: $repo")
        println("Branch: $branchName")
        println("===================================\n")

        connector.connect()

        try {
            // Skip repository metadata - focus on file content analysis
            println("[1/6] Analyzing repository: $owner/$repo (branch: $branchName)...")
            println("✓ Repository: $repo")

            // Step 2: Detect build tool (Maven or Gradle) and architecture type
            println("[2/6] Detecting build tool and architecture type...")
            val buildTool = detectBuildTool(owner, repo)
            val architectureType = detectArchitectureType(owner, repo, buildTool)
            println("✓ Build tool: $buildTool")
            println("✓ Architecture: $architectureType")

            // Step 3: Extract dependencies
            println("[3/6] Extracting dependencies...")
            val dependencies = when (buildTool) {
                "Maven" -> extractMavenDependencies(owner, repo)
                "Gradle" -> extractGradleDependencies(owner, repo)
                else -> emptyList()
            }
            println("✓ Found ${dependencies.size} dependencies")

            // Step 4: Extract configuration (pass dependencies for database detection)
            println("[4/6] Extracting configuration...")
            val configuration = extractConfiguration(owner, repo, dependencies)
            println("✓ Server port: ${configuration.serverPort}")
            if (configuration.datasourceUrl != null) {
                println("✓ Database: ${extractDbType(configuration.datasourceUrl!!)}")
            }

            // Step 5: Analyze code structure
            println("[5/6] Analyzing code structure...")
            val codeStructure = analyzeCodeStructure(owner, repo)
            println("✓ Controllers: ${codeStructure.controllers.size}, Services: ${codeStructure.services.size}")

            // Step 6: Build MCPContext
            println("[6/6] Building MCP context...")

            // Extract database type from datasource URL for BedrockClient prompt
            val databaseType = configuration.datasourceUrl?.let { extractDbType(it) }

            val mcpContext = MCPContext(
                metadata = MCPMetadata(
                    analysisTimestamp = Instant.now().toString(),
                    mcpVersion = "1.0",
                    architectureType = architectureType // Detected from build files
                ),
                project = MCPProject(
                    name = repo,
                    groupId = "github.$owner",
                    version = "1.0.0",
                    buildTool = buildTool,
                    javaVersion = detectJavaVersion(owner, repo),
                    springBootVersion = detectSpringBootVersion(dependencies),
                    databaseType = databaseType,
                    dependencies = dependencies
                ),
                configuration = configuration,
                codeStructure = codeStructure
            )

            // Debug: Print MCPContext summary for verification
            println("\n=== MCP Context Summary ===")
            println("Database Type: ${mcpContext.project.databaseType ?: "None"}")
            println("Server Port: ${mcpContext.configuration?.serverPort ?: "8080"}")
            println("Datasource URL: ${mcpContext.configuration?.datasourceUrl ?: "None"}")
            println("============================\n")

            println("✓ GitHub repository analysis complete!\n")
            mcpContext

        } catch (e: Exception) {
            println("✗ GitHub repository analysis failed: ${e.message}")
            throw e
        } finally {
            connector.disconnect()
        }
    }

    /**
     * Parse GitHub URL into owner/repo pair
     */
    private fun parseGitHubUrl(url: String): Pair<String, String> {
        // Remove common prefixes and suffixes
        val cleaned = url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .removeSuffix("/")

        val parts = cleaned.split("/")
        if (parts.size < 2) {
            throw IllegalArgumentException(
                "Invalid GitHub URL: '$url'. Expected format: 'owner/repo' or 'https://github.com/owner/repo'"
            )
        }

        return Pair(parts[0], parts[1])
    }

    /**
     * Get repository metadata using get_repository tool
     */
    private fun getRepositoryInfo(owner: String, repo: String): Map<String, Any> {
        return connector.callTool(
            toolName = "get_repository",
            arguments = mapOf(
                "owner" to owner,
                "repo" to repo
            )
        )
    }

    /**
     * Detect build tool by checking for build files in priority order
     * Priority: Gradle wrappers > Gradle build files > Maven
     */
    private fun detectBuildTool(owner: String, repo: String): String {
        // Check for Gradle wrapper first (strongest indicator)
        try {
            getFileContents(owner, repo, "gradlew")
            println("  ✓ Found gradlew - using Gradle")
            return "Gradle"
        } catch (e: Exception) {
            // Gradle wrapper not found, continue checking
        }

        // Check for Gradle Kotlin DSL
        try {
            val content = getFileContents(owner, repo, "build.gradle.kts")
            if (content.isNotBlank()) {
                println("  ✓ Found build.gradle.kts - using Gradle")
                return "Gradle"
            }
        } catch (e: Exception) {
            // build.gradle.kts not found, continue checking
        }

        // Check for Gradle Groovy DSL
        try {
            val content = getFileContents(owner, repo, "build.gradle")
            if (content.isNotBlank()) {
                println("  ✓ Found build.gradle - using Gradle")
                return "Gradle"
            }
        } catch (e: Exception) {
            // build.gradle not found, continue checking
        }

        // Check for Maven as fallback
        try {
            val content = getFileContents(owner, repo, "pom.xml")
            if (content.isNotBlank()) {
                println("  ✓ Found pom.xml - using Maven")
                return "Maven"
            }
        } catch (e: Exception) {
            // pom.xml not found
        }

        println("  ⚠ Warning: No build file found (checking gradlew, build.gradle.kts, build.gradle, pom.xml)")
        return "Unknown"
    }

    /**
     * Detect architecture type (INTELLIJ_PLUGIN or SPRING_BOOT)
     * by analyzing build file plugins
     */
    private fun detectArchitectureType(owner: String, repo: String, buildTool: String): String {
        return when (buildTool) {
            "Gradle" -> {
                try {
                    // Try Kotlin DSL first
                    val buildContent = try {
                        getFileContents(owner, repo, "build.gradle.kts")
                    } catch (e: Exception) {
                        // Fall back to Groovy DSL
                        getFileContents(owner, repo, "build.gradle")
                    }

                    // Check for IntelliJ Platform Plugin
                    val hasIntellijPlugin = buildContent.contains("org.jetbrains.intellij") ||
                            buildContent.contains("org.jetbrains.kotlin.jvm") && buildContent.contains("intellij {")

                    if (hasIntellijPlugin) {
                        println("  ✓ Detected IntelliJ IDEA Plugin project")
                        return "INTELLIJ_PLUGIN"
                    }

                    // Check for Spring Boot
                    val hasSpringBoot = buildContent.contains("org.springframework.boot")

                    if (hasSpringBoot) {
                        println("  ✓ Detected Spring Boot project")
                        return "SPRING_BOOT"
                    }

                    println("  ⚠ Warning: Couldn't determine architecture type, defaulting to SPRING_BOOT")
                    "SPRING_BOOT"

                } catch (e: Exception) {
                    println("  ⚠ Warning: Failed to detect architecture type: ${e.message}")
                    "SPRING_BOOT"
                }
            }
            "Maven" -> {
                try {
                    val pomContent = getFileContents(owner, repo, "pom.xml")

                    // Check for Spring Boot parent or dependency
                    val hasSpringBoot = pomContent.contains("spring-boot-starter-parent") ||
                            pomContent.contains("spring-boot-starter")

                    if (hasSpringBoot) {
                        println("  ✓ Detected Spring Boot project")
                        return "SPRING_BOOT"
                    }

                    println("  ⚠ Warning: Couldn't determine architecture type, defaulting to SPRING_BOOT")
                    "SPRING_BOOT"

                } catch (e: Exception) {
                    println("  ⚠ Warning: Failed to detect architecture type: ${e.message}")
                    "SPRING_BOOT"
                }
            }
            else -> {
                println("  ⚠ Warning: Unknown build tool, defaulting to SPRING_BOOT")
                "SPRING_BOOT"
            }
        }
    }

    /**
     * Extract Maven dependencies from pom.xml
     */
    private fun extractMavenDependencies(owner: String, repo: String): List<String> {
        return try {
            val pomXml = getFileContents(owner, repo, "pom.xml")

            if (pomXml.isBlank()) {
                println("  Warning: pom.xml content is empty")
                return emptyList()
            }

            println("  ✓ pom.xml content length: ${pomXml.length} characters")

            // Simple regex parsing for dependencies
            val dependencyPattern = Regex(
                "<groupId>(.*?)</groupId>\\s*<artifactId>(.*?)</artifactId>(?:\\s*<version>(.*?)</version>)?"
            )

            dependencyPattern.findAll(pomXml)
                .map { match ->
                    val groupId = match.groupValues[1].trim()
                    val artifactId = match.groupValues[2].trim()
                    val version = match.groupValues.getOrNull(3)?.trim()

                    if (!version.isNullOrBlank()) {
                        "$groupId:$artifactId:$version"
                    } else {
                        "$groupId:$artifactId"
                    }
                }
                .distinct()
                .toList()

        } catch (e: Exception) {
            println("  Warning: Failed to parse Maven dependencies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract Gradle dependencies from build.gradle or build.gradle.kts
     */
    private fun extractGradleDependencies(owner: String, repo: String): List<String> {
        return try {
            val buildFile = try {
                getFileContents(owner, repo, "build.gradle.kts")
            } catch (e: Exception) {
                getFileContents(owner, repo, "build.gradle")
            }

            // Pattern for implementation/api/compileOnly dependencies
            val dependencyPatterns = listOf(
                Regex("""(?:implementation|api|compileOnly|runtimeOnly)\s*\(\s*["'](.*?)["']\s*\)"""),
                Regex("""(?:implementation|api|compileOnly|runtimeOnly)\s*["'](.*?)["']""")
            )

            val dependencies = mutableSetOf<String>()
            dependencyPatterns.forEach { pattern ->
                pattern.findAll(buildFile).forEach { match ->
                    dependencies.add(match.groupValues[1].trim())
                }
            }

            dependencies.toList()

        } catch (e: Exception) {
            println("  Warning: Failed to parse Gradle dependencies: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract Spring Boot configuration from application.yml or application.properties
     * Enhanced to match local analysis quality with database config detection
     */
    private fun extractConfiguration(owner: String, repo: String, dependencies: List<String>): MCPConfiguration {
        val configContent = try {
            getFileContents(owner, repo, "src/main/resources/application.yml")
        } catch (e: Exception) {
            try {
                getFileContents(owner, repo, "src/main/resources/application.properties")
            } catch (e: Exception) {
                ""
            }
        }

        if (configContent.isBlank()) {
            println("  Warning: No application.yml or application.properties found, using defaults")
        }

        // Parse server.port with multiple pattern support
        val port = extractServerPort(configContent)

        // Parse datasource configuration
        val datasourceUrl = extractProperty(configContent, "spring.datasource.url")
        val datasourceUsername = extractProperty(configContent, "spring.datasource.username")
        val datasourceDriver = extractProperty(configContent, "spring.datasource.driver-class-name")

        // Detect database type from dependencies if not in config
        val detectedDatasource = if (datasourceUrl.isNullOrBlank()) {
            detectDatabaseFromDependencies(dependencies)
        } else {
            datasourceUrl
        }

        // Parse active profiles
        val profilesPattern = Regex("""spring\.profiles\.active[:\s=]+(.+)""")
        val profilesStr = profilesPattern.find(configContent)?.groupValues?.get(1)?.trim()
        val activeProfiles = profilesStr?.split(",")?.map { it.trim() } ?: emptyList()

        // Parse JPA/Hibernate settings
        val jpaShowSql = extractProperty(configContent, "spring.jpa.show-sql")?.toBoolean() ?: false
        val jpaHibernate = extractProperty(configContent, "spring.jpa.hibernate.ddl-auto")

        println("  ✓ Detected server port: $port")
        if (!detectedDatasource.isNullOrBlank()) {
            println("  ✓ Detected database: ${extractDbType(detectedDatasource)}")
        }

        return MCPConfiguration(
            serverPort = port,
            datasourceUrl = detectedDatasource,
            activeProfiles = activeProfiles
        )
    }

    /**
     * Extract server port from configuration with multiple pattern support
     */
    private fun extractServerPort(configContent: String): String {
        // Try properties format: server.port=8080 or server.port = 8080
        val propsPattern = Regex("""server\.port\s*=\s*(\d+)""")
        propsPattern.find(configContent)?.groupValues?.get(1)?.let { return it }

        // Try YAML format: server:\n  port: 8080
        val yamlPattern = Regex("""server:\s*\n\s*port:\s*(\d+)""", RegexOption.MULTILINE)
        yamlPattern.find(configContent)?.groupValues?.get(1)?.let { return it }

        // Try inline YAML: server.port: 8080
        val inlineYamlPattern = Regex("""server\.port:\s*(\d+)""")
        inlineYamlPattern.find(configContent)?.groupValues?.get(1)?.let { return it }

        return "8080" // Default Spring Boot port
    }

    /**
     * Extract property value from configuration file (supports both .properties and .yml)
     */
    private fun extractProperty(configContent: String, propertyName: String): String? {
        // Try properties format: key=value
        val propsPattern = Regex("""${Regex.escape(propertyName)}\s*=\s*(.+)""")
        propsPattern.find(configContent)?.groupValues?.get(1)?.trim()?.let { return it }

        // Try YAML format: key: value
        val yamlPattern = Regex("""${Regex.escape(propertyName)}:\s*(.+)""")
        yamlPattern.find(configContent)?.groupValues?.get(1)?.trim()?.let { return it }

        return null
    }

    /**
     * Detect database configuration from dependencies when not explicitly configured
     */
    private fun detectDatabaseFromDependencies(dependencies: List<String>): String? {
        return when {
            dependencies.any { it.contains("mysql", ignoreCase = true) } ->
                "jdbc:mysql://db:3306/database_name"
            dependencies.any { it.contains("postgresql", ignoreCase = true) } ->
                "jdbc:postgresql://db:5432/database_name"
            dependencies.any { it.contains("h2", ignoreCase = true) } ->
                "jdbc:h2:mem:testdb"
            dependencies.any { it.contains("mariadb", ignoreCase = true) } ->
                "jdbc:mariadb://db:3306/database_name"
            else -> null
        }
    }

    /**
     * Extract database type from JDBC URL
     */
    private fun extractDbType(jdbcUrl: String): String {
        return when {
            jdbcUrl.contains("mysql", ignoreCase = true) -> "MySQL"
            jdbcUrl.contains("postgresql", ignoreCase = true) -> "PostgreSQL"
            jdbcUrl.contains("h2", ignoreCase = true) -> "H2"
            jdbcUrl.contains("mariadb", ignoreCase = true) -> "MariaDB"
            jdbcUrl.contains("oracle", ignoreCase = true) -> "Oracle"
            jdbcUrl.contains("sqlserver", ignoreCase = true) -> "SQL Server"
            else -> "Unknown"
        }
    }

    /**
     * Analyze code structure using GitHub Code Search API
     */
    private fun analyzeCodeStructure(owner: String, repo: String): MCPCodeStructure {
        // Search for Spring annotations
        val controllers = searchForAnnotation(owner, repo, "@RestController") +
                         searchForAnnotation(owner, repo, "@Controller")

        val services = searchForAnnotation(owner, repo, "@Service")

        val repositories = searchForAnnotation(owner, repo, "@Repository")

        val entities = searchForAnnotation(owner, repo, "@Entity")

        // Extract REST endpoints from controllers
        val endpoints = extractRestEndpoints(owner, repo, controllers)

        return MCPCodeStructure(
            controllers = controllers.distinct(),
            services = services.distinct(),
            repositories = repositories.distinct(),
            entities = entities.distinct(),
            restEndpoints = endpoints
        )
    }

    /**
     * Search for files containing a specific annotation
     */
    private fun searchForAnnotation(owner: String, repo: String, annotation: String): List<String> {
        return try {
            val result = connector.callTool(
                toolName = "search_code",
                arguments = mapOf(
                    "owner" to owner,
                    "repo" to repo,
                    "query" to annotation
                )
            )

            @Suppress("UNCHECKED_CAST")
            val items = result["items"] as? List<Map<String, Any>> ?: emptyList()

            items.mapNotNull { item ->
                val path = item["path"] as? String
                path?.let { extractClassName(it) }
            }

        } catch (e: Exception) {
            println("  Warning: Search for $annotation failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extract REST endpoints from controller files
     */
    private fun extractRestEndpoints(owner: String, repo: String, controllers: List<String>): List<MCPCodeStructure.RestEndpoint> {
        val endpoints = mutableListOf<MCPCodeStructure.RestEndpoint>()

        controllers.take(5).forEach { controller ->  // Limit to 5 controllers to avoid rate limits
            try {
                // Try to find the controller file
                val possiblePaths = listOf(
                    "src/main/java/${controller.replace(".", "/")}.java",
                    "src/main/kotlin/${controller.replace(".", "/")}.kt"
                )

                for (filePath in possiblePaths) {
                    try {
                        val content = getFileContents(owner, repo, filePath)

                        // Extract mapping annotations with method names
                        val mappingPatterns = listOf(
                            Regex("""@GetMapping\s*\(\s*["'](.+?)["']\s*\)[\s\S]*?(?:public|private|protected)?\s*\w+\s+(\w+)\s*\("""),
                            Regex("""@PostMapping\s*\(\s*["'](.+?)["']\s*\)[\s\S]*?(?:public|private|protected)?\s*\w+\s+(\w+)\s*\("""),
                            Regex("""@PutMapping\s*\(\s*["'](.+?)["']\s*\)[\s\S]*?(?:public|private|protected)?\s*\w+\s+(\w+)\s*\("""),
                            Regex("""@DeleteMapping\s*\(\s*["'](.+?)["']\s*\)[\s\S]*?(?:public|private|protected)?\s*\w+\s+(\w+)\s*\("""),
                            Regex("""@RequestMapping\s*\(\s*["'](.+?)["']\s*\)[\s\S]*?(?:public|private|protected)?\s*\w+\s+(\w+)\s*\(""")
                        )

                        mappingPatterns.forEach { pattern ->
                            pattern.findAll(content).forEach { match ->
                                val endpointPath = match.groupValues[1]
                                val methodName = match.groupValues.getOrNull(2) ?: "unknown"
                                val httpMethod = when {
                                    pattern.pattern.contains("Get") -> "GET"
                                    pattern.pattern.contains("Post") -> "POST"
                                    pattern.pattern.contains("Put") -> "PUT"
                                    pattern.pattern.contains("Delete") -> "DELETE"
                                    else -> "ANY"
                                }
                                endpoints.add(
                                    MCPCodeStructure.RestEndpoint(
                                        httpMethod = httpMethod,
                                        path = endpointPath,
                                        controller = controller,
                                        method = methodName
                                    )
                                )
                            }
                        }

                        break // Found the file, no need to try other paths

                    } catch (e: Exception) {
                        // File not found at this path, try next
                        continue
                    }
                }

            } catch (e: Exception) {
                println("  Warning: Failed to extract endpoints from $controller: ${e.message}")
            }
        }

        return endpoints.distinctBy { "${it.httpMethod} ${it.path}" }
    }

    /**
     * Get file contents from repository using get_file_contents tool
     */
    private fun getFileContents(owner: String, repo: String, path: String): String {
        val result = connector.callTool(
            toolName = "get_file_contents",
            arguments = mapOf(
                "owner" to owner,
                "repo" to repo,
                "path" to path,
                "ref" to branch // Specify which branch/ref to read from
            )
        )

        // Check if the MCP response indicates an error (e.g., file not found - 404)
        if (result["isError"] == true) {
            val content = result["content"] as? List<*>
            val errorMsg = (content?.firstOrNull() as? Map<*, *>)?.get("text") as? String
            println("  [getFileContents] MCP error for $path: ${errorMsg ?: "Unknown error"}")
            throw Exception("File not found or inaccessible: $path")
        }

        // MCP response format: {"content": [{"type":"text",...}, {"type":"resource","resource":{"text":"..."}}]}
        val content = result["content"] as? List<*>

        if (content == null) {
            println("  [getFileContents] ERROR: result['content'] is not a List for $path")
            return ""
        }

        // Find the resource object with the actual file text
        for ((index, item) in content.withIndex()) {
            val itemMap = item as? Map<*, *>
            if (itemMap == null) {
                println("  [getFileContents] WARNING: content[$index] is not a Map")
                continue
            }

            if (itemMap["type"] == "resource") {
                val resource = itemMap["resource"] as? Map<*, *>
                if (resource == null) {
                    println("  [getFileContents] WARNING: resource object is not a Map")
                    continue
                }

                val text = resource["text"] as? String
                if (text != null) {
                    println("  [getFileContents] ✓ Extracted ${text.length} chars from $path")
                    return text
                }
            }
        }

        println("  [getFileContents] ERROR: No resource text found for $path")
        return ""
    }

    /**
     * Extract class name from file path
     */
    private fun extractClassName(filePath: String): String? {
        return filePath
            .substringAfterLast("/")
            .removeSuffix(".java")
            .removeSuffix(".kt")
            .takeIf { it.isNotBlank() && it[0].isUpperCase() }
    }

    /**
     * Detect Spring Boot version from dependencies
     */
    private fun detectSpringBootVersion(dependencies: List<String>): String? {
        return dependencies
            .find { it.contains("spring-boot-starter") }
            ?.substringAfterLast(":")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Detect Java version from pom.xml or build.gradle
     */
    private fun detectJavaVersion(owner: String, repo: String): String {
        return try {
            // Try Maven first
            val pom = getFileContents(owner, repo, "pom.xml")
            val javaVersionPattern = Regex("""<maven\.compiler\.source>(.*?)</maven\.compiler\.source>""")
            javaVersionPattern.find(pom)?.groupValues?.get(1)?.trim() ?: "17"
        } catch (e: Exception) {
            try {
                // Try Gradle
                val gradle = try {
                    getFileContents(owner, repo, "build.gradle.kts")
                } catch (e: Exception) {
                    getFileContents(owner, repo, "build.gradle")
                }

                val javaVersionPattern = Regex("""sourceCompatibility\s*=\s*JavaVersion\.VERSION_(\d+)""")
                javaVersionPattern.find(gradle)?.groupValues?.get(1) ?: "17"
            } catch (e: Exception) {
                "17"  // Default to Java 17
            }
        }
    }
}
