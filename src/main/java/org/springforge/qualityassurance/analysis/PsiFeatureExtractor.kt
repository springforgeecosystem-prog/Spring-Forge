// PsiFeatureExtractor.kt - ENHANCED VERSION
package org.springforge.qualityassurance.analysis

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.springforge.qualityassurance.model.FileFeatureModel

object PsiFeatureExtractor {

    /**
     * Extract features for ALL Java files in the project
     */
    fun extractAllFiles(project: Project, architecture: String): List<FileFeatureModel> {
        val javaFiles = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )

        return javaFiles.mapNotNull { vf ->
            extractFileFeatures(project, vf, architecture)
        }
    }

    /**
     * Extract features for a single file
     */
    private fun extractFileFeatures(
        project: Project,
        virtualFile: VirtualFile,
        architecture: String
    ): FileFeatureModel? {

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile
            ?: return null

        val fm = FileFeatureModel(
            architecture_pattern = architecture.lowercase(),
            file_name = virtualFile.name,
            file_path = virtualFile.path
        )

        var loc = 0
        var methods = 0
        var classes = 0
        var annotations = 0

        // Dependency tracking
        var controllerDeps = 0
        var serviceDeps = 0
        var repositoryDeps = 0
        var entityDeps = 0

        // Characteristics
        var hasBusinessLogic = false
        var hasDataAccess = false
        var hasHttpHandling = false
        var hasValidation = false
        var hasTransaction = false

        fm.imports = psiFile.importList?.importStatements?.size ?: 0

        psiFile.accept(object : JavaRecursiveElementVisitor() {

            override fun visitClass(aClass: PsiClass) {
                classes++

                // Check class annotations
                aClass.annotations.forEach { annotation ->
                    annotations++
                    when (annotation.qualifiedName) {
                        "org.springframework.web.bind.annotation.RestController",
                        "org.springframework.stereotype.Controller" -> hasHttpHandling = true

                        "org.springframework.stereotype.Service" -> hasBusinessLogic = true

                        "org.springframework.stereotype.Repository" -> hasDataAccess = true

                        "org.springframework.transaction.annotation.Transactional" -> hasTransaction = true
                    }
                }

                super.visitClass(aClass)
            }

            override fun visitMethod(method: PsiMethod) {
                methods++
                loc += method.text.lines().size

                // Check method annotations
                method.annotations.forEach { annotation ->
                    annotations++
                    when (annotation.qualifiedName) {
                        "org.springframework.web.bind.annotation.GetMapping",
                        "org.springframework.web.bind.annotation.PostMapping",
                        "org.springframework.web.bind.annotation.PutMapping",
                        "org.springframework.web.bind.annotation.DeleteMapping",
                        "org.springframework.web.bind.annotation.RequestMapping" -> hasHttpHandling = true

                        "javax.validation.Valid",
                        "org.springframework.validation.annotation.Validated" -> hasValidation = true

                        "org.springframework.transaction.annotation.Transactional" -> hasTransaction = true
                    }
                }

                // Check method body for business logic indicators
                val body = method.body?.text ?: ""
                if (body.contains("if (") || body.contains("for (") ||
                    body.contains("while (") || body.contains("switch (")) {
                    hasBusinessLogic = true
                }

                // Check for data access patterns
                if (body.contains(".save(") || body.contains(".find") ||
                    body.contains(".delete(") || body.contains(".query")) {
                    hasDataAccess = true
                }

                super.visitMethod(method)
            }

            override fun visitField(field: PsiField) {
                // Count dependencies by field type
                val fieldType = field.type.presentableText.lowercase()

                when {
                    "controller" in fieldType -> controllerDeps++
                    "service" in fieldType -> serviceDeps++
                    "repository" in fieldType || "dao" in fieldType -> repositoryDeps++
                    "entity" in fieldType || "model" in fieldType -> entityDeps++
                }

                super.visitField(field)
            }
        })

        // Set extracted values
        fm.loc = loc
        fm.methods = methods
        fm.classes = classes
        fm.annotations = annotations
        fm.controller_deps = controllerDeps
        fm.service_deps = serviceDeps
        fm.repository_deps = repositoryDeps
        fm.entity_deps = entityDeps
        fm.total_cross_layer_deps = controllerDeps + serviceDeps + repositoryDeps + entityDeps
        fm.has_business_logic = hasBusinessLogic
        fm.has_data_access = hasDataAccess
        fm.has_http_handling = hasHttpHandling
        fm.has_validation = hasValidation
        fm.has_transaction = hasTransaction

        // Detect layer violations
        fm.violates_layer_separation = detectLayerViolation(fm)

        // Detect layer
        fm.layer = detectLayer(fm)

        return fm
    }

    private fun detectLayerViolation(fm: FileFeatureModel): Boolean {
        // Controller accessing Repository directly (layer skip)
        if (fm.has_http_handling && fm.repository_deps > 0) {
            return true
        }

        // Service depending on Controller (reversed dependency)
        if (fm.has_business_logic && !fm.has_http_handling && fm.controller_deps > 0) {
            return true
        }

        return false
    }

    private fun detectLayer(fm: FileFeatureModel): String {
        return when {
            fm.has_http_handling -> "Controller"
            fm.has_data_access && fm.repository_deps == 0 -> "Repository"
            fm.has_business_logic -> "Service"
            fm.classes > 0 && fm.methods == 0 -> "Entity"
            else -> "Unknown"
        }
    }
}