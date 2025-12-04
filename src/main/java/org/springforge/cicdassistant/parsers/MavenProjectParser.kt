package org.springforge.cicdassistant.parsers

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Robust parser for Maven POM files
 * Extracts comprehensive project metadata for CI/CD generation
 *
 * Issue #3: Implement Maven POM Parser
 */
class MavenProjectParser {

    /**
     * Data class representing parsed Maven project metadata
     */
    data class MavenProjectMetadata(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val packaging: String,
        val name: String?,
        val description: String?,

        // Spring Boot specific
        val springBootVersion: String?,
        val springBootParentVersion: String?,
        val springBootStarters: List<String>,

        // Java configuration
        val javaVersion: String?,
        val mavenCompilerSource: String?,
        val mavenCompilerTarget: String?,

        // Dependencies
        val dependencies: List<Dependency>,
        val dependencyManagement: List<Dependency>,

        // Build plugins
        val buildPlugins: List<Plugin>,
        val pluginManagement: List<Plugin>,

        // Properties
        val properties: Map<String, String>,

        // Multi-module info
        val isMultiModule: Boolean,
        val modules: List<String>,

        // Database detection
        val databaseDrivers: List<DatabaseDriver>,

        // Messaging
        val messagingProviders: List<String>,

        // Cloud/Containerization
        val cloudProviders: List<String>,

        // Testing frameworks
        val testingFrameworks: List<String>
    ) {
        /**
         * Get effective Java version (resolves from multiple sources)
         */
        fun getEffectiveJavaVersion(): String {
            return javaVersion
                ?: mavenCompilerTarget
                ?: mavenCompilerSource
                ?: properties["java.version"]
                ?: properties["maven.compiler.release"]
                ?: "17" // Default to Java 17
        }

        /**
         * Check if project uses Spring Boot
         */
        fun isSpringBootProject(): Boolean {
            return springBootVersion != null
                || springBootParentVersion != null
                || springBootStarters.isNotEmpty()
        }

        /**
         * Get primary database driver if exists
         */
        fun getPrimaryDatabase(): DatabaseDriver? = databaseDrivers.firstOrNull()

        /**
         * Check if project has any database
         */
        fun hasDatabase(): Boolean = databaseDrivers.isNotEmpty()
    }

    /**
     * Represents a Maven dependency
     */
    data class Dependency(
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val scope: String?,
        val type: String?,
        val optional: Boolean = false
    )

    /**
     * Represents a Maven plugin
     */
    data class Plugin(
        val groupId: String,
        val artifactId: String,
        val version: String?,
        val configuration: Map<String, String> = emptyMap()
    )

    /**
     * Detected database drivers
     */
    data class DatabaseDriver(
        val type: DatabaseType,
        val artifactId: String,
        val version: String?
    )

    enum class DatabaseType {
        POSTGRESQL, MYSQL, MARIADB, ORACLE, SQLSERVER, H2, MONGODB, CASSANDRA, REDIS, UNKNOWN
    }

    /**
     * Parse Maven POM file and extract metadata
     * @param pomFile The pom.xml file to parse
     * @return Parsed project metadata
     * @throws MavenParseException if parsing fails
     */
    fun parse(pomFile: File): MavenProjectMetadata {
        if (!pomFile.exists()) {
            throw MavenParseException("POM file not found: ${pomFile.absolutePath}")
        }

        if (!pomFile.canRead()) {
            throw MavenParseException("Cannot read POM file: ${pomFile.absolutePath}")
        }

        return try {
            val doc = parsePomXml(pomFile)
            extractMetadata(doc)
        } catch (e: Exception) {
            throw MavenParseException("Failed to parse POM file: ${e.message}", e)
        }
    }

    /**
     * Parse POM file from project directory
     */
    fun parseFromProjectDir(projectDir: File): MavenProjectMetadata {
        val pomFile = File(projectDir, "pom.xml")
        return parse(pomFile)
    }

    /**
     * Parse XML document from POM file
     */
    private fun parsePomXml(pomFile: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false

        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(pomFile)
        doc.documentElement.normalize()

        return doc
    }

    /**
     * Extract all metadata from parsed document
     */
    private fun extractMetadata(doc: Document): MavenProjectMetadata {
        val root = doc.documentElement

        // Extract basic project coordinates
        val groupId = getTextContent(root, "groupId")
            ?: getTextContent(root, "parent", "groupId")
            ?: "unknown"
        val artifactId = getTextContent(root, "artifactId") ?: "unknown"
        val version = getTextContent(root, "version")
            ?: getTextContent(root, "parent", "version")
            ?: "1.0.0"
        val packaging = getTextContent(root, "packaging") ?: "jar"
        val name = getTextContent(root, "name")
        val description = getTextContent(root, "description")

        // Extract properties
        val properties = extractProperties(root)

        // Extract Spring Boot info
        val springBootParentVersion = getTextContent(root, "parent", "version")
            ?.takeIf {
                getTextContent(root, "parent", "artifactId")?.contains("spring-boot-starter-parent") == true
            }
        val springBootVersion = properties["spring-boot.version"] ?: springBootParentVersion

        // Extract Java version
        val javaVersion = properties["java.version"]
        val mavenCompilerSource = properties["maven.compiler.source"]
        val mavenCompilerTarget = properties["maven.compiler.target"]

        // Extract dependencies
        val dependencies = extractDependencies(root, "dependencies")
        val dependencyManagement = extractDependencies(root, "dependencyManagement", "dependencies")

        // Extract Spring Boot starters
        val springBootStarters = dependencies
            .filter { it.artifactId.startsWith("spring-boot-starter-") }
            .map { it.artifactId }

        // Detect databases
        val databaseDrivers = detectDatabaseDrivers(dependencies + dependencyManagement)

        // Detect messaging providers
        val messagingProviders = detectMessagingProviders(dependencies)

        // Detect cloud providers
        val cloudProviders = detectCloudProviders(dependencies)

        // Detect testing frameworks
        val testingFrameworks = detectTestingFrameworks(dependencies)

        // Extract build plugins
        val buildPlugins = extractPlugins(root, "build", "plugins")
        val pluginManagement = extractPlugins(root, "build", "pluginManagement", "plugins")

        // Multi-module detection
        val modules = extractModules(root)
        val isMultiModule = modules.isNotEmpty()

        return MavenProjectMetadata(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            packaging = packaging,
            name = name,
            description = description,
            springBootVersion = springBootVersion,
            springBootParentVersion = springBootParentVersion,
            springBootStarters = springBootStarters,
            javaVersion = javaVersion,
            mavenCompilerSource = mavenCompilerSource,
            mavenCompilerTarget = mavenCompilerTarget,
            dependencies = dependencies,
            dependencyManagement = dependencyManagement,
            buildPlugins = buildPlugins,
            pluginManagement = pluginManagement,
            properties = properties,
            isMultiModule = isMultiModule,
            modules = modules,
            databaseDrivers = databaseDrivers,
            messagingProviders = messagingProviders,
            cloudProviders = cloudProviders,
            testingFrameworks = testingFrameworks
        )
    }

    /**
     * Extract properties from POM
     */
    private fun extractProperties(root: Element): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        val propertiesNode = root.getElementsByTagName("properties").item(0) as? Element
            ?: return properties

        val childNodes = propertiesNode.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                properties[element.tagName] = element.textContent.trim()
            }
        }

        return properties
    }

    /**
     * Extract dependencies from POM
     */
    private fun extractDependencies(root: Element, vararg path: String): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()

        var currentElement: Element? = root
        for (tagName in path) {
            val nodeList = currentElement?.getElementsByTagName(tagName)
            if (nodeList == null || nodeList.length == 0) {
                return dependencies
            }
            currentElement = nodeList.item(0) as? Element
        }

        val dependencyNodes = currentElement?.getElementsByTagName("dependency") ?: return dependencies

        for (i in 0 until dependencyNodes.length) {
            val depNode = dependencyNodes.item(i)
            if (depNode.parentNode == currentElement) {
                val element = depNode as Element

                val groupId = getTextContent(element, "groupId") ?: continue
                val artifactId = getTextContent(element, "artifactId") ?: continue
                val version = getTextContent(element, "version")
                val scope = getTextContent(element, "scope")
                val type = getTextContent(element, "type")
                val optional = getTextContent(element, "optional")?.toBoolean() ?: false

                dependencies.add(
                    Dependency(
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version,
                        scope = scope,
                        type = type,
                        optional = optional
                    )
                )
            }
        }

        return dependencies
    }

    /**
     * Extract build plugins from POM
     */
    private fun extractPlugins(root: Element, vararg path: String): List<Plugin> {
        val plugins = mutableListOf<Plugin>()

        var currentElement: Element? = root
        for (tagName in path) {
            val nodeList = currentElement?.getElementsByTagName(tagName)
            if (nodeList == null || nodeList.length == 0) {
                return plugins
            }
            currentElement = nodeList.item(0) as? Element
        }

        val pluginNodes = currentElement?.getElementsByTagName("plugin") ?: return plugins

        for (i in 0 until pluginNodes.length) {
            val pluginNode = pluginNodes.item(i)
            if (pluginNode.parentNode == currentElement) {
                val element = pluginNode as Element

                val groupId = getTextContent(element, "groupId") ?: "org.apache.maven.plugins"
                val artifactId = getTextContent(element, "artifactId") ?: continue
                val version = getTextContent(element, "version")
                val configuration = extractPluginConfiguration(element)

                plugins.add(
                    Plugin(
                        groupId = groupId,
                        artifactId = artifactId,
                        version = version,
                        configuration = configuration
                    )
                )
            }
        }

        return plugins
    }

    /**
     * Extract plugin configuration
     */
    private fun extractPluginConfiguration(pluginElement: Element): Map<String, String> {
        val config = mutableMapOf<String, String>()

        val configNode = pluginElement.getElementsByTagName("configuration").item(0) as? Element
            ?: return config

        val childNodes = configNode.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                config[element.tagName] = element.textContent.trim()
            }
        }

        return config
    }

    /**
     * Extract modules for multi-module projects
     */
    private fun extractModules(root: Element): List<String> {
        val modules = mutableListOf<String>()

        val modulesNode = root.getElementsByTagName("modules").item(0) as? Element
            ?: return modules

        val moduleNodes = modulesNode.getElementsByTagName("module")
        for (i in 0 until moduleNodes.length) {
            val moduleText = moduleNodes.item(i).textContent.trim()
            if (moduleText.isNotEmpty()) {
                modules.add(moduleText)
            }
        }

        return modules
    }

    /**
     * Detect database drivers from dependencies
     */
    private fun detectDatabaseDrivers(dependencies: List<Dependency>): List<DatabaseDriver> {
        val drivers = mutableListOf<DatabaseDriver>()

        val databaseMapping = mapOf(
            "postgresql" to DatabaseType.POSTGRESQL,
            "mysql" to DatabaseType.MYSQL,
            "mariadb" to DatabaseType.MARIADB,
            "ojdbc" to DatabaseType.ORACLE,
            "mssql-jdbc" to DatabaseType.SQLSERVER,
            "h2" to DatabaseType.H2,
            "mongodb" to DatabaseType.MONGODB,
            "cassandra" to DatabaseType.CASSANDRA,
            "redis" to DatabaseType.REDIS
        )

        for (dep in dependencies) {
            val artifactLower = dep.artifactId.lowercase()
            for ((keyword, dbType) in databaseMapping) {
                if (artifactLower.contains(keyword)) {
                    drivers.add(
                        DatabaseDriver(
                            type = dbType,
                            artifactId = dep.artifactId,
                            version = dep.version
                        )
                    )
                    break
                }
            }
        }

        return drivers
    }

    /**
     * Detect messaging providers
     */
    private fun detectMessagingProviders(dependencies: List<Dependency>): List<String> {
        val providers = mutableListOf<String>()

        val messagingKeywords = listOf("rabbitmq", "kafka", "activemq", "artemis", "jms")

        for (dep in dependencies) {
            val artifactLower = dep.artifactId.lowercase()
            for (keyword in messagingKeywords) {
                if (artifactLower.contains(keyword)) {
                    providers.add(keyword.uppercase())
                    break
                }
            }
        }

        return providers.distinct()
    }

    /**
     * Detect cloud providers
     */
    private fun detectCloudProviders(dependencies: List<Dependency>): List<String> {
        val providers = mutableListOf<String>()

        val cloudKeywords = mapOf(
            "spring-cloud-aws" to "AWS",
            "spring-cloud-gcp" to "GCP",
            "spring-cloud-azure" to "Azure",
            "aws-java-sdk" to "AWS",
            "google-cloud" to "GCP"
        )

        for (dep in dependencies) {
            val artifactLower = dep.artifactId.lowercase()
            for ((keyword, provider) in cloudKeywords) {
                if (artifactLower.contains(keyword)) {
                    providers.add(provider)
                    break
                }
            }
        }

        return providers.distinct()
    }

    /**
     * Detect testing frameworks
     */
    private fun detectTestingFrameworks(dependencies: List<Dependency>): List<String> {
        val frameworks = mutableListOf<String>()

        val testingKeywords = listOf("junit", "testng", "mockito", "spring-boot-starter-test", "testcontainers")

        for (dep in dependencies) {
            val artifactLower = dep.artifactId.lowercase()
            for (keyword in testingKeywords) {
                if (artifactLower.contains(keyword)) {
                    frameworks.add(keyword)
                    break
                }
            }
        }

        return frameworks.distinct()
    }

    /**
     * Get text content from nested elements
     */
    private fun getTextContent(element: Element, vararg tagNames: String): String? {
        var current: Element? = element

        for (tagName in tagNames) {
            val nodeList = current?.getElementsByTagName(tagName)
            if (nodeList == null || nodeList.length == 0) {
                return null
            }
            current = nodeList.item(0) as? Element
        }

        return current?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Exception thrown when Maven POM parsing fails
 */
class MavenParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

