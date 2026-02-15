package org.springforge.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.springforge.icons.SpringForgeIcons

/**
 * Main SpringForge Tool Window Factory
 * Creates a sidebar panel with all SpringForge features
 */
class SpringForgeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Set custom icon
        toolWindow.setIcon(SpringForgeIcons.ToolWindow)

        // Get or create the service
        val service = project.getService(SpringForgeToolWindowService::class.java)
        service.toolWindow = toolWindow

        // Create the main panel with all features
        val mainPanel = SpringForgeToolWindowPanel(project)

        // Create content and add to tool window
        val content = ContentFactory.getInstance()
            .createContent(mainPanel, "", false)

        toolWindow.contentManager.addContent(content)
    }

    companion object {
        /**
         * Get the main SpringForge panel for a project
         */
        fun getPanel(project: Project): SpringForgeToolWindowPanel? {
            val service = project.getService(SpringForgeToolWindowService::class.java)
            val tw = service.toolWindow ?: return null

            return tw.contentManager.selectedContent?.component as? SpringForgeToolWindowPanel
        }
    }
}
