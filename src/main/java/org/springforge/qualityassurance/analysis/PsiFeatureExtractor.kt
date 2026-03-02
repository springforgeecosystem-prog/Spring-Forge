package org.springforge.qualityassurance.analysis

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.springforge.qualityassurance.model.FileFeatureModel

object PsiFeatureExtractor {

    // ── Spring / JPA annotation constants ────────────────────────────────────

    private val CONTROLLER_ANNOTATIONS = setOf(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping"
    )

    private val SERVICE_ANNOTATIONS = setOf(
        "org.springframework.stereotype.Service"
    )

    private val REPOSITORY_ANNOTATIONS = setOf(
        "org.springframework.stereotype.Repository",
        "org.springframework.data.repository.Repository",
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.data.repository.CrudRepository",
        "org.springframework.data.repository.PagingAndSortingRepository"
    )

    private val ENTITY_ANNOTATIONS = setOf(
        "jakarta.persistence.Entity",
        "javax.persistence.Entity",
        "jakarta.persistence.MappedSuperclass",
        "javax.persistence.MappedSuperclass",
        "jakarta.persistence.Embeddable",
        "javax.persistence.Embeddable",
        "jakarta.persistence.Table",
        "javax.persistence.Table"
    )

    private val ENTITY_FIELD_ANNOTATIONS = setOf(
        "jakarta.persistence.Id",
        "javax.persistence.Id",
        "jakarta.persistence.Column",
        "javax.persistence.Column",
        "jakarta.persistence.GeneratedValue",
        "javax.persistence.GeneratedValue",
        "jakarta.persistence.OneToMany",
        "javax.persistence.OneToMany",
        "jakarta.persistence.ManyToOne",
        "javax.persistence.ManyToOne",
        "jakarta.persistence.OneToOne",
        "javax.persistence.OneToOne",
        "jakarta.persistence.ManyToMany",
        "javax.persistence.ManyToMany",
        "jakarta.persistence.JoinColumn",
        "javax.persistence.JoinColumn"
    )

    private val CONFIG_ANNOTATIONS = setOf(
        "org.springframework.context.annotation.Configuration",
        "org.springframework.boot.autoconfigure.SpringBootApplication",
        "org.springframework.context.annotation.Bean",
        "org.springframework.boot.context.properties.ConfigurationProperties",
        "org.springframework.context.annotation.ComponentScan"
    )

    private val COMPONENT_ANNOTATIONS = setOf(
        "org.springframework.stereotype.Component"
    )

    private val TRANSACTION_ANNOTATIONS = setOf(
        "org.springframework.transaction.annotation.Transactional",
        "jakarta.transaction.Transactional",
        "javax.transaction.Transactional"
    )

    private val VALIDATION_ANNOTATIONS = setOf(
        "jakarta.validation.Valid",
        "javax.validation.Valid",
        "org.springframework.validation.annotation.Validated"
    )

    private val PORT_MARKERS    = setOf("port", "ports", "usecase", "usecases")
    private val ADAPTER_MARKERS = setOf("adapter", "adapters", "infrastructure")

    // ── Public API ────────────────────────────────────────────────────────────

    fun extractAllFiles(project: Project, architecture: String): List<FileFeatureModel> {
        val javaFiles = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )
        return javaFiles.mapNotNull { vf ->
            extractFileFeatures(project, vf, architecture)
        }
    }

    // ── Per-file extraction ───────────────────────────────────────────────────

    private fun extractFileFeatures(
        project: Project,
        virtualFile: VirtualFile,
        architecture: String
    ): FileFeatureModel? {

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile
            ?: return null

        // ── All mutable state lives here as local vars ────────────────────────
        var loc         = 0
        var methods     = 0
        var classes     = 0
        var annotations = 0
        var imports     = psiFile.importList?.importStatements?.size ?: 0

        var controllerDeps  = 0
        var serviceDeps     = 0
        var repositoryDeps  = 0
        var entityDeps      = 0
        var adapterDeps     = 0
        var portDeps        = 0

        var isController  = false
        var isService     = false
        var isRepository  = false
        var isEntity      = false
        var isConfig      = false
        var isComponent   = false

        var hasBusinessLogic  = false
        var hasDataAccess     = false
        var hasHttpHandling   = false
        var hasValidation     = false
        var hasTransaction    = false

        // ── Step 1: scan imports for early layer signals ──────────────────────
        val importStatements = psiFile.importList?.importStatements ?: emptyArray()
        for (imp in importStatements) {
            val fqn = imp.qualifiedName ?: continue
            when {
                CONTROLLER_ANNOTATIONS.contains(fqn)  -> { isController = true; hasHttpHandling = true }
                SERVICE_ANNOTATIONS.contains(fqn)     -> { isService = true }
                REPOSITORY_ANNOTATIONS.contains(fqn)  -> isRepository = true
                ENTITY_ANNOTATIONS.contains(fqn)      -> isEntity = true
                ENTITY_FIELD_ANNOTATIONS.contains(fqn)-> isEntity = true
                CONFIG_ANNOTATIONS.contains(fqn)      -> isConfig = true
                TRANSACTION_ANNOTATIONS.contains(fqn) -> hasTransaction = true
                VALIDATION_ANNOTATIONS.contains(fqn)  -> hasValidation = true
                fqn.startsWith("org.springframework.data.") -> isRepository = true
            }
        }

        // ── Step 2: walk PSI tree ─────────────────────────────────────────────
        psiFile.accept(object : JavaRecursiveElementVisitor() {

            override fun visitClass(aClass: PsiClass) {
                classes++

                // Check supertype interfaces for Spring Data repos
                for (superType in aClass.superTypes) {
                    val typeName = superType.canonicalText
                    if (typeName.contains("JpaRepository")
                        || typeName.contains("CrudRepository")
                        || typeName.contains("PagingAndSortingRepository")) {
                        isRepository = true
                    }
                }

                // Check class-level annotations
                for (annotation in aClass.annotations) {
                    annotations++
                    val fqn = annotation.qualifiedName ?: continue

                    if (CONTROLLER_ANNOTATIONS.contains(fqn)) {
                        isController    = true
                        hasHttpHandling = true
                    } else if (SERVICE_ANNOTATIONS.contains(fqn)) {
                        isService        = true
                        hasBusinessLogic = true
                    } else if (REPOSITORY_ANNOTATIONS.contains(fqn)) {
                        isRepository = true
                    } else if (ENTITY_ANNOTATIONS.contains(fqn)) {
                        isEntity = true
                    } else if (CONFIG_ANNOTATIONS.contains(fqn)) {
                        isConfig = true
                    } else if (COMPONENT_ANNOTATIONS.contains(fqn)) {
                        isComponent = true
                    } else if (TRANSACTION_ANNOTATIONS.contains(fqn)) {
                        hasTransaction = true
                    }
                }

                super.visitClass(aClass)
            }

            override fun visitMethod(method: PsiMethod) {
                methods++
                loc += method.text.lines().size

                // Check method-level annotations
                for (annotation in method.annotations) {
                    annotations++
                    val fqn = annotation.qualifiedName ?: continue

                    if (CONTROLLER_ANNOTATIONS.contains(fqn)) {
                        isController    = true
                        hasHttpHandling = true
                    } else if (TRANSACTION_ANNOTATIONS.contains(fqn)) {
                        hasTransaction = true
                    } else if (VALIDATION_ANNOTATIONS.contains(fqn)) {
                        hasValidation = true
                    } else if (CONFIG_ANNOTATIONS.contains(fqn)) {
                        isConfig = true
                    }
                }

                // Check method parameters for @Valid
                for (param in method.parameterList.parameters) {
                    for (annotation in param.annotations) {
                        val fqn = annotation.qualifiedName ?: continue
                        if (VALIDATION_ANNOTATIONS.contains(fqn)) {
                            hasValidation = true
                        }
                    }
                }

                // Scan method body for patterns
                val body = method.body?.text ?: ""

                if (body.contains("if (") || body.contains("if(")
                    || body.contains("for (") || body.contains("for(")
                    || body.contains("while (") || body.contains("while(")
                    || body.contains("switch (") || body.contains("switch(")) {
                    hasBusinessLogic = true
                }

                if (body.contains(".save(") || body.contains(".saveAll(")
                    || body.contains(".findById(") || body.contains(".findAll(")
                    || body.contains(".deleteById(") || body.contains(".delete(")
                    || body.contains(".existsById(") || body.contains(".count(")
                    || body.contains("entityManager") || body.contains(".flush(")) {
                    hasDataAccess = true
                }

                super.visitMethod(method)
            }

            override fun visitField(field: PsiField) {
                // Check field annotations for JPA entity signals
                for (annotation in field.annotations) {
                    val fqn = annotation.qualifiedName ?: continue
                    if (ENTITY_FIELD_ANNOTATIONS.contains(fqn)) {
                        isEntity     = true
                        annotations++
                    }
                }

                // Count injected cross-layer dependencies by field type name
                val fieldType = field.type.presentableText.lowercase()
                if (fieldType.contains("controller")) {
                    controllerDeps++
                } else if (fieldType.contains("service")) {
                    serviceDeps++
                } else if (fieldType.contains("repository") || fieldType.contains("dao")) {
                    repositoryDeps++
                } else if (fieldType.contains("entity") || fieldType.contains("model")) {
                    entityDeps++
                } else if (fieldType.contains("port")) {
                    portDeps++
                } else if (fieldType.contains("adapter")) {
                    adapterDeps++
                }

                super.visitField(field)
            }
        })

        // ── Step 3: detect violations and layer ───────────────────────────────
        val violatesLayer = detectLayerViolation(
            isController   = isController,
            isService      = isService,
            repositoryDeps = repositoryDeps,
            controllerDeps = controllerDeps,
            hasHttpHandling = hasHttpHandling
        )

        val packageName = psiFile.packageName
        val fileName    = virtualFile.name

        val layer = detectLayer(
            isController     = isController,
            isService        = isService,
            isRepository     = isRepository,
            isEntity         = isEntity,
            isConfig         = isConfig,
            isComponent      = isComponent,
            hasHttpHandling  = hasHttpHandling,
            hasDataAccess    = hasDataAccess,
            hasBusinessLogic = hasBusinessLogic,
            repositoryDeps   = repositoryDeps,
            packageName      = packageName,
            fileName         = fileName
        )

        // ── Step 4: build FileFeatureModel in ONE shot — no mutation ──────────
        return FileFeatureModel(
            architecture_pattern     = architecture.lowercase(),
            architecture_confidence  = 0.95,
            file_name                = virtualFile.name,
            file_path                = virtualFile.path,
            layer                    = layer,
            loc                      = loc,
            methods                  = methods,
            classes                  = classes,
            avg_cc                   = 1.5,
            imports                  = imports,
            annotations              = annotations,
            controller_deps          = controllerDeps,
            service_deps             = serviceDeps,
            repository_deps          = repositoryDeps,
            entity_deps              = entityDeps,
            adapter_deps             = adapterDeps,
            port_deps                = portDeps,
            usecase_deps             = 0,
            gateway_deps             = 0,
            total_cross_layer_deps   = controllerDeps + serviceDeps + repositoryDeps
                                       + entityDeps + adapterDeps + portDeps,
            has_business_logic       = hasBusinessLogic,
            has_data_access          = hasDataAccess,
            has_http_handling        = hasHttpHandling,
            has_validation           = hasValidation,
            has_transaction          = hasTransaction,
            violates_layer_separation = violatesLayer
        )
    }

    // ── Layer violation ───────────────────────────────────────────────────────

    private fun detectLayerViolation(
        isController   : Boolean,
        isService      : Boolean,
        repositoryDeps : Int,
        controllerDeps : Int,
        hasHttpHandling: Boolean
    ): Boolean {
        // Controller skipping service and accessing repository directly
        if ((isController || hasHttpHandling) && repositoryDeps > 0) return true
        // Service depending on controller (reversed dependency)
        if (isService && controllerDeps > 0) return true
        return false
    }

    // ── Layer detection — priority order matters ──────────────────────────────

    private fun detectLayer(
        isController    : Boolean,
        isService       : Boolean,
        isRepository    : Boolean,
        isEntity        : Boolean,
        isConfig        : Boolean,
        isComponent     : Boolean,
        hasHttpHandling : Boolean,
        hasDataAccess   : Boolean,
        hasBusinessLogic: Boolean,
        repositoryDeps  : Int,
        packageName     : String,
        fileName        : String
    ): String {

        val pkg  = packageName.lowercase()
        val file = fileName.lowercase().removeSuffix(".java")

        // 1. Annotation-based (most reliable)
        if (isController || hasHttpHandling) return "controller"
        if (isEntity)                        return "entity"
        if (isRepository)                    return "repository"
        if (isService)                       return "service"
        if (isConfig)                        return "config"
        if (isComponent)                     return "component"

        // 2. Package-name based (hexagonal / clean arch)
        if (PORT_MARKERS.any    { m -> pkg.contains(m) }) return "port"
        if (ADAPTER_MARKERS.any { m -> pkg.contains(m) }) return "adapter"

        // 3. File-name heuristics
        if (file.endsWith("controller"))                        return "controller"
        if (file.endsWith("service")
            || file.endsWith("serviceimpl"))                    return "service"
        if (file.endsWith("repository")
            || file.endsWith("dao")
            || file.endsWith("daoimpl"))                        return "repository"
        if (file.endsWith("entity")
            || file.endsWith("model")
            || file.endsWith("domain"))                         return "entity"
        if (file.endsWith("config")
            || file.endsWith("configuration")
            || file.endsWith("application"))                    return "config"
        if (file.endsWith("dto")
            || file.endsWith("request")
            || file.endsWith("response"))                       return "dto"
        if (file.endsWith("mapper")
            || file.endsWith("converter"))                      return "mapper"
        if (file.endsWith("util")
            || file.endsWith("utils")
            || file.endsWith("helper"))                         return "util"
        if (file.endsWith("exception")
            || file.endsWith("handler"))                        return "exception"

        // 4. Behavioural fallback
        if (hasDataAccess && repositoryDeps > 0) return "service"
        if (hasDataAccess)                       return "repository"
        if (hasBusinessLogic)                    return "service"

        return "unknown"
    }
}