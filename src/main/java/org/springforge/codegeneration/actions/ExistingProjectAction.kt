package org.springforge.codegeneration.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.springforge.codegeneration.analysis.FeatureExtractor
import org.springforge.codegeneration.service.ArchitecturePredictor

class ExistingProjectAction : AnAction("Existing Project") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Extract 58 features
        val extractor = FeatureExtractor(project)
        val features = extractor.extractAllFeatures()   // <-- FIXED HERE

        // Call ML backend
        val predictor = ArchitecturePredictor()
        val result = predictor.predict(features)

        if (result == null) {
            Messages.showErrorDialog(project, "Unable to connect to ML server", "SpringForge")
            return
        }

        val arch = result.predicted.uppercase()
        val conf = (result.confidence * 100).toInt()

        Messages.showInfoMessage(
            project,
            "Predicted Architecture: $arch\nConfidence: $conf%",
            "SpringForge Architecture Detection"
        )
    }
}
