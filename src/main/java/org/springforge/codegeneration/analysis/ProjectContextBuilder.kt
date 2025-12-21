package org.springforge.codegeneration.analysis

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * High-level analyzer that uses:
 *  - ProjectAnalyzer (low-level feature extractor)
 *  - Naming convention inference
 *  - Layer detection
 *  - Architecture prediction
 *  - Base package detection
 *
 * Produces ProjectAnalysisResult for PromptBuilder.
 */
object ProjectContextBuilder {

    fun build(project: Project): ProjectAnalysisResult {

        val featureAnalyzer = ProjectAnalyzer(project)
        val features = featureAnalyzer.analyze() // your existing ArchitectureFeatures

        val basePackage = detectBasePackage(project)
        val layers = detectLayers(project, basePackage)
        val naming = detectNamingConventions(project)

        val architecture = inferArchitecture(features, layers)

        return ProjectAnalysisResult(
            detectedArchitecture = architecture,
            basePackage = basePackage,
            layers = layers,
            namingConventions = naming
        )
    }

    private fun inferArchitecture(features: ArchitectureFeatures, layers: List<String>): String {
        return when {
            features.controllers > 0 && features.services > 0 && features.repositories > 0 ->
                "layered"

            layers.contains("usecase") || layers.contains("adapter") ->
                "clean"

            features.controllers > 0 && features.repositories == 0 ->
                "mvc"

            else -> "layered"
        }
    }

    private fun detectBasePackage(project: Project): String {
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        val pkgs = javaFiles
            .mapNotNull { (psiManager.findFile(it) as? PsiJavaFile)?.packageName }
            .filter { it.isNotBlank() }

        if (pkgs.isEmpty()) return "com.example"

        return pkgs.minBy { it.length }
    }

    private fun detectLayers(project: Project, basePackage: String): List<String> {
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        val layers = mutableSetOf<String>()

        javaFiles.forEach { vFile ->
            val psi = psiManager.findFile(vFile)
            if (psi is PsiJavaFile) {
                val pkg = psi.packageName
                when {
                    pkg.contains(".controller") -> layers.add("controller")
                    pkg.contains(".service") -> layers.add("service")
                    pkg.contains(".repository") -> layers.add("repository")
                    pkg.contains(".entity") -> layers.add("entity")
                    pkg.contains(".domain") -> layers.add("domain")
                    pkg.contains(".usecase") -> layers.add("usecase")
                    pkg.contains(".adapter") -> layers.add("adapter")
                }
            }
        }

        return layers.toList()
    }

    private fun detectNamingConventions(project: Project): Map<String, String> {
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        val result = mutableMapOf<String, String>()

        javaFiles.forEach { vf ->
            val psi = psiManager.findFile(vf)
            if (psi is PsiJavaFile) {
                psi.classes.forEach { cls ->
                    val name = cls.name ?: return@forEach

                    when {
                        name.endsWith("Controller") -> result["controller"] = "Controller suffix"
                        name.endsWith("Service") -> result["service"] = "Service suffix"
                        name.endsWith("Repository") -> result["repository"] = "Repository suffix"
                        name.endsWith("Impl") -> result["impl"] = "Impl suffix"
                    }
                }
            }
        }

        return result
    }
}
