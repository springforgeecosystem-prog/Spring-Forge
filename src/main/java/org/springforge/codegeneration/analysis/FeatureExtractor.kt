package org.springforge.codegeneration.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * AST-driven feature extractor for SpringForge.
 *
 * Produces a Map<String, Int> with the 58 features expected by the ML model.
 *
 * Important: run on a background thread because it may traverse many files.
 */
class FeatureExtractor(private val project: Project) {

    /**
     * Public entry - returns a snapshot map of features (all int values).
     */
    fun extractAllFeatures(): Map<String, Int> {
        // run inside a ReadAction for PSI safety
        return ReadAction.compute<Map<String, Int>, RuntimeException> {
            doExtract()
        }
    }

    private fun doExtract(): Map<String, Int> {
        // initialize feature map
        val features = mutableMapOf<String, Int>()
        FEATURE_LIST.forEach { features[it] = 0 }

        val psiManager = PsiManager.getInstance(project)

        // collect all Java files in project scope
        val javaFiles = mutableListOf<PsiJavaFile>()
        FileTypeIndex.processFiles(com.intellij.ide.highlighter.JavaFileType.INSTANCE, { vf ->
            val psi = psiManager.findFile(vf)
            if (psi is PsiJavaFile) javaFiles.add(psi)
            true
        }, GlobalSearchScope.projectScope(project))

        // helper maps for inter-layer resolution: class simple name -> layer
        val classLayerMap = ConcurrentHashMap<String, String>()

        // first pass: collect per-file metrics and class->layer mapping, imports
        for (jf in javaFiles) {
            features["total_java_files"] = features["total_java_files"]!! + 1

            val text = jf.text
            features["loc"] = features["loc"]!! + (text.lines().size)
            // approximate method count by counting method declarations
            val methodCount = PsiTreeUtil.findChildrenOfType(jf, PsiMethod::class.java).size
            features["method_count"] = features["method_count"]!! + methodCount

            // classes/interfaces
            val classes = jf.classes
            features["class_count"] = features["class_count"]!! + classes.size
            val interfaces = classes.count { it.isInterface }
            features["interface_count"] = features["interface_count"]!! + interfaces

            // detect file-name based hints
            val fname = jf.virtualFile?.name ?: ""
            if (fname.contains("Controller", true)) features["file_named_controller"] = features["file_named_controller"]!! + 1
            if (fname.contains("Service", true)) features["file_named_service"] = features["file_named_service"]!! + 1
            if (fname.contains("Repository", true)) features["file_named_repository"] = features["file_named_repository"]!! + 1
            if (DTO_REGEX.containsMatchIn(fname)) features["dto_like_names"] = features["dto_like_names"]!! + 1

            // imports
            val importList = jf.importList
            if (importList != null) {
                val imports = importList.importStatements
                for (imp in imports) {
                    val impText = imp.importReference?.qualifiedName ?: ""
                    when {
                        impText.startsWith("org.springframework.web") -> features["spring_web"] = features["spring_web"]!! + 1
                        impText.startsWith("org.springframework.stereotype") -> features["spring_stereotype"] = features["spring_stereotype"]!! + 1
                        impText.startsWith("org.springframework.data") || impText.startsWith("org.springframework.data.jpa") ->
                            features["spring_data"] = features["spring_data"]!! + 1
                        impText.startsWith("javax.persistence") || impText.startsWith("jakarta.persistence") ->
                            features["jpa"] = features["jpa"]!! + 1
                    }
                }
            }

            // package -> layer heuristics
            val pkg = jf.packageName?.lowercase() ?: ""
            val inferredLayer = inferLayerFromPath(jf.virtualFile?.path ?: pkg)
            if (inferredLayer != "unknown_layer") {
                features[inferredLayer] = features[inferredLayer]!! + 1
            } else {
                features["unknown_layer"] = features["unknown_layer"]!! + 1
            }

            // classes: annotations and map class->layer
            for (cls in classes) {
                val clsName = cls.name ?: continue
                val clsLayer = inferLayerFromClass(cls, pkg)
                classLayerMap[clsName] = clsLayer

                // annotations
                for (ann in cls.annotations) {
                    val qn = ann.qualifiedName ?: ann.text
                    when {
                        qn.endsWith("RestController") || qn.endsWith("Controller") -> {
                            features["controller"] = features["controller"]!! + 1
                            features["controller_layer"] = features["controller_layer"]!! + 1
                        }
                        qn.endsWith("Service") -> {
                            features["service"] = features["service"]!! + 1
                            features["service_layer"] = features["service_layer"]!! + 1
                        }
                        qn.endsWith("Repository") -> {
                            features["repository"] = features["repository"]!! + 1
                            features["repository_layer"] = features["repository_layer"]!! + 1
                        }
                        qn.endsWith("Entity") -> {
                            features["entity"] = features["entity"]!! + 1
                            features["is_entity"] = features["is_entity"]!! + 1
                        }
                        qn.endsWith("Configuration") -> features["config"] = features["config"]!! + 1
                        qn.endsWith("Component") -> features["component"] = features["component"]!! + 1
                    }
                }

                // detect DTO-like class names
                if (DTO_REGEX.containsMatchIn(clsName)) features["dto_like_names"] = features["dto_like_names"]!! + 1
            }

            // method bodies: detect service calls and inter-class references
            PsiTreeUtil.processElements(jf) { elem ->
                // detect method call expressions invoking XService.someMethod()
                if (elem is PsiMethodCallExpression) {
                    val refText = elem.methodExpression.qualifierExpression?.text ?: ""
                    if (refText.endsWith("Service")) {
                        features["service_call_count"] = features["service_call_count"]!! + 1
                    }

                    // record target type simple name if available (for inter-layer edges)
                    val qual = elem.methodExpression.qualifierExpression
                    if (qual is PsiReferenceExpression) {
                        val resolved = qual.reference?.resolve()
                        if (resolved is PsiClass) {
                            val targetName = resolved.name
                            if (targetName != null) {
                                // increment class reference - we will evaluate edges in second pass
                                // store temporary in features map using a reserved key (not persisted)
                                val key = "__ref__${targetName}"
                                features[key] = (features[key] ?: 0)
                                features[key] = features[key]!! + 1
                            }
                        }
                    }
                }
                true
            }
        } // end first pass

        // second pass: compute inter-layer edges using classLayerMap and temporary ref keys
        for ((k, v) in features.toMap()) {
            if (k.startsWith("__ref__")) {
                val targetClass = k.removePrefix("__ref__")
                val targetLayer = classLayerMap[targetClass] ?: "unknown_layer"
                // We cannot know source layer here easily (would need origin mapping). As a conservative fallback,
                // increment all possible edges from controllers/services/repositories present in this repo
                // A more accurate approach: build class->source mapping from definitions and resolve references earlier.
                // For now, estimate:
                when (targetLayer) {
                    "controller_layer" -> features["unknown_layer_to_controller_layer"] = features["unknown_layer_to_controller_layer"]!! + v
                    "service_layer" -> features["unknown_layer_to_service_layer"] = features["unknown_layer_to_service_layer"]!! + v
                    "repository_layer" -> features["unknown_layer_to_repository_layer"] = features["unknown_layer_to_repository_layer"]!! + v
                    "domain_layer" -> features["unknown_layer_to_domain_layer"] = features["unknown_layer_to_domain_layer"]!! + v
                    "usecase_layer" -> features["unknown_layer_to_usecase_layer"] = features["unknown_layer_to_usecase_layer"]!! + v
                    else -> {}
                }
                // remove the temporary key
                features.remove(k)
            }
        }

        // finalize unique_layers_used
        val usedLayers = classLayerMap.values.toSet().filter { it != "unknown_layer" }.size
        features["unique_layers_used"] = usedLayers

        return features
    }

    // infer a layer from file path using keywords
    private fun inferLayerFromPath(pathOrPkg: String): String {
        val p = pathOrPkg.lowercase()
        return when {
            p.contains("/controller") || p.contains(".controller") || p.contains("/web/") -> "controller_layer"
            p.contains("/service") || p.contains(".service") || p.contains("/application/") -> "service_layer"
            p.contains("/repository") || p.contains(".repository") || p.contains("/infrastructure/") -> "repository_layer"
            p.contains("/domain") || p.contains(".domain") || p.contains("/model/") -> "domain_layer"
            p.contains("/usecase") || p.contains(".usecase") || p.contains("/interactor/") -> "usecase_layer"
            else -> "unknown_layer"
        }
    }

    // infer layer from class annotations or package hints
    private fun inferLayerFromClass(cls: PsiClass, pkgLower: String): String {
        for (ann in cls.annotations) {
            val qn = ann.qualifiedName ?: ann.text
            if (qn.endsWith("Controller") || qn.endsWith("RestController")) return "controller_layer"
            if (qn.endsWith("Service")) return "service_layer"
            if (qn.endsWith("Repository")) return "repository_layer"
            if (qn.endsWith("Entity")) return "domain_layer"
        }
        // fallback to package keywords
        val pkg = pkgLower.lowercase()
        return inferLayerFromPath(pkg)
    }

    companion object {
        private val DTO_REGEX = Regex("(DTO|Request|Response|Command|Query)\$", RegexOption.IGNORE_CASE)

        // canonical feature list (58)
        val FEATURE_LIST = listOf(
            "class_count",
            "component",
            "config",
            "controller",
            "controller_layer",
            "controller_layer_to_domain_layer",
            "controller_layer_to_repository_layer",
            "controller_layer_to_service_layer",
            "controller_layer_to_unknown_layer",
            "controller_layer_to_usecase_layer",
            "domain_layer",
            "domain_layer_to_controller_layer",
            "domain_layer_to_repository_layer",
            "domain_layer_to_service_layer",
            "domain_layer_to_unknown_layer",
            "domain_layer_to_usecase_layer",
            "dto_like_names",
            "entity",
            "file_named_controller",
            "file_named_repository",
            "file_named_service",
            "interface_count",
            "is_entity",
            "jpa",
            "loc",
            "method_count",
            "repository",
            "repository_layer",
            "repository_layer_to_controller_layer",
            "repository_layer_to_domain_layer",
            "repository_layer_to_service_layer",
            "repository_layer_to_unknown_layer",
            "repository_layer_to_usecase_layer",
            "service",
            "service_call_count",
            "service_layer",
            "service_layer_to_controller_layer",
            "service_layer_to_domain_layer",
            "service_layer_to_repository_layer",
            "service_layer_to_unknown_layer",
            "service_layer_to_usecase_layer",
            "spring_data",
            "spring_stereotype",
            "spring_web",
            "total_java_files",
            "unique_layers_used",
            "unknown_layer",
            "unknown_layer_to_controller_layer",
            "unknown_layer_to_domain_layer",
            "unknown_layer_to_repository_layer",
            "unknown_layer_to_service_layer",
            "unknown_layer_to_usecase_layer",
            "usecase_layer",
            "usecase_layer_to_controller_layer",
            "usecase_layer_to_domain_layer",
            "usecase_layer_to_repository_layer",
            "usecase_layer_to_service_layer",
            "usecase_layer_to_unknown_layer"
        )
    }
}
