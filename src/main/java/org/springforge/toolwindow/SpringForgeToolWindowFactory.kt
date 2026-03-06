package org.springforge.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import org.springforge.auth.SessionManager
import org.springforge.auth.ui.LoginDialog
import org.springforge.icons.SpringForgeIcons

/**
 * Main SpringForge Tool Window Factory
 * Creates a sidebar panel with all SpringForge features.
 * Requires user login before showing the main content.
 */
class SpringForgeToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Set custom icon
        toolWindow.setIcon(SpringForgeIcons.ToolWindow)

        // Get or create the service
        val service = project.getService(SpringForgeToolWindowService::class.java)
        service.toolWindow = toolWindow

        if (SessionManager.getInstance().isLoggedIn) {
            showMainPanel(project, toolWindow)
        } else {
            showLoginPanel(project, toolWindow)
        }
    }

    private fun showLoginPanel(project: Project, toolWindow: ToolWindow) {
        val loginPanel = LoginRequiredPanel(project) {
            // On successful login, replace with main panel
            toolWindow.contentManager.removeAllContents(true)
            showMainPanel(project, toolWindow)
        }
        val content = ContentFactory.getInstance()
            .createContent(loginPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun showMainPanel(project: Project, toolWindow: ToolWindow) {
        val mainPanel = SpringForgeToolWindowPanel(project)
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
