package org.springforge.subscription.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import org.springforge.auth.SessionManager
import org.springforge.subscription.SubscriptionManager
import java.awt.*
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Dialog shown when a Community user has exhausted their 5 requests/month limit.
 */
class RequestLimitDialog(private val project: Project) : JDialog() {

    init {
        title = "Monthly Request Limit Reached"
        isModal = true
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = buildContent()
        pack()
        setLocationRelativeTo(null)
        minimumSize = Dimension(420, 300)
    }

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(0, 0))
        root.border = EmptyBorder(24, 28, 20, 28)
        root.background = UIManager.getColor("Panel.background")

        // Icon + title row
        val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        titleRow.isOpaque = false
        val iconLabel = JLabel("⚠")
        iconLabel.font = iconLabel.font.deriveFont(22f)
        iconLabel.foreground = Color(230, 130, 0)
        val titleLabel = JLabel("Request Limit Reached")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 15f)
        titleRow.add(iconLabel)
        titleRow.add(titleLabel)

        // Body text
        val sub = SubscriptionManager.getInstance()
        val used = sub.status.requestsUsed
        val limit = sub.status.requestsLimit
        val resetAt = formatResetDate(sub.status.resetAt)

        val bodyText = JTextArea(
            "You've used $used/$limit AI requests this month.\n\n" +
            "Your usage resets on $resetAt.\n\n" +
            "Upgrade to Ultimate for unlimited requests across all modules."
        )
        bodyText.isEditable = false
        bodyText.isOpaque = false
        bodyText.font = UIManager.getFont("Label.font")
        bodyText.lineWrap = true
        bodyText.wrapStyleWord = true
        bodyText.border = EmptyBorder(12, 0, 16, 0)

        // Button row
        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        btnRow.isOpaque = false

        val refreshBtn = JButton("Refresh Plan")
        refreshBtn.font = refreshBtn.font.deriveFont(11f)
        refreshBtn.addActionListener {
            val token = SessionManager.getInstance().token ?: return@addActionListener
            refreshBtn.isEnabled = false
            refreshBtn.text = "Refreshing..."
            Thread {
                SubscriptionManager.getInstance().refreshStatus(token)
                SwingUtilities.invokeLater {
                    dispose()
                }
            }.start()
        }

        val upgradeBtn = JButton("Upgrade to Ultimate — \$9/month")
        upgradeBtn.font = upgradeBtn.font.deriveFont(Font.BOLD, 11f)
        upgradeBtn.foreground = Color.WHITE
        upgradeBtn.background = Color(80, 140, 255)
        upgradeBtn.isBorderPainted = false
        upgradeBtn.isOpaque = true
        upgradeBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        upgradeBtn.addActionListener { openCheckout() }

        btnRow.add(refreshBtn)
        btnRow.add(upgradeBtn)

        root.add(titleRow, BorderLayout.NORTH)
        root.add(bodyText, BorderLayout.CENTER)
        root.add(btnRow, BorderLayout.SOUTH)

        return root
    }

    private fun openCheckout() {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI("https://www.springforge.dev/pricing"))
        }
    }

    private fun formatResetDate(raw: String): String {
        if (raw.isBlank()) return "the 1st of next month"
        return try {
            val instant = Instant.parse(raw)
            val formatter = DateTimeFormatter
                .ofPattern("MMMM d, yyyy", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            raw
        }
    }

    companion object {
        fun show(project: Project) {
            SwingUtilities.invokeLater {
                RequestLimitDialog(project).isVisible = true
            }
        }
    }
}
