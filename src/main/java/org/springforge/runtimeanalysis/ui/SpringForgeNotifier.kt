package org.springforge.runtimeanalysis.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object SpringForgeNotifier {

    fun info(project: Project, message: String) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("SpringForge Notifications")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
    }

    fun error(project: Project, message: String) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("SpringForge Notifications")
                .createNotification(message, NotificationType.ERROR)
                .notify(project)
    }
}
