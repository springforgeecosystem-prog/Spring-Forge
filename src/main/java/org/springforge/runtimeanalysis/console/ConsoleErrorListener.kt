package org.springforge.runtimeanalysis.console

import com.intellij.execution.process.*
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

class ConsoleErrorListener(
        private val project: Project
) : ProcessAdapter() {

    private val buffer = StringBuilder()
    private var popupShown = false

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {

        // Capture both STDOUT and STDERR (Spring Boot logs exceptions to STDOUT)
        if (outputType != ProcessOutputTypes.STDERR && outputType != ProcessOutputTypes.STDOUT) return

        buffer.append(event.text)

        if (!popupShown && looksLikeRuntimeError(buffer.toString())) {
            popupShown = true
            showPopup()
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        if (!popupShown && looksLikeRuntimeError(buffer.toString())) {
            popupShown = true
            showPopup()
        }
    }

    private fun looksLikeRuntimeError(text: String): Boolean {
        return listOf(
                "Exception",
                "Error",
                "APPLICATION FAILED TO START",
                "Caused by:"
        ).any { it in text }
    }

    private fun showPopup() {
        val stacktrace = buffer.toString()

        ApplicationManager.getApplication().invokeLater {
            val notification = Notification(
                    "SpringForge",
                    "Runtime Error Detected",
                    "SpringForge detected a runtime failure. Analyze?",
                    NotificationType.ERROR
            )

            notification.addAction(
                    NotificationAction.createSimple("Analyze with SpringForge") {
                        ConsoleErrorStore.set(stacktrace)
                    }
            )

            Notifications.Bus.notify(notification, project)
        }
    }
}
