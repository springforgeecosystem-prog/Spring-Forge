package org.springforge.cicdassistant.analyzer

import java.io.File

/**
 *  Analyzes actual project file structure
 * Ensures prompts are based on reality, not assumptions
 */
class ProjectStructureAnalyzer {

    data class ProjectStructure(
        val hasGradleWrapper: Boolean,
        val hasGradleDir: Boolean,
        val buildFiles: List<String>,
        val sourceDirectories: List<String>,
        val resourceDirectories: List<String>,
        val pluginXmlLocation: String?,
        val hasDockerfile: Boolean,
        val hasEnvFile: Boolean,
        val testDirectories: List<String>,
        val additionalFiles: List<String>
    )

    /**
     * Analyze actual project directory structure
     */
    fun analyze(projectDir: File): ProjectStructure {
        val sourceDir = File(projectDir, "src")
        val mainDir = File(sourceDir, "main")
        val testDir = File(sourceDir, "test")
        val resourcesDir = File(mainDir, "resources")

        return ProjectStructure(
            hasGradleWrapper = File(projectDir, "gradlew").exists() || File(projectDir, "gradlew.bat").exists(),
            hasGradleDir = File(projectDir, "gradle").exists(),
            buildFiles = findBuildFiles(projectDir),
            sourceDirectories = findSourceDirectories(projectDir),
            resourceDirectories = findResourceDirectories(projectDir),
            pluginXmlLocation = findPluginXml(projectDir),
            hasDockerfile = File(projectDir, "Dockerfile").exists(),
            hasEnvFile = File(projectDir, ".env").exists(),
            testDirectories = findTestDirectories(projectDir),
            additionalFiles = findAdditionalConfigFiles(projectDir)
        )
    }

    /**
     * Find all build files (build.gradle.kts, build.gradle, pom.xml)
     */
    private fun findBuildFiles(projectDir: File): List<String> {
        return listOfNotNull(
            "build.gradle.kts".takeIf { File(projectDir, it).exists() },
            "build.gradle".takeIf { File(projectDir, it).exists() },
            "pom.xml".takeIf { File(projectDir, it).exists() },
            "settings.gradle.kts".takeIf { File(projectDir, it).exists() },
            "settings.gradle".takeIf { File(projectDir, it).exists() }
        )
    }

    /**
     * Find actual source directories
     */
    private fun findSourceDirectories(projectDir: File): List<String> {
        val directories = mutableListOf<String>()
        val srcDir = File(projectDir, "src")
        
        if (!srcDir.exists()) return directories

        // Main source directories
        listOf("main/java", "main/kotlin", "main/groovy").forEach { path ->
            val dir = File(srcDir, path)
            if (dir.exists() && dir.isDirectory) {
                directories.add("src/$path")
            }
        }

        return directories
    }

    /**
     * Find actual resource directories
     */
    private fun findResourceDirectories(projectDir: File): List<String> {
        val directories = mutableListOf<String>()
        val srcDir = File(projectDir, "src")
        
        if (!srcDir.exists()) return directories

        listOf("main/resources", "test/resources").forEach { path ->
            val dir = File(srcDir, path)
            if (dir.exists() && dir.isDirectory) {
                directories.add("src/$path")
            }
        }

        return directories
    }

    /**
     * Find plugin.xml location for IntelliJ plugins
     */
    private fun findPluginXml(projectDir: File): String? {
        val possibleLocations = listOf(
            "src/main/resources/META-INF/plugin.xml",
            "src/main/resources/plugin.xml",
            "resources/META-INF/plugin.xml",
            "META-INF/plugin.xml",
            "plugin.xml"
        )

        return possibleLocations.firstOrNull { path ->
            File(projectDir, path).exists()
        }
    }

    /**
     * Find test directories
     */
    private fun findTestDirectories(projectDir: File): List<String> {
        val directories = mutableListOf<String>()
        val srcDir = File(projectDir, "src")
        
        if (!srcDir.exists()) return directories

        listOf("test/java", "test/kotlin", "test/groovy").forEach { path ->
            val dir = File(srcDir, path)
            if (dir.exists() && dir.isDirectory) {
                directories.add("src/$path")
            }
        }

        return directories
    }

    /**
     * Find additional configuration files
     */
    private fun findAdditionalConfigFiles(projectDir: File): List<String> {
        return listOfNotNull(
            ".gitignore".takeIf { File(projectDir, it).exists() },
            "README.md".takeIf { File(projectDir, it).exists() },
            ".env.example".takeIf { File(projectDir, it).exists() },
            "docker-compose.yml".takeIf { File(projectDir, it).exists() },
            "kubernetes.yaml".takeIf { File(projectDir, it).exists() }
        )
    }

    /**
     * Generate Dockerfile COPY instructions based on actual structure
     */
    fun generateCopyInstructions(structure: ProjectStructure): List<String> {
        val instructions = mutableListOf<String>()

        // Gradle wrapper
        if (structure.hasGradleWrapper) {
            instructions.add("COPY gradlew ./")
            if (File("gradlew.bat").exists()) {
                instructions.add("COPY gradlew.bat ./")
            }
        }

        // Gradle directory
        if (structure.hasGradleDir) {
            instructions.add("COPY gradle/ ./gradle/")
        }

        // Build files
        structure.buildFiles.forEach { file ->
            instructions.add("COPY $file ./")
        }

        // Source directories (copy entire src/ instead of individual dirs)
        if (structure.sourceDirectories.isNotEmpty() || structure.resourceDirectories.isNotEmpty()) {
            instructions.add("COPY src/ ./src/")
        }

        return instructions
    }
}
