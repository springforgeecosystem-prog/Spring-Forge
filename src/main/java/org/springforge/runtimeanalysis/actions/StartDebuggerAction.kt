package org.springforge.runtimeanalysis.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class StartDebuggerAction : AnAction("Start Runtime Debugger") {

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "Debugger Module is working!\nAttaching to runtime...",
            "SpringForge - Debugger"
        )
    }
}
