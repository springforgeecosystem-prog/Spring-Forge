package org.springforge.runtimeanalysis.collector

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import java.io.File

object ProjectEnvExtractor {

    fun extractEnvironment(project: Project): Map<String, String> {
        // 1. Grab the JDK Version directly from IntelliJ's Project Settings
        val jdkVersion = ProjectRootManager.getInstance(project).projectSdk?.versionString ?: "Unknown JDK"

        // 2. Scan for Spring Boot Version
        var springBootVersion = "Unknown Spring Boot"
        val basePath = project.basePath

        if (basePath != null) {
            val pomFile = File(basePath, "pom.xml")
            val gradleFile = File(basePath, "build.gradle")
            val gradleKtsFile = File(basePath, "build.gradle.kts")

            if (pomFile.exists()) {
                val content = pomFile.readText()
                // Matches <artifactId>spring-boot-starter-parent</artifactId> then gets the <version>
                val regex = Regex("""<artifactId>spring-boot-starter-parent</artifactId>\s*<version>(.*?)</version>""")
                val match = regex.find(content)
                if (match != null) {
                    springBootVersion = match.groupValues[1]
                }
            } else if (gradleFile.exists() || gradleKtsFile.exists()) {
                val fileToRead = if (gradleFile.exists()) gradleFile else gradleKtsFile
                val content = fileToRead.readText()
                // Matches id 'org.springframework.boot' version '3.2.0'
                val regex = Regex("""id\s*['"]org\.springframework\.boot['"]\s*version\s*['"](.*?)['"]""")
                val match = regex.find(content)
                if (match != null) {
                    springBootVersion = match.groupValues[1]
                }
            }
        }

        return mapOf(
            "jdk_version" to jdkVersion,
            "spring_boot_version" to springBootVersion
        )
    }
}