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

        val content = ContentFactory.getInstance()
            .createContent(panel, "", false)

        toolWindow.contentManager.addContent(content)
    }

    companion object {
        fun getPanel(project: Project): QualityToolWindowPanel {
            val service = project.getService(QualityToolWindowService::class.java)
            val tw = service.toolWindow ?: return QualityToolWindowPanel()

            return tw.contentManager.selectedContent?.component as? QualityToolWindowPanel
                ?: QualityToolWindowPanel()
        }
    }

}
