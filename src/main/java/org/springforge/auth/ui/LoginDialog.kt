package org.springforge.auth.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import org.springforge.auth.AuthException
import org.springforge.auth.AuthService
import org.springforge.auth.SessionManager
import java.awt.*
import javax.swing.*

/**
 * Modal login dialog shown before the user can access SpringForge features.
 */
class LoginDialog(private val project: Project?) : DialogWrapper(project) {

    private val emailField = JBTextField(25)
    private val passwordField = JPasswordField(25)
    private val errorLabel = JBLabel("").apply {
        foreground = JBColor.RED
        isVisible = false
    }

    var loginSucceeded = false
        private set

    init {
        title = "SpringForge — Login"
        setOKButtonText("Login")
        setCancelButtonText("Exit")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(12)
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        // Title
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER
        val titleLabel = JBLabel("Sign in to SpringForge")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
        panel.add(titleLabel, gbc)

        // Spacer
        gbc.gridy = 1
        panel.add(Box.createVerticalStrut(8), gbc)

        // Email label
        gbc.gridy = 2; gbc.gridwidth = 1; gbc.gridx = 0; gbc.anchor = GridBagConstraints.LINE_END
        panel.add(JBLabel("Email:"), gbc)

        // Email field
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START; gbc.weightx = 1.0
        panel.add(emailField, gbc)

        // Password label
        gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.LINE_END
        panel.add(JBLabel("Password:"), gbc)

        // Password field
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.LINE_START; gbc.weightx = 1.0
        panel.add(passwordField, gbc)

        // Error message
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER
        panel.add(errorLabel, gbc)

        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = emailField

    override fun doValidate(): ValidationInfo? {
        if (emailField.text.isNullOrBlank()) return ValidationInfo("Email is required", emailField)
        if (passwordField.password.isEmpty()) return ValidationInfo("Password is required", passwordField)
        return null
    }

    override fun doOKAction() {
        val email = emailField.text.trim()
        val password = String(passwordField.password)

        // Disable button and show progress immediately (already on EDT)
        isOKActionEnabled = false
        errorLabel.text = "Logging in..."
        errorLabel.foreground = JBColor.GRAY
        errorLabel.isVisible = true

        // Run login on a background thread to keep UI responsive
        Thread {
            try {
                val response = AuthService.login(email, password)
                SessionManager.getInstance().login(response.token, response.user)
                loginSucceeded = true
                // Close dialog on EDT
                SwingUtilities.invokeLater {
                    close(OK_EXIT_CODE)
                }
            } catch (ex: AuthException) {
                SwingUtilities.invokeLater {
                    isOKActionEnabled = true
                    errorLabel.text = ex.message ?: "Login failed"
                    errorLabel.foreground = JBColor.RED
                    errorLabel.isVisible = true
                }
            } catch (ex: Exception) {
                SwingUtilities.invokeLater {
                    isOKActionEnabled = true
                    errorLabel.text = "Connection error: ${ex.message}"
                    errorLabel.foreground = JBColor.RED
                    errorLabel.isVisible = true
                }
            }
        }.start()
    }

    companion object {
        /**
         * Show the login dialog and return true if user logged in successfully.
         */
        fun showAndLogin(project: Project?): Boolean {
            val dialog = LoginDialog(project)
            dialog.show()
            return dialog.loginSucceeded
        }

        /**
         * Ensure the user is logged in. If not, show the login dialog.
         * @return true if a valid session exists after this call.
         */
        fun ensureLoggedIn(project: Project?): Boolean {
            val session = SessionManager.getInstance()
            if (session.isLoggedIn) {
                // Verify token is still valid
                try {
                    val user = AuthService.verifyToken(session.token!!)
                    session.login(session.token!!, user) // refresh user info
                    return true
                } catch (_: Exception) {
                    session.logout() // token expired, force re-login
                }
            }
            return showAndLogin(project)
        }
    }
}
