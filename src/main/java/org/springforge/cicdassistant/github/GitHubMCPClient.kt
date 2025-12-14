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

    /**
     * Analyze a GitHub repository and return MCP context
     *
     * @param repoUrl GitHub repository URL (formats supported):
     *   - https://github.com/owner/repo
     *   - github.com/owner/repo
     *   - owner/repo
     * @return MCPContext compatible with BedrockClient
     * @throws IllegalArgumentException if URL format is invalid
     * @throws Exception if analysis fails
     */
    suspend fun analyzeGitHubRepository(repoUrl: String): MCPContext = withContext(Dispatchers.IO) {
        val (owner, repo) = parseGitHubUrl(repoUrl)

        println("\n=== Analyzing GitHub Repository ===")
        println("Owner: $owner")
        println("Repository: $repo")
        println("===================================\n")

        connector.connect()

        try {
            // Skip repository metadata - focus on file content analysis
            println("[1/6] Analyzing repository: $owner/$repo...")
            println("✓ Repository: $repo")

            // Step 2: Detect build tool (Maven or Gradle)
            println("[2/6] Detecting build tool...")
            val buildTool = detectBuildTool(owner, repo)
            println("✓ Build tool: $buildTool")

            // Step 3: Extract dependencies
            println("[3/6] Extracting dependencies...")
            val dependencies = when (buildTool) {
                "Maven" -> extractMavenDependencies(owner, repo)
                "Gradle" -> extractGradleDependencies(owner, repo)
                else -> emptyList()
            }
            println("✓ Found ${dependencies.size} dependencies")

            // Step 4: Extract configuration
            println("[4/6] Extracting configuration...")
            val configuration = extractConfiguration(owner, repo)
            println("✓ Server port: ${configuration.serverPort}")

            // Step 5: Analyze code structure
            println("[5/6] Analyzing code structure...")
            val codeStructure = analyzeCodeStructure(owner, repo)
            println("✓ Controllers: ${codeStructure.controllers.size}, Services: ${codeStructure.services.size}")

            // Step 6: Build MCPContext
            println("[6/6] Building MCP context...")
            val mcpContext = MCPContext(
                metadata = MCPMetadata(
                    analysisTimestamp = Instant.now().toString(),
                    mcpVersion = "1.0",
                    architectureType = "SPRING_BOOT" // GitHub repos analyzed as Spring Boot projects
                ),
                project = MCPProject(
                    name = repo,
                    groupId = "github.$owner",
                    version = "1.0.0",
                    buildTool = buildTool,
                    javaVersion = detectJavaVersion(owner, repo),
                    springBootVersion = detectSpringBootVersion(dependencies),
                    dependencies = dependencies
                ),
                configuration = configuration,
                codeStructure = codeStructure
            )

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
     * Detect build tool by checking for pom.xml or build.gradle files
     */
    private fun detectBuildTool(owner: String, repo: String): String {
        // Try Maven first
        return try {
            getFileContents(owner, repo, "pom.xml")
            "Maven"
        } catch (e: Exception) {
            // Try Gradle (Kotlin DSL)
            try {
                getFileContents(owner, repo, "build.gradle.kts")
                "Gradle"
            } catch (e: Exception) {
                // Try Gradle (Groovy)
                try {
                    getFileContents(owner, repo, "build.gradle")
                    "Gradle"
                } catch (e: Exception) {
                    println("  Warning: No build file found (pom.xml, build.gradle, or build.gradle.kts)")
                    "Unknown"
                }
            }
        }
    }

    /**
     * Extract Maven dependencies from pom.xml
     */
    private fun extractMavenDependencies(owner: String, repo: String): List<String> {
        return try {
            val pomXml = getFileContents(owner, repo, "pom.xml")

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
     */
    private fun extractConfiguration(owner: String, repo: String): MCPConfiguration {
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

        // Parse server.port
        val portPattern = Regex("""server\.port[:\s=]+(\d+)""")
        val port = portPattern.find(configContent)?.groupValues?.get(1) ?: "8080"

        // Parse datasource URL
        val datasourcePattern = Regex("""spring\.datasource\.url[:\s=]+(.+)""")
        val datasourceUrl = datasourcePattern.find(configContent)?.groupValues?.get(1)?.trim()

        // Parse active profiles
        val profilesPattern = Regex("""spring\.profiles\.active[:\s=]+(.+)""")
        val profilesStr = profilesPattern.find(configContent)?.groupValues?.get(1)?.trim()
        val activeProfiles = profilesStr?.split(",")?.map { it.trim() } ?: emptyList()

        return MCPConfiguration(
            serverPort = port,
            datasourceUrl = datasourceUrl,
            activeProfiles = activeProfiles
        )
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
                "path" to path
            )
        )

        return result["content"] as? String ?: ""
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
