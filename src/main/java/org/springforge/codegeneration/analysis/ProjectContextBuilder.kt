package org.springforge.codegeneration.analysis

import com.intellij.openapi.project.Project
import org.springforge.codegeneration.service.ArchitecturePredictor

object ProjectContextBuilder {

    fun build(project: Project): ProjectAnalysisResult {
        // 1. Basic Analysis (Base package, naming conventions, etc.)
        val analyzed = ProjectAnalyzer(project).analyze()

        // 2. FIX: Use the shared FeatureExtractor so inputs match the ML model
        // (This ensures we send "controller" instead of "layer_controller")
        val extractor = FeatureExtractor(project)
        val features = extractor.extractAllFeatures()

        // 3. Get Prediction
        val predictor = ArchitecturePredictor()
        val prediction = predictor.predict(features)

        // 4. FIX: Apply the same Heuristics as ExistingProjectAction
        // If we don't do this, the popup might say "MVC" (corrected)
        // while this prompt says "Layered" (raw model output).
        val finalArchitecture = if (prediction != null) {
            applyHeuristics(prediction.predicted, prediction.confidence, features)
        } else {
            analyzed.detectedArchitecture // fallback to "unknown"
        }

        val finalConfidence = prediction?.confidence ?: analyzed.confidence

        return analyzed.copy(
            detectedArchitecture = finalArchitecture,
            confidence = finalConfidence
        )
    }

    /**
     * Shared heuristic logic to correct low-confidence predictions..
     * Kept identical to ExistingProjectAction to ensure consistency.
     */
    private fun applyHeuristics(
        predicted: String,
        confidence: Double,
        f: Map<String, Int>
    ): String {

        if (confidence >= 0.75) return predicted

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

        // Case 1: Model predicted MVC
        if (predicted == "mvc") {
            if (cleanBias >= 10 && uniqueLayers >= 3) corrected = "clean"
            if (layeredBias >= 15 && services > controllers) corrected = "layered"
            if (domain >= 5 && domain > controllers) corrected = "clean"
            if (usecases > 0 && usecases >= controllers) corrected = "clean"
        }

        // Case 2: Model predicted Clean
        if (predicted == "clean") {
            if (controllerBias >= 10 && controllers >= services) corrected = "mvc"
            if (domain < 3 && controllerBias > cleanBias) corrected = "mvc"
            if (repositories > services && layeredBias > cleanBias) corrected = "layered"
        }

        // Case 3: Model predicted Layered
        if (predicted == "layered") {
            if (cleanBias >= 12 && domain > repositories) corrected = "clean"
            if (controllerBias >= 10 && controllers > services) corrected = "mvc"
        }

        // Global Fallback
        if (confidence < 0.60) {
            if (cleanBias > layeredBias && cleanBias > controllerBias) corrected = "clean"
            if (layeredBias > cleanBias && layeredBias > controllerBias) corrected = "layered"
            if (controllerBias > cleanBias && controllerBias > layeredBias) corrected = "mvc"
        }

        return corrected
    }
}