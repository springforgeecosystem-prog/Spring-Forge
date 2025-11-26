package org.springforge.codegeneration.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.springforge.codegeneration.analysis.FeatureExtractor
import org.springforge.codegeneration.service.ArchitecturePredictor

class ExistingProjectAction : AnAction("Existing Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Extract features using PSI
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

        // If confidence >= 75%, trust model unless anomaly detected
        if (confidence >= 0.75) {
            val anomaly = detectArchitectureAnomaly(predicted, f)
            if (anomaly != null) return anomaly
            return predicted
        }

        var corrected = predicted

        val c = f["controller"] ?: 0
        val s = f["service"] ?: 0
        val r = f["repository"] ?: 0
        val d = f["domain_layer"] ?: 0
        val u = f["usecase_layer"] ?: 0
        val layers = f["unique_layers_used"] ?: 0

        val c2s = f["controller_layer_to_service_layer"] ?: 0
        val s2r = f["service_layer_to_repository_layer"] ?: 0
        val r2s = f["repository_layer_to_service_layer"] ?: 0
        val d2s = f["domain_layer_to_service_layer"] ?: 0

        // Weighted architecture signature scoring
        val mvcScore =
            (c * 3) + (c2s * 2) + (c - d) + if (layers <= 2) 4 else 0

        val cleanScore =
            (d * 3) + (u * 4) + (d2s * 2) + (layers * 2) + if (c < d) 3 else 0

        val layeredScore =
            (s2r * 3) + (r2s * 3) + (s * 2) + (r * 2) + if (layers == 3) 2 else 0

        val archScores = listOf(
            "mvc" to mvcScore,
            "clean" to cleanScore,
            "layered" to layeredScore
        ).sortedByDescending { it.second }

        val topCandidate = archScores[0].first
        val topScore = archScores[0].second

        // Override if structural score contradicts model strongly
        val strongConflict = topCandidate != predicted && topScore >= 10
        if (strongConflict) {
            corrected = topCandidate
        }

        // Strong domain/usecase → Clean
        if (d >= 6 || u >= 3 || (d + u) >= 8) {
            corrected = "clean"
        }

        // Strong service-repository interaction → Layered
        if (s2r >= 10 || r2s >= 10 || (s > c && r > c)) {
            corrected = "layered"
        }

        // Strong controller dominance → MVC
        if (c >= 10 && c > s && c > d) {
            corrected = "mvc"
        }

        // Final fallback when confidence extremely low
        if (confidence < 0.60) {
            corrected = topCandidate
        }

        return corrected
    }


    // ----------------------------------------------------------
    // DETECT ANOMALIES WHEN MODEL IS CONFIDENT
    // ----------------------------------------------------------
    private fun detectArchitectureAnomaly(pred: String, f: Map<String, Int>): String? {

        val c = f["controller"] ?: 0
        val s = f["service"] ?: 0
        val r = f["repository"] ?: 0
        val d = f["domain_layer"] ?: 0
        val u = f["usecase_layer"] ?: 0

        // MVC anomaly: domain/usecase heavy
        if (pred == "mvc" && (d >= 6 || u >= 2) && d > c) {
            return "clean"
        }

        // MVC anomaly: repository/service dominant
        if (pred == "mvc" && r > c && s > c) {
            return "layered"
        }

        // Clean anomaly: controllers dominating
        if (pred == "clean" && c >= 10 && c > d) {
            return "mvc"
        }

        // Layered anomaly: domain/usecase heavy
        if (pred == "layered" && (d + u) >= 7) {
            return "clean"
        }

        return null
    }
}
