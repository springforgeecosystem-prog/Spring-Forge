package org.springforge.codegeneration.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class GenerateCodeAction : AnAction("Generate Code") {

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "CodeGen Module is working!\nGenerating Spring Boot code...",
            "SpringForge - CodeGen"
        )
    }
}
