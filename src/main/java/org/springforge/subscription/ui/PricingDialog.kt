package org.springforge.subscription.ui

import com.intellij.ui.JBColor
import org.springforge.auth.SessionManager
import org.springforge.subscription.SubscriptionManager
import org.springforge.subscription.SubscriptionTier
import java.awt.*
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Full pricing comparison dialog opened from the header Upgrade button.
 */
class PricingDialog : JDialog() {

    init {
        title = "SpringForge Plans"
        isModal = true
        defaultCloseOperation = DISPOSE_ON_CLOSE
        contentPane = buildContent()
        pack()
        setLocationRelativeTo(null)
        minimumSize = Dimension(520, 460)
    }

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(0, 16))
        root.border = EmptyBorder(24, 28, 24, 28)
        root.background = UIManager.getColor("Panel.background")

        // Title
        val title = JLabel("Choose Your Plan")
        title.font = title.font.deriveFont(Font.BOLD, 18f)
        title.horizontalAlignment = SwingConstants.CENTER
        root.add(title, BorderLayout.NORTH)

        // Plan cards row
        val cardsPanel = JPanel(GridLayout(1, 2, 16, 0))
        cardsPanel.isOpaque = false

        val currentTier = SubscriptionManager.getInstance().status.tier
        cardsPanel.add(buildCommunityCard(currentTier))
        cardsPanel.add(buildUltimateCard(currentTier))

        root.add(cardsPanel, BorderLayout.CENTER)

        // Bottom row
        val bottomRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        bottomRow.isOpaque = false

        val refreshBtn = JButton("Refresh Plan")
        refreshBtn.font = refreshBtn.font.deriveFont(11f)
        refreshBtn.addActionListener {
            val token = SessionManager.getInstance().token ?: return@addActionListener
            refreshBtn.isEnabled = false
            refreshBtn.text = "Refreshing..."
            Thread {
                SubscriptionManager.getInstance().refreshStatus(token)
                SwingUtilities.invokeLater { dispose() }
            }.start()
        }

        val closeBtn = JButton("Close")
        closeBtn.font = closeBtn.font.deriveFont(11f)
        closeBtn.addActionListener { dispose() }

        bottomRow.add(refreshBtn)
        bottomRow.add(closeBtn)
        root.add(bottomRow, BorderLayout.SOUTH)

        return root
    }

    private fun buildCommunityCard(current: SubscriptionTier): JPanel {
        val card = JPanel(BorderLayout(0, 12))
        card.border = if (current == SubscriptionTier.COMMUNITY)
            LineBorder(Color(80, 140, 255), 2, true)
        else
            LineBorder(JBColor.border(), 1, true)
        card.background = UIManager.getColor("Panel.background")

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.isOpaque = false
        inner.border = EmptyBorder(16, 16, 16, 16)

        val nameLabel = JLabel("Community")
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, 14f)
        nameLabel.alignmentX = Component.LEFT_ALIGNMENT

        val priceLabel = JLabel("Free")
        priceLabel.font = priceLabel.font.deriveFont(Font.BOLD, 22f)
        priceLabel.foreground = JBColor.foreground()
        priceLabel.alignmentX = Component.LEFT_ALIGNMENT

        val sep = JSeparator()
        sep.alignmentX = Component.LEFT_ALIGNMENT
        sep.maximumSize = Dimension(Int.MAX_VALUE, 1)

        inner.add(nameLabel)
        inner.add(Box.createVerticalStrut(4))
        inner.add(priceLabel)
        inner.add(Box.createVerticalStrut(12))
        inner.add(sep)
        inner.add(Box.createVerticalStrut(12))

        listOf(
            "✓ All 4 modules — full access",
            "✓ CI/CD generation",
            "✓ Runtime analysis",
            "✓ Code quality analysis",
            "✓ Code generation",
            "⚠ 5 AI requests/month"
        ).forEach { inner.add(featureRow(it)) }

        if (current == SubscriptionTier.COMMUNITY) {
            inner.add(Box.createVerticalStrut(12))
            val badge = JLabel("Current Plan")
            badge.font = badge.font.deriveFont(Font.BOLD, 10f)
            badge.foreground = Color(80, 140, 255)
            badge.alignmentX = Component.LEFT_ALIGNMENT
            inner.add(badge)
        }

        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun buildUltimateCard(current: SubscriptionTier): JPanel {
        val card = JPanel(BorderLayout(0, 12))
        card.border = if (current == SubscriptionTier.ULTIMATE)
            LineBorder(Color(80, 200, 120), 2, true)
        else
            LineBorder(JBColor.border(), 1, true)
        card.background = UIManager.getColor("Panel.background")

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.isOpaque = false
        inner.border = EmptyBorder(16, 16, 16, 16)

        val nameLabel = JLabel("Ultimate")
        nameLabel.font = nameLabel.font.deriveFont(Font.BOLD, 14f)
        nameLabel.alignmentX = Component.LEFT_ALIGNMENT

        val priceRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        priceRow.isOpaque = false
        priceRow.alignmentX = Component.LEFT_ALIGNMENT
        val priceLabel = JLabel("\$9")
        priceLabel.font = priceLabel.font.deriveFont(Font.BOLD, 22f)
        val perMonth = JLabel("/month")
        perMonth.font = perMonth.font.deriveFont(12f)
        perMonth.foreground = JBColor.GRAY
        priceRow.add(priceLabel)
        priceRow.add(perMonth)

        val sep = JSeparator()
        sep.alignmentX = Component.LEFT_ALIGNMENT
        sep.maximumSize = Dimension(Int.MAX_VALUE, 1)

        inner.add(nameLabel)
        inner.add(Box.createVerticalStrut(4))
        inner.add(priceRow)
        inner.add(Box.createVerticalStrut(12))
        inner.add(sep)
        inner.add(Box.createVerticalStrut(12))

        listOf(
            "✓ All 4 modules — full access",
            "✓ CI/CD generation",
            "✓ Runtime analysis",
            "✓ Code quality analysis",
            "✓ Code generation",
            "✓ Unlimited AI requests"
        ).forEach { inner.add(featureRow(it)) }

        inner.add(Box.createVerticalStrut(12))

        if (current == SubscriptionTier.ULTIMATE) {
            val sub = SubscriptionManager.getInstance().status
            val expiresAt = sub.expiresAt

            if (expiresAt.isNotBlank()) {
                // Cancellation already scheduled — show pending notice, no cancel button
                val cancelNote = JLabel("Cancels on ${formatDate(expiresAt)}")
                cancelNote.font = cancelNote.font.deriveFont(Font.BOLD, 10f)
                cancelNote.foreground = Color(200, 150, 0)
                cancelNote.alignmentX = Component.LEFT_ALIGNMENT
                inner.add(cancelNote)
            } else {
                // Active Ultimate — show Cancel Plan button
                val cancelBtn = JButton("Cancel Plan")
                cancelBtn.font = cancelBtn.font.deriveFont(11f)
                cancelBtn.foreground = Color(200, 60, 60)
                cancelBtn.isBorderPainted = true
                cancelBtn.isOpaque = false
                cancelBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                cancelBtn.alignmentX = Component.LEFT_ALIGNMENT
                cancelBtn.maximumSize = Dimension(Int.MAX_VALUE, 32)
                cancelBtn.toolTipText = "Manage or cancel your subscription on the website"
                cancelBtn.addActionListener {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI("https://www.springforge.dev/pricing"))
                    }
                }
                inner.add(cancelBtn)
            }

            inner.add(Box.createVerticalStrut(6))

            // Refresh Plan button — always shown for Ultimate
            val refreshBtn = JButton("Refresh Plan")
            refreshBtn.font = refreshBtn.font.deriveFont(11f)
            refreshBtn.alignmentX = Component.LEFT_ALIGNMENT
            refreshBtn.maximumSize = Dimension(Int.MAX_VALUE, 32)
            refreshBtn.toolTipText = "Re-check your subscription status from the server"
            refreshBtn.addActionListener {
                val token = SessionManager.getInstance().token ?: return@addActionListener
                refreshBtn.isEnabled = false
                refreshBtn.text = "Checking..."
                Thread {
                    SubscriptionManager.getInstance().refreshStatus(token)
                    SwingUtilities.invokeLater {
                        dispose()
                    }
                }.start()
            }
            inner.add(refreshBtn)

            inner.add(Box.createVerticalStrut(6))
            val badge = JLabel("✦ Current Plan")
            badge.font = badge.font.deriveFont(Font.BOLD, 10f)
            badge.foreground = Color(80, 200, 120)
            badge.alignmentX = Component.LEFT_ALIGNMENT
            inner.add(badge)
        } else {
            val upgradeBtn = JButton("Upgrade to Ultimate")
            upgradeBtn.font = upgradeBtn.font.deriveFont(Font.BOLD, 11f)
            upgradeBtn.foreground = Color.WHITE
            upgradeBtn.background = Color(80, 140, 255)
            upgradeBtn.isBorderPainted = false
            upgradeBtn.isOpaque = true
            upgradeBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            upgradeBtn.alignmentX = Component.LEFT_ALIGNMENT
            upgradeBtn.maximumSize = Dimension(Int.MAX_VALUE, 32)
            upgradeBtn.addActionListener { openCheckout() }
            inner.add(upgradeBtn)
        }

        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun formatDate(raw: String): String {
        return try {
            val instant = Instant.parse(raw)
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (e: Exception) { raw }
    }

    private fun featureRow(text: String): JLabel {
        val lbl = JLabel(text)
        lbl.font = lbl.font.deriveFont(11f)
        lbl.alignmentX = Component.LEFT_ALIGNMENT
        lbl.border = EmptyBorder(2, 0, 2, 0)
        if (text.startsWith("⚠")) lbl.foreground = Color(180, 120, 0)
        return lbl
    }

    private fun openCheckout() {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI("https://www.springforge.dev/pricing"))
        }
    }

    companion object {
        fun show() {
            SwingUtilities.invokeLater {
                PricingDialog().isVisible = true
            }
        }
    }
}
