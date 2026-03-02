package org.springforge.qualityassurance.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class QualityToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.getService(QualityToolWindowService::class.java)
        service.toolWindow = toolWindow

        val panel = QualityToolWindowPanel()
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        fun getPanel(project: Project): QualityToolWindowPanel? {
            val service = project.getService(QualityToolWindowService::class.java)
                ?: return null
            val toolWindow = service.toolWindow ?: return null
            val selectedContent = toolWindow.contentManager.selectedContent ?: return null
            return selectedContent.component as? QualityToolWindowPanel
        }
    }
}