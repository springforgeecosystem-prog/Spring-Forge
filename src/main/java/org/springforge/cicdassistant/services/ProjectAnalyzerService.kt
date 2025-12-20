package org.springforge.cicdassistant.services

import com.intellij.openapi.project.Project
import org.springforge.cicdassistant.parsers.MavenParseException
import org.springforge.cicdassistant.parsers.MavenProjectParser
import org.springforge.cicdassistant.parsers.CodeStructureAnalyzer
import org.springforge.cicdassistant.parsers.CodeStructure
import org.springforge.cicdassistant.parsers.GradleBuildAnalyzer
import org.springforge.cicdassistant.parsers.ConfigurationScanner
import org.springforge.cicdassistant.mcp.MCPClient
import org.springforge.cicdassistant.mcp.models.MCPContext
import org.w3c.dom.Element
import java.io.File

/**
 * Service to analyze Spring Boot project structure and extract metadata
 * Uses MavenProjectParser + GradleBuildAnalyzer for build file analysis
 * + CodeStructureAnalyzer for AST-based code analysis
 * + ConfigurationScanner for application.properties/yml analysis (Issue #5)
 * + MCPClient for packaging context in MCP format (Issue #6)
 */
class ProjectAnalyzerService {

    private val mavenParser = MavenProjectParser()
    private val gradleAnalyzer = GradleBuildAnalyzer()
    private val codeAnalyzer = CodeStructureAnalyzer()
    private val configScanner = ConfigurationScanner()
    private val mcpClient = MCPClient()

    data class ProjectInfo(
        val projectName: String,
        val buildTool: BuildTool,
        val javaVersion: String,
        val springBootVersion: String,
        val dependencies: List<String>,
        val packageName: String,
        val hasDatabase: Boolean,
        val databaseType: String?,
        val hasRedis: Boolean,
        val hasRabbitMQ: Boolean,
        val hasKafka: Boolean,
        val port: Int = 8080,
        //  AST-derived information
        val codeStructure: CodeStructure? = null,
        val hasRestControllers: Boolean = false,
        val apiEndpointCount: Int = 0,
        val hasAsyncProcessing: Boolean = false,
        val hasScheduledTasks: Boolean = false,
        val hasMessageListeners: Boolean = false,
        val architectureType: String = "UNKNOWN"
    )

    enum class BuildTool {
        MAVEN, GRADLE, UNKNOWN
    }

    /**
     * Analyze a Spring Boot project and extract comprehensive information
     */
    fun analyzeProject(project: Project): ProjectInfo {
        val basePath = project.basePath ?: throw IllegalStateException("Project path not found")
        val projectDir = File(basePath)

        val buildTool = detectBuildTool(projectDir)

        return when (buildTool) {
            BuildTool.MAVEN -> analyzeMavenProject(projectDir, project.name)
            BuildTool.GRADLE -> analyzeGradleProject(projectDir, project.name)
            BuildTool.UNKNOWN -> createDefaultProjectInfo(project.name)
        }
    }

    /**
     * Detect which build tool the project uses
     */
    private fun detectBuildTool(projectDir: File): BuildTool {
        return when {
            File(projectDir, "pom.xml").exists() -> BuildTool.MAVEN
            File(projectDir, "build.gradle").exists() ||
            File(projectDir, "build.gradle.kts").exists() -> BuildTool.GRADLE
            else -> BuildTool.UNKNOWN
        }
    }

    /**
     * Analyze Maven project (pom.xml) using MavenProjectParser + AST analysis
     */
    private fun analyzeMavenProject(projectDir: File, projectName: String): ProjectInfo {
        return try {
            val metadata = mavenParser.parseFromProjectDir(projectDir)

            // Convert MavenProjectMetadata to ProjectInfo
            val dependencies = metadata.dependencies.map { it.artifactId }
            val databaseInfo = if (metadata.hasDatabase()) {
                Pair(true, metadata.getPrimaryDatabase()?.type?.name)
            } else {
                Pair(false, null)
            }

            //  Perform AST analysis to understand code structure
            val codeStructure = try {
                codeAnalyzer.analyze(projectDir)
            } catch (e: Exception) {
                println(" AST analysis failed: ${e.message}")
                null
            }

            //  Issue #5: Extract configuration from application.properties/yml
            val appConfig = configScanner.scan(projectDir)

            ProjectInfo(
                projectName = metadata.name ?: projectName,
                buildTool = BuildTool.MAVEN,
                javaVersion = metadata.getEffectiveJavaVersion(),
                springBootVersion = metadata.springBootVersion ?: metadata.springBootParentVersion ?: "3.2.0",
                dependencies = dependencies,
                packageName = extractPackageName(projectDir),
                hasDatabase = databaseInfo.first || appConfig.datasourceUrl != null,
                databaseType = configScanner.detectDatabaseFromUrl(appConfig.datasourceUrl) ?: databaseInfo.second,
                hasRedis = metadata.messagingProviders.any { it.contains("REDIS") },
                hasRabbitMQ = metadata.messagingProviders.any { it.contains("RABBITMQ") },
                hasKafka = metadata.messagingProviders.any { it.contains("KAFKA") },
                port = appConfig.serverPort,
                //  AST-derived insights
                codeStructure = codeStructure,
                hasRestControllers = codeStructure?.restControllers?.isNotEmpty() == true,
                apiEndpointCount = codeStructure?.restControllers?.sumOf { it.endpoints.size } ?: 0,
                hasAsyncProcessing = codeStructure?.services?.any { it.hasAsyncMethods } == true ||
                                    codeStructure?.restControllers?.any { controller -> 
                                        controller.endpoints.any { it.isAsync } 
                                    } == true,
                hasScheduledTasks = codeStructure?.scheduledTasks?.isNotEmpty() == true,
                hasMessageListeners = codeStructure?.messageListeners?.isNotEmpty() == true,
                architectureType = codeStructure?.architectureType?.name ?: "UNKNOWN"
            )
        } catch (e: MavenParseException) {
            println("Failed to parse Maven project: ${e.message}")
            createDefaultProjectInfo(projectName)
        } catch (e: Exception) {
            println("Unexpected error analyzing Maven project: ${e.message}")
            createDefaultProjectInfo(projectName)
        }
    }

    /**
     *  Enhanced Gradle analysis with AST-based build file parsing + code analysis
     */
    private fun analyzeGradleProject(projectDir: File, projectName: String): ProjectInfo {
        return try {
            // 1. Analyze build.gradle/build.gradle.kts with AST
            val gradleMetadata = gradleAnalyzer.analyze(projectDir)

            // 2. Analyze code structure if it's NOT an IntelliJ plugin
            val codeStructure = if (!gradleMetadata.isIntellijPlugin()) {
                try {
                    codeAnalyzer.analyze(projectDir)
                } catch (e: Exception) {
                    println(" Code AST analysis failed: ${e.message}")
                    null
                }
            } else {
                println(" Detected IntelliJ Plugin - skipping Java/Kotlin code analysis")
                null
            }

            //  Issue #5: Extract configuration from application.properties/yml
            val appConfig = configScanner.scan(projectDir)

            // 3. Convert to ProjectInfo
            val dependencies = gradleMetadata.dependencies.map { "${it.group}:${it.artifact}" }
            
            ProjectInfo(
                projectName = projectName,
                buildTool = BuildTool.GRADLE,
                javaVersion = gradleMetadata.javaVersion ?: "17",
                springBootVersion = gradleMetadata.springBootVersion ?: "3.2.0",
                dependencies = dependencies,
                packageName = extractPackageName(projectDir),
                hasDatabase = gradleMetadata.hasSpringBootStarterData() || appConfig.datasourceUrl != null,
                databaseType = configScanner.detectDatabaseFromUrl(appConfig.datasourceUrl) 
                    ?: if (gradleMetadata.hasSpringBootStarterData()) "PostgreSQL" else null,
                hasRedis = dependencies.any { it.contains("redis", ignoreCase = true) },
                hasRabbitMQ = dependencies.any { it.contains("rabbitmq", ignoreCase = true) },
                hasKafka = dependencies.any { it.contains("kafka", ignoreCase = true) },
                port = appConfig.serverPort,
                //  AST-derived insights
                codeStructure = codeStructure,
                hasRestControllers = codeStructure?.restControllers?.isNotEmpty() == true,
                apiEndpointCount = codeStructure?.restControllers?.sumOf { it.endpoints.size } ?: 0,
                hasAsyncProcessing = codeStructure?.services?.any { it.hasAsyncMethods } == true,
                hasScheduledTasks = codeStructure?.scheduledTasks?.isNotEmpty() == true,
                hasMessageListeners = codeStructure?.messageListeners?.isNotEmpty() == true,
                architectureType = when {
                    gradleMetadata.isIntellijPlugin() -> {
                        println("[ProjectAnalyzerService] ✓ Detected INTELLIJ_PLUGIN from Gradle metadata")
                        "INTELLIJ_PLUGIN"
                    }
                    codeStructure?.architectureType != null -> {
                        println("[ProjectAnalyzerService] ✓ Detected ${codeStructure.architectureType.name} from code structure")
                        codeStructure.architectureType.name
                    }
                    gradleMetadata.isSpringBoot() -> {
                        println("[ProjectAnalyzerService] ✓ Detected SPRING_BOOT from Gradle metadata")
                        "SPRING_BOOT"
                    }
                    else -> {
                        println("[ProjectAnalyzerService] ⚠ Unknown architecture type, defaulting to UNKNOWN")
                        "UNKNOWN"
                    }
                }
            )
        } catch (e: Exception) {
            println("Failed to analyze Gradle project: ${e.message}")
            createDefaultProjectInfo(projectName)
        }
    }

    /**
     * Extract dependencies from Gradle build file
     */
    private fun extractGradleDependencies(content: String): List<String> {
        val dependencies = mutableListOf<String>()
        val dependencyPattern = Regex("""implementation\s*\(?\s*["']([^"']+)["']""")

        dependencyPattern.findAll(content).forEach { match ->
            val dep = match.groupValues[1]
            // Extract artifact ID (last part after :)
            val artifactId = dep.split(":").lastOrNull()
            if (artifactId != null) {
                dependencies.add(artifactId)
            }
        }

        return dependencies
    }

    /**
     * Extract Java version from Maven pom.xml
     */
    private fun extractJavaVersionFromMaven(doc: org.w3c.dom.Document): String {
        // Try java.version property
        val properties = doc.getElementsByTagName("java.version")
        if (properties.length > 0) {
            return properties.item(0).textContent
        }

        // Try maven.compiler.target
        val compilerTarget = doc.getElementsByTagName("maven.compiler.target")
        if (compilerTarget.length > 0) {
            return compilerTarget.item(0).textContent
        }

        return "17" // Default
    }

    /**
     * Extract Spring Boot version from Maven pom.xml
     */
    private fun extractSpringBootVersionFromMaven(doc: org.w3c.dom.Document): String {
        val parent = doc.getElementsByTagName("parent").item(0) as? Element
        if (parent != null) {
            val version = parent.getElementsByTagName("version").item(0)?.textContent
            if (version != null) {
                return version
            }
        }
        return "3.2.0" // Default
    }

    /**
     * Extract Java version from Gradle build file
     */
    private fun extractJavaVersionFromGradle(content: String): String {
        val javaVersionPattern = Regex("""jvmTarget\s*=\s*["'](\d+)["']""")
        val match = javaVersionPattern.find(content)
        return match?.groupValues?.get(1) ?: "17"
    }

    /**
     * Extract Spring Boot version from Gradle build file
     */
    private fun extractSpringBootVersionFromGradle(content: String): String {
        val springBootPattern = Regex("""org\.springframework\.boot['"]\s+version\s+["']([^"']+)["']""")
        val match = springBootPattern.find(content)
        return match?.groupValues?.get(1) ?: "3.2.0"
    }

    /**
     * Detect database type from dependencies
     */
    private fun detectDatabase(dependencies: List<String>): Pair<Boolean, String?> {
        val databaseDeps = mapOf(
            "postgresql" to "PostgreSQL",
            "mysql" to "MySQL",
            "mariadb" to "MariaDB",
            "h2" to "H2",
            "mongodb" to "MongoDB",
            "spring-boot-starter-data-jpa" to "JPA (SQL Database)"
        )

        for ((key, value) in databaseDeps) {
            if (dependencies.any { it.contains(key, ignoreCase = true) }) {
                return Pair(true, value)
            }
        }

        return Pair(false, null)
    }

    /**
     * Extract base package name from project structure
     */
    private fun extractPackageName(projectDir: File): String {
        val srcDir = File(projectDir, "src/main/java")
        if (!srcDir.exists()) {
            return "com.example.app"
        }

        // Find the first Java file with @SpringBootApplication
        return findSpringBootMainClass(srcDir)?.substringBeforeLast(".") ?: "com.example.app"
    }

    /**
     * Find Spring Boot main class package
     */
    private fun findSpringBootMainClass(dir: File): String? {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val result = findSpringBootMainClass(file)
                if (result != null) return result
            } else if (file.extension == "java") {
                val content = file.readText()
                if (content.contains("@SpringBootApplication")) {
                    // Extract package name from file
                    val packagePattern = Regex("""package\s+([\w.]+)\s*;""")
                    val match = packagePattern.find(content)
                    return match?.groupValues?.get(1)
                }
            }
        }
        return null
    }

    /**
     * Create default project info when analysis fails
     */
    private fun createDefaultProjectInfo(projectName: String): ProjectInfo {
        return ProjectInfo(
            projectName = projectName,
            buildTool = BuildTool.UNKNOWN,
            javaVersion = "17",
            springBootVersion = "3.2.0",
            dependencies = emptyList(),
            packageName = "com.example.app",
            hasDatabase = false,
            databaseType = null,
            hasRedis = false,
            hasRabbitMQ = false,
            hasKafka = false
        )
    }

    /**
     * Format project info as string for Claude prompt
     */
    fun formatProjectInfoForPrompt(info: ProjectInfo): String {
        return buildString {
            appendLine("Project Name: ${info.projectName}")
            appendLine("Build Tool: ${info.buildTool}")
            appendLine("Java Version: ${info.javaVersion}")
            appendLine("Spring Boot Version: ${info.springBootVersion}")
            appendLine("Package: ${info.packageName}")
            appendLine("Port: ${info.port}")

            if (info.hasDatabase) {
                appendLine("Database: ${info.databaseType}")
            }

            if (info.hasRedis) appendLine("Uses: Redis")
            if (info.hasRabbitMQ) appendLine("Uses: RabbitMQ")
            if (info.hasKafka) appendLine("Uses: Kafka")

            if (info.dependencies.isNotEmpty()) {
                appendLine("\nKey Dependencies:")
                info.dependencies.take(10).forEach { dep ->
                    appendLine("  - $dep")
                }
            }
        }
    }

    // ============ MCP Integration Methods (Issue #6d) ============

    /**
     * Analyzes a Spring Boot project and packages the result in MCP format.
     * This combines project analysis with MCP packaging and sensitive data filtering.
     *
     * @param project The IntelliJ Project to analyze
     * @return MCPContext with filtered sensitive data, ready for AWS Bedrock
     */
    fun analyzeProjectWithMCP(project: Project): MCPContext {
        // 1. Perform standard project analysis
        val projectInfo = analyzeProject(project)
        
        // 2. Package into MCP format
        val mcpContext = mcpClient.packageContext(projectInfo)
        
        // 3. Filter sensitive data before returning
        return mcpClient.filterSensitiveData(mcpContext)
    }

    /**
     * Convenience method: Analyzes project and returns JSON ready for AWS Bedrock API.
     * This is the recommended method for getting MCP context to send to Claude.
     *
     * Workflow:
     * 1. Analyze Spring Boot project (Maven/Gradle, code structure, config)
     * 2. Package into MCP format
     * 3. Filter sensitive data (passwords, API keys, secrets)
     * 4. Serialize to JSON
     * 5. Validate size (must be ≤180K tokens for Claude 4)
     *
     * @param project The IntelliJ Project to analyze
     * @return JSON string ready for AWS Bedrock API, or throws exception if context too large
     * @throws IllegalStateException if context exceeds token limit
     */
    fun getMCPContextForBedrock(project: Project): String {
        // Get filtered MCP context
        val mcpContext = analyzeProjectWithMCP(project)
        
        // Validate size before serialization
        if (!mcpClient.validateContextSize(mcpContext)) {
            val sizeInfo = mcpClient.getContextSizeInfo(mcpContext)
            throw IllegalStateException(
                "MCP context too large for Claude 4: ${sizeInfo["estimated_tokens"]} tokens " +
                "(max: ${sizeInfo["max_tokens"]}). Consider reducing context or implementing chunking."
            )
        }
        
        // Serialize to JSON
        return mcpClient.serializeForBedrock(mcpContext)
    }

    /**
     * Gets detailed size information about the MCP context for a project.
     * Useful for monitoring and debugging context size issues.
     *
     * @param project The IntelliJ Project to analyze
     * @return Map with size metrics (characters, tokens, percentage used, within_limit)
     */
    fun getMCPContextSizeInfo(project: Project): Map<String, Any> {
        val mcpContext = analyzeProjectWithMCP(project)
        return mcpClient.getContextSizeInfo(mcpContext)
    }
}

