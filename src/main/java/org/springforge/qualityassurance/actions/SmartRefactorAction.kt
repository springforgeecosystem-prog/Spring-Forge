package org.springforge.qualityassurance.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class SmartRefactorAction : AnAction("Smart Refactor") {

    override fun actionPerformed(e: AnActionEvent) {
        Messages.showInfoMessage(
            "Refactor Module is working!\nRefactoring source files...",
            "SpringForge - Refactor"
        )
    }
}
