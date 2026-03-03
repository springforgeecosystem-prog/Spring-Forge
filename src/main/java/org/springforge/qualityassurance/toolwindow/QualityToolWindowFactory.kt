// src/main/java/org/springforge/qualityassurance/toolwindow/QualityToolWindowFactory.kt
// REPLACE your existing QualityToolWindowFactory.kt with this file
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
        /**
         * Returns the QualityToolWindowPanel if it exists.
         * Checks ALL contents (not just selected) so it works even when
         * the tool window hasn't been interacted with yet.
         */
        fun getPanel(project: Project): QualityToolWindowPanel? {
            return try {
                val service = project.getService(QualityToolWindowService::class.java)
                    ?: return null
                val toolWindow = service.toolWindow ?: return null
                val contentManager = toolWindow.contentManager

                // Try selected content first
                val selected = contentManager.selectedContent
                if (selected?.component is QualityToolWindowPanel) {
                    return selected.component as QualityToolWindowPanel
                }

                // Fall back to first content (panel always added at index 0)
                val first = contentManager.getContent(0)
                first?.component as? QualityToolWindowPanel

            } catch (ex: Exception) {
                println("⚠️ QualityToolWindowFactory.getPanel() error: ${ex.message}")
                null
            }
        }
    }
}