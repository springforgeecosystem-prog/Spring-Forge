package org.springforge.toolwindow

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

/**
 * Service to hold reference to SpringForge Tool Window
 */
@Service(Service.Level.PROJECT)
class SpringForgeToolWindowService(val project: Project) {
    var toolWindow: ToolWindow? = null
}
