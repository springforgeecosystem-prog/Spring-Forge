package org.springforge.cicdassistant.parsers

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.Properties

/**
 * Scans Spring Boot configuration files (application.properties/yml)
 * Extracts server ports, database configs, and custom properties
 * 
 * Issue #5: Spring Boot Configuration Scanner
 */
class ConfigurationScanner {

    data class AppConfiguration(
        val serverPort: Int = 8080,
        val datasourceUrl: String? = null,
        val datasourceDriver: String? = null,
        val datasourceUsername: String? = null,
        val profiles: List<String> = emptyList(),
        val activeProfile: String? = null,
        val customProperties: Map<String, String> = emptyMap()
    )

    /**
     * Scan project for configuration files
     * Priority: application.properties > application.yml > application.yaml
     */
    fun scan(projectDir: File): AppConfiguration {
        val resourcesDir = File(projectDir, "src/main/resources")
        if (!resourcesDir.exists()) {
            return AppConfiguration()
        }

        // Try application.properties first
        val propertiesFile = File(resourcesDir, "application.properties")
        if (propertiesFile.exists()) {
            return parsePropertiesFile(propertiesFile)
        }

        // Try application.yml
        val ymlFile = File(resourcesDir, "application.yml")
        if (ymlFile.exists()) {
            return parseYamlFile(ymlFile)
        }

        // Try application.yaml
        val yamlFile = File(resourcesDir, "application.yaml")
        if (yamlFile.exists()) {
            return parseYamlFile(yamlFile)
        }

        return AppConfiguration()
    }

    /**
     * Parse .properties format
     */
    private fun parsePropertiesFile(file: File): AppConfiguration {
        val props = Properties()
        try {
            file.inputStream().use { props.load(it) }
        } catch (e: Exception) {
            println("⚠️  Failed to parse properties file: ${e.message}")
            return AppConfiguration()
        }

        return AppConfiguration(
            serverPort = props.getProperty("server.port")?.toIntOrNull() ?: 8080,
            datasourceUrl = props.getProperty("spring.datasource.url"),
            datasourceDriver = props.getProperty("spring.datasource.driver-class-name"),
            datasourceUsername = props.getProperty("spring.datasource.username"),
            profiles = extractProfiles(props),
            activeProfile = props.getProperty("spring.profiles.active"),
            customProperties = props.entries.associate { 
                it.key.toString() to it.value.toString() 
            }
        )
    }

    /**
     * Parse .yml/.yaml format using SnakeYAML
     */
    private fun parseYamlFile(file: File): AppConfiguration {
        try {
            val yaml = Yaml()
            val data = file.inputStream().use { 
                yaml.load<Map<String, Any>>(it) as? Map<String, Any> 
            } ?: return AppConfiguration()

            return AppConfiguration(
                serverPort = extractServerPort(data),
                datasourceUrl = extractNestedValue(data, "spring", "datasource", "url") as? String,
                datasourceDriver = extractNestedValue(data, "spring", "datasource", "driver-class-name") as? String,
                datasourceUsername = extractNestedValue(data, "spring", "datasource", "username") as? String,
                profiles = extractProfilesFromYaml(data),
                activeProfile = extractNestedValue(data, "spring", "profiles", "active") as? String,
                customProperties = flattenYaml(data)
            )
        } catch (e: Exception) {
            println("⚠️  Failed to parse YAML file: ${e.message}")
            return AppConfiguration()
        }
    }

    /**
     * Extract server port from YAML structure
     */
    private fun extractServerPort(data: Map<String, Any>): Int {
        val port = extractNestedValue(data, "server", "port")
        return when (port) {
            is Int -> port
            is String -> port.toIntOrNull() ?: 8080
            else -> 8080
        }
    }

    /**
     * Extract nested value from YAML map
     * Example: extractNestedValue(data, "spring", "datasource", "url")
     */
    private fun extractNestedValue(map: Map<String, Any>, vararg keys: String): Any? {
        var current: Any? = map
        for (key in keys) {
            current = (current as? Map<*, *>)?.get(key)
            if (current == null) return null
        }
        return current
    }

    /**
     * Extract profiles from properties
     */
    private fun extractProfiles(props: Properties): List<String> {
        val activeProfiles = props.getProperty("spring.profiles.active")
        return if (activeProfiles != null) {
            activeProfiles.split(",").map { it.trim() }
        } else {
            emptyList()
        }
    }

    /**
     * Extract profiles from YAML
     */
    private fun extractProfilesFromYaml(data: Map<String, Any>): List<String> {
        val activeProfiles = extractNestedValue(data, "spring", "profiles", "active")
        return when (activeProfiles) {
            is String -> activeProfiles.split(",").map { it.trim() }
            is List<*> -> activeProfiles.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    /**
     * Flatten nested YAML structure into flat map
     */
    private fun flattenYaml(
        map: Map<String, Any>, 
        prefix: String = ""
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        for ((key, value) in map) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
            
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.putAll(flattenYaml(value as Map<String, Any>, fullKey))
                }
                is List<*> -> {
                    result[fullKey] = value.joinToString(",")
                }
                else -> {
                    result[fullKey] = value.toString()
                }
            }
        }
        
        return result
    }

    /**
     * Detect database type from datasource URL
     */
    fun detectDatabaseFromUrl(url: String?): String? {
        if (url == null) return null
        
        return when {
            url.contains("postgresql", ignoreCase = true) -> "PostgreSQL"
            url.contains("mysql", ignoreCase = true) -> "MySQL"
            url.contains("mariadb", ignoreCase = true) -> "MariaDB"
            url.contains("oracle", ignoreCase = true) -> "Oracle"
            url.contains("sqlserver", ignoreCase = true) -> "SQL Server"
            url.contains("h2", ignoreCase = true) -> "H2"
            url.contains("mongodb", ignoreCase = true) -> "MongoDB"
            url.contains("redis", ignoreCase = true) -> "Redis"
            else -> "Unknown"
        }
    }
}
