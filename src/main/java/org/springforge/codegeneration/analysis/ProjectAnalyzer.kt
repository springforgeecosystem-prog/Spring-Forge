package org.springforge.codegeneration.analysis

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

data class ArchitectureFeatures(
    val numJavaFiles: Int,
    val avgPackageDepth: Double,
    val controllers: Int,
    val services: Int,
    val repositories: Int
)

class ProjectAnalyzer(private val project: Project) {

    fun analyze(): ArchitectureFeatures {
        val psiManager = PsiManager.getInstance(project)
        val javaFiles = mutableListOf<PsiJavaFile>()

        FileTypeIndex.processFiles(JavaFileType.INSTANCE, { vf ->
            val psi = psiManager.findFile(vf)
            if (psi is PsiJavaFile) javaFiles.add(psi)
            true
        }, GlobalSearchScope.projectScope(project))

        var controllers = 0
        var services = 0
        var repos = 0
        val packageDepths = mutableListOf<Int>()

        for (jf in javaFiles) {
            val pkg = jf.packageName
            packageDepths.add(if (pkg.isBlank()) 0 else pkg.split(".").size)
            for (cls in jf.classes) {
                for (annot in cls.annotations) {
                    val name = annot.qualifiedName ?: annot.text
                    when {
                        name.endsWith("RestController") -> controllers++
                        name.endsWith("Service") -> services++
                        name.endsWith("Repository") -> repos++
                    }
                }
            }
        }

        val avgDepth = if (packageDepths.isEmpty()) 0.0 else packageDepths.average()
        return ArchitectureFeatures(javaFiles.size, avgDepth, controllers, services, repos)
    }
}
