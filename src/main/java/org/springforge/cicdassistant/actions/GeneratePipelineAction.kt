package org.springforge.cicdassistant.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class GeneratePipelineAction : AnAction("Generate CI/CD Pipeline") {

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "CI/CD Module is working!\nCreating pipeline configuration...",
            "SpringForge - CI/CD"
        )
    }
}
