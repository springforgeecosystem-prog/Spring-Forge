package org.springforge.qualityassurance.analysis

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.springforge.qualityassurance.model.FeatureModel

object PsiFeatureExtractor {

    fun extract(project: Project, architecture: String): FeatureModel {

        val fm = FeatureModel(architecture_pattern = architecture)

        var loc = 0
        var methods = 0
        var classes = 0
        var imports = 0
        var annotations = 0

        // ✅ FIX: Correct way to read ALL .java files in the project
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))

        javaFiles.forEach { vf ->
            val psi = PsiManager.getInstance(project).findFile(vf) as? PsiJavaFile ?: return@forEach

            imports += psi.importList?.importStatements?.size ?: 0

            psi.accept(object : JavaRecursiveElementVisitor() {

                override fun visitClass(aClass: PsiClass) {
                    classes++
                    annotations += aClass.annotations.size
                    super.visitClass(aClass)
                }

                override fun visitMethod(method: PsiMethod) {
                    methods++
                    annotations += method.annotations.size
                    loc += method.text.lines().size
                    super.visitMethod(method)
                }
            })
        }

        fm.loc = loc
        fm.methods = methods
        fm.classes = classes
        fm.imports = imports
        fm.annotations = annotations

        return fm
    }
}
