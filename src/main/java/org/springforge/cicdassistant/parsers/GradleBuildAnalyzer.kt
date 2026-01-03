package org.springforge.cicdassistant.parsers

import java.io.File

/**
 * AST-based analyzer for Gradle build files (build.gradle / build.gradle.kts)
 * Detects project type, plugins, dependencies, and configurations
 */
class GradleBuildAnalyzer {

    /**
     * Analyze Gradle build file and extract project metadata
     */
    fun analyze(projectDir: File): GradleBuildMetadata {
        val buildFile = findBuildFile(projectDir) 
            ?: throw IllegalArgumentException("No Gradle build file found in ${projectDir.absolutePath}")

        val content = buildFile.readText()
        val isKotlinDsl = buildFile.name.endsWith(".kts")

        return GradleBuildMetadata(
            projectType = detectProjectType(content),
            plugins = extractPlugins(content, isKotlinDsl),
            dependencies = extractDependencies(content, isKotlinDsl),
            javaVersion = extractJavaVersion(content),
            kotlinVersion = extractKotlinVersion(content),
            springBootVersion = extractSpringBootVersion(content),
            intellijPlatformVersion = extractIntellijPlatformVersion(content),
            buildFile = buildFile.name
        )
    }

    private fun findBuildFile(projectDir: File): File? {
        return listOf("build.gradle.kts", "build.gradle")
            .map { File(projectDir, it) }
            .firstOrNull { it.exists() }
    }

    /**
     * Detect project type from plugins block
     */
    private fun detectProjectType(content: String): ProjectType {
        return when {
            // IntelliJ Platform Plugin
            hasPlugin(content, "org.jetbrains.intellij") ||
            hasPlugin(content, "org.jetbrains.kotlin.jvm") && content.contains("intellij {") -> 
                ProjectType.INTELLIJ_PLUGIN

            // Spring Boot Application
            hasPlugin(content, "org.springframework.boot") -> 
                ProjectType.SPRING_BOOT

            // Kotlin Multiplatform
            hasPlugin(content, "org.jetbrains.kotlin.multiplatform") -> 
                ProjectType.KOTLIN_MULTIPLATFORM

            // Android Application
            hasPlugin(content, "com.android.application") -> 
                ProjectType.ANDROID_APP

            // Pure Kotlin Library
            hasPlugin(content, "org.jetbrains.kotlin.jvm") -> 
                ProjectType.KOTLIN_LIBRARY

            // Java Library/Application
            hasPlugin(content, "java") || hasPlugin(content, "java-library") -> 
                ProjectType.JAVA_LIBRARY

            else -> ProjectType.UNKNOWN
        }
    }

    /**
     * Check if a plugin is declared in the plugins block
     */
    private fun hasPlugin(content: String, pluginId: String): Boolean {
        // Kotlin DSL: id("org.jetbrains.intellij")
        val kotlinDslPattern = Regex("""id\s*\(\s*["']$pluginId["']\s*\)""")
        
        // Groovy DSL: id 'org.jetbrains.intellij'
        val groovyDslPattern = Regex("""id\s+['"]$pluginId['"]""")
        
        // Old style: apply plugin: 'org.jetbrains.intellij'
        val applyPluginPattern = Regex("""apply\s+plugin\s*:\s*['"]$pluginId['"]""")

        return kotlinDslPattern.containsMatchIn(content) ||
               groovyDslPattern.containsMatchIn(content) ||
               applyPluginPattern.containsMatchIn(content)
    }

    /**
     * Extract all plugins declared in the build file
     */
    private fun extractPlugins(content: String, isKotlinDsl: Boolean): List<GradlePlugin> {
        val plugins = mutableListOf<GradlePlugin>()

        // Extract from plugins {} block
        val pluginsBlockRegex = if (isKotlinDsl) {
            Regex("""plugins\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        } else {
            Regex("""plugins\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        }

        pluginsBlockRegex.find(content)?.let { match ->
            val pluginsBlock = match.groupValues[1]

            // Extract plugin IDs and versions
            val pluginPattern = if (isKotlinDsl) {
                // Kotlin DSL: id("org.jetbrains.intellij") version "1.17.0"
                Regex("""id\s*\(\s*["']([^"']+)["']\s*\)(?:\s+version\s+["']([^"']+)["'])?""")
            } else {
                // Groovy DSL: id 'org.jetbrains.intellij' version '1.17.0'
                Regex("""id\s+['"]([^'"]+)['"](?:\s+version\s+['"]([^'"]+)['"])?""")
            }

            pluginPattern.findAll(pluginsBlock).forEach { pluginMatch ->
                plugins.add(GradlePlugin(
                    id = pluginMatch.groupValues[1],
                    version = pluginMatch.groupValues.getOrNull(2)
                ))
            }
        }

        return plugins
    }

    /**
     * Extract dependencies from dependencies {} block
     */
    private fun extractDependencies(content: String, isKotlinDsl: Boolean): List<GradleDependency> {
        val dependencies = mutableListOf<GradleDependency>()

        // Extract dependencies {} block
        val dependenciesBlockRegex = Regex("""dependencies\s*\{([^}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        
        dependenciesBlockRegex.find(content)?.let { match ->
            val depsBlock = match.groupValues[1]

            // Match dependency declarations
            val depPattern = if (isKotlinDsl) {
                // implementation("group:artifact:version")
                Regex("""(implementation|api|compileOnly|runtimeOnly|testImplementation)\s*\(\s*["']([^"']+)["']\s*\)""")
            } else {
                // implementation 'group:artifact:version'
                Regex("""(implementation|api|compileOnly|runtimeOnly|testImplementation)\s+['"]([^'"]+)['"]""")
            }

            depPattern.findAll(depsBlock).forEach { depMatch ->
                val scope = depMatch.groupValues[1]
                val coordinate = depMatch.groupValues[2]

                // Parse Maven coordinate: "group:artifact:version"
                val parts = coordinate.split(":")
                if (parts.size >= 2) {
                    dependencies.add(GradleDependency(
                        scope = scope,
                        group = parts[0],
                        artifact = parts[1],
                        version = parts.getOrNull(2)
                    ))
                }
            }
        }

        return dependencies
    }

    /**
     * Extract Java version from java {} or sourceCompatibility
     */
    private fun extractJavaVersion(content: String): String? {
        // Kotlin DSL: java { sourceCompatibility = JavaVersion.VERSION_21 }
        Regex("""sourceCompatibility\s*=\s*JavaVersion\.VERSION_(\d+)""").find(content)?.let {
            return it.groupValues[1]
        }

        // Groovy DSL: sourceCompatibility = '21'
        Regex("""sourceCompatibility\s*=\s*['"]?(\d+)['"]?""").find(content)?.let {
            return it.groupValues[1]
        }

        // Kotlin options: kotlinOptions { jvmTarget = "21" }
        Regex("""jvmTarget\s*=\s*["'](\d+)["']""").find(content)?.let {
            return it.groupValues[1]
        }

        return null
    }

    /**
     * Extract Kotlin version from plugins block
     */
    private fun extractKotlinVersion(content: String): String? {
        // kotlin("jvm") version "1.9.23"
        val kotlinPluginRegex = Regex("""kotlin\s*\(\s*["']jvm["']\s*\)\s+version\s+["']([^"']+)["']""")
        return kotlinPluginRegex.find(content)?.groupValues?.get(1)
    }

    /**
     * Extract Spring Boot version
     */
    private fun extractSpringBootVersion(content: String): String? {
        // id("org.springframework.boot") version "3.2.0"
        val springBootRegex = Regex("""id\s*\(\s*["']org\.springframework\.boot["']\s*\)\s+version\s+["']([^"']+)["']""")
        return springBootRegex.find(content)?.groupValues?.get(1)
    }

    /**
     * Extract IntelliJ Platform version from intellij {} block
     */
    private fun extractIntellijPlatformVersion(content: String): String? {
        // intellij { version.set("IU-2024.3") }
        val versionSetRegex = Regex("""version\.set\s*\(\s*["']([^"']+)["']\s*\)""")
        return versionSetRegex.find(content)?.groupValues?.get(1)
    }
}

/**
 * Gradle build metadata extracted from AST analysis
 */
data class GradleBuildMetadata(
    val projectType: ProjectType,
    val plugins: List<GradlePlugin>,
    val dependencies: List<GradleDependency>,
    val javaVersion: String?,
    val kotlinVersion: String?,
    val springBootVersion: String?,
    val intellijPlatformVersion: String?,
    val buildFile: String
) {
    fun isIntellijPlugin(): Boolean = projectType == ProjectType.INTELLIJ_PLUGIN
    fun isSpringBoot(): Boolean = projectType == ProjectType.SPRING_BOOT
    fun hasSpringBootStarterWeb(): Boolean = dependencies.any { 
        it.artifact.contains("spring-boot-starter-web") 
    }
    fun hasSpringBootStarterData(): Boolean = dependencies.any { 
        it.artifact.contains("spring-boot-starter-data") 
    }
}

data class GradlePlugin(
    val id: String,
    val version: String?
)

data class GradleDependency(
    val scope: String, // implementation, api, etc.
    val group: String,
    val artifact: String,
    val version: String?
)

enum class ProjectType {
    INTELLIJ_PLUGIN,
    SPRING_BOOT,
    KOTLIN_MULTIPLATFORM,
    ANDROID_APP,
    KOTLIN_LIBRARY,
    JAVA_LIBRARY,
    MICROSERVICE,
    MONOLITH,
    REACTIVE_WEBFLUX,
    EVENT_DRIVEN,
    UNKNOWN
}
