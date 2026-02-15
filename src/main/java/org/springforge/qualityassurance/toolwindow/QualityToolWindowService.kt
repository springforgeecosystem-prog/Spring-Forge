package org.springforge.qualityassurance.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

@Service(Service.Level.PROJECT)
class QualityToolWindowService(val project: Project) {
    var toolWindow: ToolWindow? = null
}
