package org.springforge.runtimeanalysis.collector

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

object ProjectEnvExtractor {

    fun extractEnvironment(project: Project): Map<String, String> {
        // 1. Grab the JDK Version
        val jdkVersion = ProjectRootManager.getInstance(project).projectSdk?.versionString ?: "Unknown JDK"

        // 2. Scan for Spring Boot Version and Build File Content
        var springBootVersion = "Unknown Spring Boot"
        var buildFileName = "None"
        var buildFileContent = ""

        val basePath = project.basePath
        System.err.println("[ProjectEnvExtractor] basePath: $basePath")

        if (basePath != null) {
            // Try pom.xml
            val pomFile = File(basePath, "pom.xml")
            System.err.println("[ProjectEnvExtractor] Checking pom.xml: ${pomFile.absolutePath}, exists: ${pomFile.exists()}")
            
            val gradleFile = File(basePath, "build.gradle")
            val gradleKtsFile = File(basePath, "build.gradle.kts")

            if (pomFile.exists()) {
                try {
                    buildFileName = "pom.xml"
                    buildFileContent = pomFile.readText()
                    System.err.println("[ProjectEnvExtractor] Read pom.xml successfully, size: ${buildFileContent.length}")

                    // Matches <artifactId>spring-boot-starter-parent</artifactId> then gets the <version>
                    val regex = Regex("""<artifactId>spring-boot-starter-parent</artifactId>\s*<version>(.*?)</version>""")
                    val match = regex.find(buildFileContent)
                    if (match != null) {
                        springBootVersion = match.groupValues[1]
                    }
                } catch (e: Exception) {
                    System.err.println("[ProjectEnvExtractor] Error reading pom.xml: ${e.message}")
                }
            } else if (gradleFile.exists() || gradleKtsFile.exists()) {
                val fileToRead = if (gradleFile.exists()) gradleFile else gradleKtsFile
                try {
                    buildFileName = fileToRead.name
                    buildFileContent = fileToRead.readText() // Read the whole file
                    System.err.println("[ProjectEnvExtractor] Read ${buildFileName} successfully, size: ${buildFileContent.length}")

                    // Matches id 'org.springframework.boot' version '3.2.0'
                    val regex = Regex("""id\s*['"]org\.springframework\.boot['"]\s*version\s*['"](.*?)['"]""")
                    val match = regex.find(buildFileContent)
                    if (match != null) {
                        springBootVersion = match.groupValues[1]
                    }
                } catch (e: Exception) {
                    System.err.println("[ProjectEnvExtractor] Error reading ${fileToRead.name}: ${e.message}")
                }
            } else {
                System.err.println("[ProjectEnvExtractor] No pom.xml or build.gradle found at $basePath")
                // Search recursively in subdirectories as fallback
                searchBuildFilesRecursively(File(basePath), { file ->
                    if (!buildFileContent.isNotEmpty()) {
                        try {
                            buildFileName = file.name
                            buildFileContent = file.readText()
                            System.err.println("[ProjectEnvExtractor] Found build file at: ${file.absolutePath}")
                        } catch (e: Exception) {
                            System.err.println("[ProjectEnvExtractor] Error reading ${file.name}: ${e.message}")
                        }
                    }
                })
            }
        }

        return mapOf(
                "jdk_version" to jdkVersion,
                "spring_boot_version" to springBootVersion,
                "build_file_name" to buildFileName,
                "build_file_content" to buildFileContent
        )
    }

    private fun searchBuildFilesRecursively(dir: File, onFound: (File) -> Unit, depth: Int = 0) {
        if (depth > 3) return // Limit search depth
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name == "pom.xml" || file.name == "build.gradle" || file.name == "build.gradle.kts")) {
                    onFound(file)
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    searchBuildFilesRecursively(file, onFound, depth + 1)
                }
            }
        } catch (e: Exception) {
            System.err.println("[ProjectEnvExtractor] Error searching directory ${dir.path}: ${e.message}")
        }
    }
}