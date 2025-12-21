package org.springforge.codegeneration.analysis

import com.intellij.openapi.project.Project
import java.io.File

class ProjectAnalyzer(private val project: Project) {

    fun analyze(): ProjectAnalysisResult {
        val projectPath = project.basePath ?: return ProjectAnalysisResult.empty()
        val srcMainJava = File(projectPath, "src/main/java")
        if (!srcMainJava.exists()) return ProjectAnalysisResult.empty()

        // 1. Find Spring Boot application class
        val applicationFile = srcMainJava.walkTopDown()
            .firstOrNull { file ->
                file.isFile &&
                        file.name.endsWith(".java") &&
                        file.readText().contains("@SpringBootApplication")
            } ?: return ProjectAnalysisResult.empty()

        // 2. Base package directory = parent of Application class
        val basePackageDir = applicationFile.parentFile

        val basePackage = basePackageDir
            .relativeTo(srcMainJava)
            .path
            .replace(File.separator, ".")

        // 3. Detect layers from subdirectories
        val layers = basePackageDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.lowercase() }
            ?: emptyList()

        // 4. Detect naming conventions
        val namingConventions = mutableMapOf<String, String>()

        basePackageDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".java") }
            .forEach { file ->
                when {
                    file.name.endsWith("Controller.java") ->
                        namingConventions["controller"] = "Controller suffix"

                    file.name.endsWith("ServiceImpl.java") ->
                        namingConventions["service"] = "Impl suffix"

                    file.name.endsWith("DaoImpl.java") ->
                        namingConventions["dao"] = "Impl suffix"
                }
            }

        return ProjectAnalysisResult(
            detectedArchitecture = "layered",
            basePackage = basePackage,
            layers = layers,
            namingConventions = namingConventions
        )
    }
}
