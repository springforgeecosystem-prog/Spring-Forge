package org.springforge.codegeneration.analysis

import com.intellij.openapi.project.Project
import java.io.File

class ProjectAnalyzer(private val project: Project) {

    fun analyze(): ProjectAnalysisResult {
        val projectPath = project.basePath ?: return ProjectAnalysisResult.empty()
        val srcMainJava = File(projectPath, "src/main/java")
        if (!srcMainJava.exists()) return ProjectAnalysisResult.empty()

        val applicationFile = srcMainJava.walkTopDown()
            .firstOrNull {
                it.isFile &&
                        it.name.endsWith(".java") &&
                        it.readText().contains("@SpringBootApplication")
            } ?: return ProjectAnalysisResult.empty()

        val basePackageDir = applicationFile.parentFile
        val basePackage = basePackageDir
            .relativeTo(srcMainJava)
            .path.replace(File.separator, ".")

        val layers = basePackageDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name.lowercase() }
            ?: emptyList()

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

        // ⚠️ DO NOT decide architecture here
        return ProjectAnalysisResult(
            detectedArchitecture = "unknown",
            confidence = 0.0,
            basePackage = basePackage,
            layers = layers,
            namingConventions = namingConventions
        )
    }
}
