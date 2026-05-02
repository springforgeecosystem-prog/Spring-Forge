package org.springforge.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.springforge.auth.ui.LoginDialog
import java.awt.*
import javax.swing.*

/**
 * Shown in the tool window when the user has not logged in yet.
 * Contains a login button that triggers the login dialog.
 */
class LoginRequiredPanel(
    private val project: Project,
    private val onLoginSuccess: () -> Unit
) : JPanel() {

    init {
        layout = GridBagLayout()
        border = JBUI.Borders.empty(24)
        setupUI()
    }

    private fun setupUI() {
        val gbc = GridBagConstraints().apply {
            gridx = 0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(4)
        }

        // Title
        gbc.gridy = 0
        val title = JBLabel("SpringForge Tools")
        title.font = title.font.deriveFont(Font.BOLD, 18f)
        title.horizontalAlignment = SwingConstants.CENTER
        add(title, gbc)

        // Spacer
        gbc.gridy = 1
        add(Box.createVerticalStrut(12), gbc)

        // Message
        gbc.gridy = 2
        val msg = JBLabel("Please log in to access SpringForge features.")
        msg.foreground = JBColor.GRAY
        msg.horizontalAlignment = SwingConstants.CENTER
        add(msg, gbc)

        // Spacer
        gbc.gridy = 3
        add(Box.createVerticalStrut(16), gbc)

        // Login button
        gbc.gridy = 4
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        val loginButton = JButton("Login")
        loginButton.preferredSize = Dimension(120, 36)
        loginButton.addActionListener {
            val success = LoginDialog.showAndLogin(project)
            if (success) {
                onLoginSuccess()
            }
        }
        add(loginButton, gbc)
    }
}
