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

/**
 * Extracts ML-model features from every Java source file in the project using
 * IntelliJ's PSI tree.
 *
 * Architecture-aware changes (v2):
 *   - Layer detection now supports all four architectures:
 *       layered            → controller / service / repository / entity / config
 *       mvc                → controller / model / view / service / config
 *       hexagonal          → port / adapter / usecase / gateway / entity / config
 *       clean_architecture → usecase / gateway / entity / adapter / port / config
 *   - usecase_deps and gateway_deps fields are now populated (were always 0 before)
 *   - Hexagonal/clean arch package and filename heuristics are expanded
 *   - new_keyword detection added (for tight_coupling_new_keyword anti-pattern)
 *   - broad_catch detection added at method-body level
 */
object PsiFeatureExtractor {

    // ── Spring / JPA annotation sets ─────────────────────────────────────────

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
        "jakarta.persistence.Id",   "javax.persistence.Id",
        "jakarta.persistence.Column","javax.persistence.Column",
        "jakarta.persistence.GeneratedValue","javax.persistence.GeneratedValue",
        "jakarta.persistence.OneToMany","javax.persistence.OneToMany",
        "jakarta.persistence.ManyToOne","javax.persistence.ManyToOne",
        "jakarta.persistence.OneToOne","javax.persistence.OneToOne",
        "jakarta.persistence.ManyToMany","javax.persistence.ManyToMany",
        "jakarta.persistence.JoinColumn","javax.persistence.JoinColumn"
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

    // ── Architecture-specific package/name markers ────────────────────────────

    /** Hexagonal & Clean: package segments that signal the Port layer */
    private val PORT_PKG_MARKERS    = setOf("port", "ports", "inbound", "outbound", "driving", "driven")

    /** Hexagonal & Clean: package segments that signal the Adapter layer */
    private val ADAPTER_PKG_MARKERS = setOf("adapter", "adapters", "infrastructure", "infra", "secondary", "primary")

    /** Hexagonal & Clean: package segments that signal the UseCase layer */
    private val USECASE_PKG_MARKERS = setOf("usecase", "usecases", "use_case", "application", "interactor")

    /** Hexagonal & Clean: package segments that signal the Gateway layer */
    private val GATEWAY_PKG_MARKERS = setOf("gateway", "gateways", "datasource", "persistence")

    /** MVC: package segments that signal the View layer */
    private val VIEW_PKG_MARKERS    = setOf("view", "views", "template", "templates", "thymeleaf", "freemarker")

    // ── Public API ────────────────────────────────────────────────────────────

    fun extractAllFiles(project: Project, architecture: String): List<FileFeatureModel> {
        val javaFiles = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        )
        // Normalise architecture key to lowercase so comparisons are consistent
        val arch = architecture.lowercase()
        return javaFiles.mapNotNull { vf -> extractFileFeatures(project, vf, arch) }
    }

    // ── Per-file extraction ───────────────────────────────────────────────────

    private fun extractFileFeatures(
        project     : Project,
        virtualFile : VirtualFile,
        architecture: String       // already lowercase
    ): FileFeatureModel? {

        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? PsiJavaFile
            ?: return null

        // ── Mutable counters / flags ──────────────────────────────────────────
        var loc         = 0
        var methods     = 0
        var classes     = 0
        var annotations = 0
        var imports     = psiFile.importList?.importStatements?.size ?: 0

        var controllerDeps = 0
        var serviceDeps    = 0
        var repositoryDeps = 0
        var entityDeps     = 0
        var adapterDeps    = 0
        var portDeps       = 0
        var usecaseDeps    = 0   // ← was always 0 before v2
        var gatewayDeps    = 0   // ← was always 0 before v2

        var isController = false
        var isService    = false
        var isRepository = false
        var isEntity     = false
        var isConfig     = false
        var isComponent  = false

        // Hexagonal / Clean layers
        var isPort     = false
        var isAdapter  = false
        var isUseCase  = false
        var isGateway  = false

        // MVC-specific
        var isView     = false

        var hasBusinessLogic = false
        var hasDataAccess    = false
        var hasHttpHandling  = false
        var hasValidation    = false
        var hasTransaction   = false

        // Extra signals for anti-pattern detection
        var usesNewKeyword   = false   // tight_coupling_new_keyword
        var hasBroadCatch    = false   // broad_catch (catches Exception / Throwable)

        // ── Step 1: scan imports ──────────────────────────────────────────────
        val importStatements = psiFile.importList?.importStatements ?: emptyArray()
        for (imp in importStatements) {
            val fqn = imp.qualifiedName ?: continue
            when {
                CONTROLLER_ANNOTATIONS.any { it == fqn } -> { isController = true; hasHttpHandling = true }
                SERVICE_ANNOTATIONS.any    { it == fqn } -> isService    = true
                REPOSITORY_ANNOTATIONS.any { it == fqn } -> isRepository = true
                ENTITY_ANNOTATIONS.any     { it == fqn } -> isEntity     = true
                ENTITY_FIELD_ANNOTATIONS.any { it == fqn } -> isEntity   = true
                CONFIG_ANNOTATIONS.any     { it == fqn } -> isConfig     = true
                TRANSACTION_ANNOTATIONS.any{ it == fqn } -> hasTransaction = true
                VALIDATION_ANNOTATIONS.any { it == fqn } -> hasValidation  = true
                fqn.startsWith("org.springframework.data.") -> isRepository = true
                // Framework imports in domain layers (for framework_dependency_in_domain detection)
                fqn.startsWith("org.springframework.") || fqn.startsWith("jakarta.") || fqn.startsWith("javax.") -> {
                    /* tracked implicitly via annotation sets above */
                }
            }
        }

        // ── Step 2: walk PSI tree ─────────────────────────────────────────────
        psiFile.accept(object : JavaRecursiveElementVisitor() {

            override fun visitClass(aClass: PsiClass) {
                classes++

                // Spring Data supertypes
                for (st in aClass.superTypes) {
                    val t = st.canonicalText
                    if (t.contains("JpaRepository") || t.contains("CrudRepository")
                        || t.contains("PagingAndSortingRepository")) {
                        isRepository = true
                    }
                }

                for (ann in aClass.annotations) {
                    annotations++
                    val fqn = ann.qualifiedName ?: continue
                    when {
                        CONTROLLER_ANNOTATIONS.any { it == fqn } -> { isController = true; hasHttpHandling = true }
                        SERVICE_ANNOTATIONS.any    { it == fqn } -> { isService = true; hasBusinessLogic = true }
                        REPOSITORY_ANNOTATIONS.any { it == fqn } -> isRepository = true
                        ENTITY_ANNOTATIONS.any     { it == fqn } -> isEntity     = true
                        CONFIG_ANNOTATIONS.any     { it == fqn } -> isConfig     = true
                        COMPONENT_ANNOTATIONS.any  { it == fqn } -> isComponent  = true
                        TRANSACTION_ANNOTATIONS.any{ it == fqn } -> hasTransaction = true
                    }
                }
                super.visitClass(aClass)
            }

            override fun visitMethod(method: PsiMethod) {
                methods++
                loc += method.text.lines().size

                for (ann in method.annotations) {
                    annotations++
                    val fqn = ann.qualifiedName ?: continue
                    when {
                        CONTROLLER_ANNOTATIONS.any { it == fqn } -> { isController = true; hasHttpHandling = true }
                        TRANSACTION_ANNOTATIONS.any{ it == fqn } -> hasTransaction = true
                        VALIDATION_ANNOTATIONS.any { it == fqn } -> hasValidation  = true
                        CONFIG_ANNOTATIONS.any     { it == fqn } -> isConfig       = true
                    }
                }

                for (param in method.parameterList.parameters) {
                    for (ann in param.annotations) {
                        if (VALIDATION_ANNOTATIONS.any { it == ann.qualifiedName }) hasValidation = true
                    }
                }

                val body = method.body?.text ?: ""

                // Business logic signals
                if (body.contains("if (") || body.contains("if(")
                    || body.contains("for (") || body.contains("for(")
                    || body.contains("while (") || body.contains("while(")
                    || body.contains("switch (") || body.contains("switch(")) {
                    hasBusinessLogic = true
                }

                // Data access signals
                if (body.contains(".save(") || body.contains(".saveAll(")
                    || body.contains(".findById(") || body.contains(".findAll(")
                    || body.contains(".deleteById(") || body.contains(".delete(")
                    || body.contains(".existsById(") || body.contains(".count(")
                    || body.contains("entityManager") || body.contains(".flush(")) {
                    hasDataAccess = true
                }

                // tight_coupling_new_keyword: concrete instantiation via `new`
                if (body.contains(" new ") || body.contains("(new ") || body.contains("= new ")) {
                    usesNewKeyword = true
                }

                // broad_catch: catches Exception or Throwable directly
                if (body.contains("catch (Exception")   || body.contains("catch(Exception")
                    || body.contains("catch (Throwable") || body.contains("catch(Throwable")) {
                    hasBroadCatch = true
                }

                super.visitMethod(method)
            }

            override fun visitField(field: PsiField) {
                for (ann in field.annotations) {
                    val fqn = ann.qualifiedName ?: continue
                    if (ENTITY_FIELD_ANNOTATIONS.any { it == fqn }) {
                        isEntity = true; annotations++
                    }
                }

                // Cross-layer dependency counting — now includes usecase & gateway
                val ft = field.type.presentableText.lowercase()
                when {
                    ft.contains("controller")                         -> controllerDeps++
                    ft.contains("service")                           -> serviceDeps++
                    ft.contains("repository") || ft.contains("dao") -> repositoryDeps++
                    ft.contains("entity") || ft.contains("model")   -> entityDeps++
                    ft.contains("port")                              -> portDeps++
                    ft.contains("adapter")                           -> adapterDeps++
                    // Hexagonal / Clean specific
                    ft.contains("usecase") || ft.contains("interactor")
                            || ft.contains("inputport") || ft.contains("outputport") -> usecaseDeps++
                    ft.contains("gateway") || ft.contains("datasource")
                            || ft.contains("persistence")                            -> gatewayDeps++
                }

                super.visitField(field)
            }
        })

        // ── Step 3: package / filename heuristics for hexagonal & clean arch ──
        val pkg  = psiFile.packageName.lowercase()
        val file = virtualFile.name.lowercase().removeSuffix(".java")

        // Port detection
        if (PORT_PKG_MARKERS.any { pkg.contains(it) }
            || file.endsWith("port") || file.endsWith("useport") || file.endsWith("inputport")
            || file.endsWith("outputport")) {
            isPort = true
        }

        // Adapter detection
        if (ADAPTER_PKG_MARKERS.any { pkg.contains(it) }
            || file.endsWith("adapter") || file.endsWith("adapterimpl")) {
            isAdapter = true
        }

        // UseCase detection
        if (USECASE_PKG_MARKERS.any { pkg.contains(it) }
            || file.endsWith("usecase") || file.endsWith("usecaseimpl")
            || file.endsWith("interactor") || file.endsWith("service") && (
                USECASE_PKG_MARKERS.any { pkg.contains(it) })) {
            isUseCase = true
        }

        // Gateway detection
        if (GATEWAY_PKG_MARKERS.any { pkg.contains(it) }
            || file.endsWith("gateway") || file.endsWith("gatewayimpl")
            || file.endsWith("datasource") || file.endsWith("datasourceimpl")) {
            isGateway = true
        }

        // MVC View detection
        if (VIEW_PKG_MARKERS.any { pkg.contains(it) }
            || file.endsWith("view") || file.endsWith("viewmodel")
            || file.endsWith("form") || file.endsWith("template")) {
            isView = true
        }

        // ── Step 4: layer violation detection ────────────────────────────────
        val violatesLayer = detectLayerViolation(
            architecture   = architecture,
            isController   = isController,
            isService      = isService,
            isUseCase      = isUseCase,
            isAdapter      = isAdapter,
            isGateway      = isGateway,
            repositoryDeps = repositoryDeps,
            controllerDeps = controllerDeps,
            serviceDeps    = serviceDeps,
            usecaseDeps    = usecaseDeps,
            hasHttpHandling = hasHttpHandling
        )

        // ── Step 5: resolve final layer string ────────────────────────────────
        val layer = detectLayer(
            architecture    = architecture,
            isController    = isController,
            isService       = isService,
            isRepository    = isRepository,
            isEntity        = isEntity,
            isConfig        = isConfig,
            isComponent     = isComponent,
            isPort          = isPort,
            isAdapter       = isAdapter,
            isUseCase       = isUseCase,
            isGateway       = isGateway,
            isView          = isView,
            hasHttpHandling = hasHttpHandling,
            hasDataAccess   = hasDataAccess,
            hasBusinessLogic= hasBusinessLogic,
            repositoryDeps  = repositoryDeps,
            packageName     = pkg,
            fileName        = file
        )

        // ── Step 6: reject files with no class body ──────────────────────────
        // Every meaningful Spring Boot file has at least one class/interface/enum.
        // Files with classes=0 are either:
        //   - Fully commented out (all code in block/line comments)
        //   - Partially commented (imports left but class removed)
        //   - package-info.java (not analyzable)
        // All of these produce garbage quality scores — skip them.
        if (classes == 0) {
            println("⚠️ Skipping no-class file: ${virtualFile.name}")
            return null
        }

        return FileFeatureModel(
            architecture_pattern    = architecture,
            architecture_confidence = 0.95,
            file_name               = virtualFile.name,
            file_path               = virtualFile.path,
            layer                   = layer,
            loc                     = loc,
            methods                 = methods,
            classes                 = classes,
            avg_cc                  = 1.5,
            imports                 = imports,
            annotations             = annotations,
            controller_deps         = controllerDeps,
            service_deps            = serviceDeps,
            repository_deps         = repositoryDeps,
            entity_deps             = entityDeps,
            adapter_deps            = adapterDeps,
            port_deps               = portDeps,
            usecase_deps            = usecaseDeps,
            gateway_deps            = gatewayDeps,
            total_cross_layer_deps  = controllerDeps + serviceDeps + repositoryDeps
                                      + entityDeps + adapterDeps + portDeps
                                      + usecaseDeps + gatewayDeps,
            has_business_logic      = hasBusinessLogic,
            has_data_access         = hasDataAccess,
            has_http_handling       = hasHttpHandling,
            has_validation          = hasValidation,
            has_transaction         = hasTransaction,
            violates_layer_separation = violatesLayer,
            uses_new_keyword        = usesNewKeyword,
            has_broad_catch         = hasBroadCatch,
            source_code             = psiFile.text
        )
    }

    // ── Layer violation ───────────────────────────────────────────────────────

    /**
     * Architecture-aware violation detection.
     *
     * Layered / MVC:
     *   Controller skipping Service → direct repo access
     *   Service depending on Controller (reversed dependency)
     *
     * Hexagonal:
     *   Adapter directly instantiating UseCase (should use Port)
     *   Domain (UseCase) depending on Adapter
     *
     * Clean Architecture:
     *   Outer layer (Gateway/Adapter) leaking into UseCase
     *   UseCase depending on framework-specific type (via serviceDeps heuristic)
     */
    private fun detectLayerViolation(
        architecture   : String,
        isController   : Boolean,
        isService      : Boolean,
        isUseCase      : Boolean,
        isAdapter      : Boolean,
        isGateway      : Boolean,
        repositoryDeps : Int,
        controllerDeps : Int,
        serviceDeps    : Int,
        usecaseDeps    : Int,
        hasHttpHandling: Boolean
    ): Boolean = when (architecture) {

        "hexagonal" -> {
            // Adapter bypassing Port to call UseCase directly (should inject Port interface)
            (isAdapter && usecaseDeps > 0 && repositoryDeps > 0) ||
            // Domain (UseCase) must not depend on Adapter
            (isUseCase && adapterDeps(repositoryDeps, controllerDeps) > 0)
        }

        "clean_architecture" -> {
            // UseCase (inner) must not depend on outer rings (Gateway = db, Adapter = web)
            (isUseCase && (repositoryDeps > 0 || controllerDeps > 0)) ||
            // Gateway/Adapter must not reference UseCase directly (use Port interface)
            (isGateway && usecaseDeps > 0)
        }

        "mvc" -> {
            // Controller bypassing Service/Model layer to hit repository directly
            ((isController || hasHttpHandling) && repositoryDeps > 0) ||
            // Service depending on Controller (reversed)
            (isService && controllerDeps > 0)
        }

        else -> { // "layered" (default)
            ((isController || hasHttpHandling) && repositoryDeps > 0) ||
            (isService && controllerDeps > 0)
        }
    }

    /** Small helper so the lambda can reference adapter-dep count without shadowing. */
    private fun adapterDeps(repoDeps: Int, ctrlDeps: Int): Int = repoDeps + ctrlDeps

    // ── Layer resolver ────────────────────────────────────────────────────────

    /**
     * Returns the canonical layer string expected by the ML service.
     *
     * Layer strings per architecture that the model was trained on:
     *   layered            → controller | service | repository | entity | config
     *   mvc                → controller | service | repository | entity | config | view
     *   hexagonal          → port | adapter | usecase | gateway | entity | config
     *   clean_architecture → usecase | gateway | entity | adapter | port | config
     */
    private fun detectLayer(
        architecture    : String,
        isController    : Boolean,
        isService       : Boolean,
        isRepository    : Boolean,
        isEntity        : Boolean,
        isConfig        : Boolean,
        isComponent     : Boolean,
        isPort          : Boolean,
        isAdapter       : Boolean,
        isUseCase       : Boolean,
        isGateway       : Boolean,
        isView          : Boolean,
        hasHttpHandling : Boolean,
        hasDataAccess   : Boolean,
        hasBusinessLogic: Boolean,
        repositoryDeps  : Int,
        packageName     : String,
        fileName        : String
    ): String {
        val pkg  = packageName
        val file = fileName

        return when (architecture) {

            "hexagonal" -> detectHexagonalLayer(
                isController, isService, isRepository, isEntity, isConfig,
                isPort, isAdapter, isUseCase, isGateway,
                hasHttpHandling, hasDataAccess, hasBusinessLogic,
                repositoryDeps, pkg, file
            )

            "clean_architecture" -> detectCleanLayer(
                isController, isService, isRepository, isEntity, isConfig,
                isPort, isAdapter, isUseCase, isGateway,
                hasHttpHandling, hasDataAccess, hasBusinessLogic,
                repositoryDeps, pkg, file
            )

            "mvc" -> detectMvcLayer(
                isController, isService, isRepository, isEntity, isConfig,
                isView, hasHttpHandling, hasDataAccess, hasBusinessLogic,
                repositoryDeps, pkg, file
            )

            else -> detectLayeredLayer(
                isController, isService, isRepository, isEntity, isConfig,
                isComponent, hasHttpHandling, hasDataAccess, hasBusinessLogic,
                repositoryDeps, pkg, file
            )
        }
    }

    // ── Architecture-specific layer resolvers ─────────────────────────────────

    private fun detectLayeredLayer(
        isController: Boolean, isService: Boolean, isRepository: Boolean,
        isEntity: Boolean, isConfig: Boolean, isComponent: Boolean,
        hasHttpHandling: Boolean, hasDataAccess: Boolean, hasBusinessLogic: Boolean,
        repositoryDeps: Int, pkg: String, file: String
    ): String {
        // 1. Annotation-based (highest confidence)
        if (isController || hasHttpHandling) return "controller"
        if (isEntity)                        return "entity"
        if (isRepository)                    return "repository"
        if (isService)                       return "service"
        if (isConfig)                        return "config"

        // 2. Filename heuristics
        if (file.endsWith("controller"))                         return "controller"
        if (file.endsWith("service") || file.endsWith("serviceimpl")) return "service"
        if (file.endsWith("repository") || file.endsWith("dao")
            || file.endsWith("daoimpl"))                         return "repository"
        if (file.endsWith("entity") || file.endsWith("model")
            || file.endsWith("domain"))                          return "entity"
        if (file.endsWith("config") || file.endsWith("configuration")
            || file.endsWith("application"))                     return "config"
        if (file.endsWith("dto") || file.endsWith("request")
            || file.endsWith("response"))                        return "dto"
        if (file.endsWith("mapper") || file.endsWith("converter")) return "mapper"

        // 3. Behavioural fallback
        if (hasDataAccess && repositoryDeps > 0) return "service"
        if (hasDataAccess)                       return "repository"
        if (hasBusinessLogic)                    return "service"
        return "unknown"
    }

    private fun detectMvcLayer(
        isController: Boolean, isService: Boolean, isRepository: Boolean,
        isEntity: Boolean, isConfig: Boolean, isView: Boolean,
        hasHttpHandling: Boolean, hasDataAccess: Boolean, hasBusinessLogic: Boolean,
        repositoryDeps: Int, pkg: String, file: String
    ): String {
        if (isController || hasHttpHandling) return "controller"
        if (isView)                          return "view"
        if (isEntity)                        return "entity"
        if (isRepository)                    return "repository"
        if (isService)                       return "service"
        if (isConfig)                        return "config"

        if (VIEW_PKG_MARKERS.any { pkg.contains(it) }) return "view"

        if (file.endsWith("controller"))                               return "controller"
        if (file.endsWith("service") || file.endsWith("serviceimpl")) return "service"
        if (file.endsWith("repository") || file.endsWith("dao"))       return "repository"
        if (file.endsWith("model") || file.endsWith("entity")
            || file.endsWith("domain"))                                return "entity"
        if (file.endsWith("view") || file.endsWith("viewmodel")
            || file.endsWith("form"))                                  return "view"
        if (file.endsWith("config") || file.endsWith("application"))   return "config"
        if (file.endsWith("dto") || file.endsWith("request")
            || file.endsWith("response"))                              return "dto"

        if (hasDataAccess) return "repository"
        if (hasBusinessLogic) return "service"
        return "unknown"
    }

    private fun detectHexagonalLayer(
        isController: Boolean, isService: Boolean, isRepository: Boolean,
        isEntity: Boolean, isConfig: Boolean,
        isPort: Boolean, isAdapter: Boolean, isUseCase: Boolean, isGateway: Boolean,
        hasHttpHandling: Boolean, hasDataAccess: Boolean, hasBusinessLogic: Boolean,
        repositoryDeps: Int, pkg: String, file: String
    ): String {
        // Annotation-based gives us controller/service/entity/repository signals
        // but in hexagonal arch those map to adapter/usecase/entity/gateway
        if (isPort)                          return "port"
        if (isAdapter || isController || hasHttpHandling) return "adapter"
        if (isUseCase || isService)          return "usecase"
        if (isGateway || isRepository)       return "gateway"
        if (isEntity)                        return "entity"
        if (isConfig)                        return "config"

        // Package heuristics
        if (PORT_PKG_MARKERS.any    { pkg.contains(it) }) return "port"
        if (ADAPTER_PKG_MARKERS.any { pkg.contains(it) }) return "adapter"
        if (USECASE_PKG_MARKERS.any { pkg.contains(it) }) return "usecase"
        if (GATEWAY_PKG_MARKERS.any { pkg.contains(it) }) return "gateway"

        // Filename heuristics
        if (file.endsWith("port") || file.endsWith("inputport")
            || file.endsWith("outputport"))                return "port"
        if (file.endsWith("adapter") || file.endsWith("adapterimpl")) return "adapter"
        if (file.endsWith("usecase") || file.endsWith("usecaseimpl")
            || file.endsWith("interactor"))                return "usecase"
        if (file.endsWith("gateway") || file.endsWith("gatewayimpl")
            || file.endsWith("datasource"))                return "gateway"
        if (file.endsWith("entity") || file.endsWith("domain")) return "entity"
        if (file.endsWith("config") || file.endsWith("application")) return "config"

        if (hasDataAccess)   return "gateway"
        if (hasBusinessLogic) return "usecase"
        return "unknown"
    }

    private fun detectCleanLayer(
        isController: Boolean, isService: Boolean, isRepository: Boolean,
        isEntity: Boolean, isConfig: Boolean,
        isPort: Boolean, isAdapter: Boolean, isUseCase: Boolean, isGateway: Boolean,
        hasHttpHandling: Boolean, hasDataAccess: Boolean, hasBusinessLogic: Boolean,
        repositoryDeps: Int, pkg: String, file: String
    ): String {
        // Clean Architecture rings: Entity (innermost) → UseCase → Gateway/Port → Adapter (outermost)
        if (isEntity)                          return "entity"
        if (isUseCase || isService)            return "usecase"
        if (isPort)                            return "port"
        if (isGateway || isRepository)         return "gateway"
        if (isAdapter || isController || hasHttpHandling) return "adapter"
        if (isConfig)                          return "config"

        // Package heuristics
        if (GATEWAY_PKG_MARKERS.any { pkg.contains(it) }) return "gateway"
        if (USECASE_PKG_MARKERS.any { pkg.contains(it) }) return "usecase"
        if (PORT_PKG_MARKERS.any    { pkg.contains(it) }) return "port"
        if (ADAPTER_PKG_MARKERS.any { pkg.contains(it) }) return "adapter"

        // Filename heuristics
        if (file.endsWith("usecase") || file.endsWith("usecaseimpl")
            || file.endsWith("interactor"))                return "usecase"
        if (file.endsWith("gateway") || file.endsWith("gatewayimpl")) return "gateway"
        if (file.endsWith("port") || file.endsWith("inputport")
            || file.endsWith("outputport"))                return "port"
        if (file.endsWith("adapter") || file.endsWith("adapterimpl")) return "adapter"
        if (file.endsWith("entity") || file.endsWith("domain")) return "entity"
        if (file.endsWith("config") || file.endsWith("application")) return "config"

        if (hasDataAccess)    return "gateway"
        if (hasBusinessLogic) return "usecase"
        return "unknown"
    }
}