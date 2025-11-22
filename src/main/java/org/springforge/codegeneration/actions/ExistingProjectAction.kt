package org.springforge.codegeneration.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.springforge.codegeneration.analysis.FeatureExtractor
import org.springforge.codegeneration.service.ArchitecturePredictor

class ExistingProjectAction : AnAction("Existing Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Extract features from project
        val extractor = FeatureExtractor(project)
        val features = extractor.extractAllFeatures()

        // Call ML backend
        val predictor = ArchitecturePredictor()
        val result = predictor.predict(features)

        if (result == null) {
            Messages.showErrorDialog(project, "Unable to connect to ML server", "SpringForge")
            return
        }

        // Apply heuristic correction layer
        val corrected = applyHeuristics(
            predicted = result.predicted,
            confidence = result.confidence,
            f = features
        )

        val conf = (result.confidence * 100).toInt()

        Messages.showInfoMessage(
            project,
            "Predicted Architecture: ${corrected.uppercase()}\nConfidence: $conf%",
            "SpringForge Architecture Detection"
        )
    }


    // ----------------------------------------------------------
    // ADVANCED HEURISTIC CORRECTION LAYER
    // ----------------------------------------------------------
    private fun applyHeuristics(
        predicted: String,
        confidence: Double,
        f: Map<String, Int>
    ): String {

        if (confidence >= 0.75) return predicted   // trust model

        var corrected = predicted

        val controllers = f["controller"] ?: 0
        val services = f["service"] ?: 0
        val repositories = f["repository"] ?: 0
        val domain = f["domain_layer"] ?: 0
        val usecases = f["usecase_layer"] ?: 0
        val uniqueLayers = f["unique_layers_used"] ?: 0

        val ctrlToSvc = f["controller_layer_to_service_layer"] ?: 0
        val svcToRepo = f["service_layer_to_repository_layer"] ?: 0
        val repoToSvc = f["repository_layer_to_service_layer"] ?: 0
        val domainToSvc = f["domain_layer_to_service_layer"] ?: 0

        val controllerBias = controllers + ctrlToSvc
        val cleanBias = domain + usecases + domainToSvc
        val layeredBias = svcToRepo + repoToSvc


        // ───────────────────────────────────────────────
        // CASE 1: Model predicted MVC
        // ───────────────────────────────────────────────
        if (predicted == "mvc") {

            // Strong Clean signature
            if (cleanBias >= 10 && uniqueLayers >= 3) {
                corrected = "clean"
            }

            // Layered: repo/service heavy
            if (layeredBias >= 15 && services > controllers) {
                corrected = "layered"
            }

            // Domain dominant → Clean
            if (domain >= 5 && domain > controllers) {
                corrected = "clean"
            }

            // Usecase present → Clean
            if (usecases > 0 && usecases >= controllers) {
                corrected = "clean"
            }
        }


        // ───────────────────────────────────────────────
        // CASE 2: Model predicted Clean
        // ───────────────────────────────────────────────
        if (predicted == "clean") {

            // MVC signature: heavy controllers
            if (controllerBias >= 10 && controllers >= services) {
                corrected = "mvc"
            }

            // Domain lacking but controllers dominate → MVC
            if (domain < 3 && controllerBias > cleanBias) {
                corrected = "mvc"
            }

            // Layered: heavy repo→service
            if (repositories > services && layeredBias > cleanBias) {
                corrected = "layered"
            }
        }


        // ───────────────────────────────────────────────
        // CASE 3: Model predicted Layered
        // ───────────────────────────────────────────────
        if (predicted == "layered") {

            // Clean: domain + usecase
            if (cleanBias >= 12 && domain > repositories) {
                corrected = "clean"
            }

            // MVC version of layered
            if (controllerBias >= 10 && controllers > services) {
                corrected = "mvc"
            }
        }


        // ───────────────────────────────────────────────
        // GLOBAL FALLBACK (very low confidence)
        // ───────────────────────────────────────────────
        if (confidence < 0.60) {

            if (cleanBias > layeredBias && cleanBias > controllerBias)
                corrected = "clean"

            if (layeredBias > cleanBias && layeredBias > controllerBias)
                corrected = "layered"

            if (controllerBias > cleanBias && controllerBias > layeredBias)
                corrected = "mvc"
        }

        return corrected
    }
}
