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
 * Embedded subscription/plan panel shown as the "Plan" tab in the tool window.
 * Refreshes itself automatically via SubscriptionManager.onStatusChanged.
 */
class SubscriptionPanel : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        rebuild()
        // Chain onto any existing callback (tool window header may already be registered)
        val existing = SubscriptionManager.getInstance().onStatusChanged
        SubscriptionManager.getInstance().onStatusChanged = {
            existing?.invoke()
            SwingUtilities.invokeLater { rebuild() }
        }
    }

    private fun rebuild() {
        removeAll()
        add(buildContent(), BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun buildContent(): JPanel {
        val root = JPanel(BorderLayout(0, 0))
        root.isOpaque = false
        root.border = EmptyBorder(20, 20, 20, 20)

        // ── Title row ──────────────────────────────────────────────
        val titleLabel = JLabel("Subscription & Pricing")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 15f)
        titleLabel.border = EmptyBorder(0, 0, 16, 0)
        root.add(titleLabel, BorderLayout.NORTH)

        // ── Plan cards ─────────────────────────────────────────────
        val cardsPanel = JPanel(GridLayout(1, 2, 16, 0))
        cardsPanel.isOpaque = false

        val currentTier = SubscriptionManager.getInstance().status.tier
        cardsPanel.add(buildCommunityCard(currentTier))
        cardsPanel.add(buildUltimateCard(currentTier))
        root.add(cardsPanel, BorderLayout.CENTER)

        // ── Footer note ────────────────────────────────────────────
        val footer = JLabel("Payments and billing are managed securely at springforge.dev")
        footer.font = footer.font.deriveFont(10f)
        footer.foreground = JBColor.GRAY
        footer.horizontalAlignment = SwingConstants.CENTER
        footer.border = EmptyBorder(14, 0, 0, 0)
        root.add(footer, BorderLayout.SOUTH)

        return root
    }

    private fun buildCommunityCard(current: SubscriptionTier): JPanel {
        val isActive = current == SubscriptionTier.COMMUNITY
        val card = JPanel(BorderLayout())
        card.background = UIManager.getColor("Panel.background")
        card.border = if (isActive) LineBorder(Color(80, 140, 255), 2, true)
                      else          LineBorder(JBColor.border(), 1, true)

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.isOpaque = false
        inner.border = EmptyBorder(16, 16, 16, 16)

        val name = JLabel("Community")
        name.font = name.font.deriveFont(Font.BOLD, 14f)
        name.alignmentX = Component.LEFT_ALIGNMENT

        val price = JLabel("Free")
        price.font = price.font.deriveFont(Font.BOLD, 24f)
        price.alignmentX = Component.LEFT_ALIGNMENT

        val sep = JSeparator()
        sep.alignmentX = Component.LEFT_ALIGNMENT
        sep.maximumSize = Dimension(Int.MAX_VALUE, 1)

        inner.add(name)
        inner.add(Box.createVerticalStrut(4))
        inner.add(price)
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

        inner.add(Box.createVerticalStrut(12))

        if (isActive) {
            val sub = SubscriptionManager.getInstance().status
            val usageBar = JLabel("${sub.requestsUsed} / ${sub.requestsLimit} requests used this month")
            usageBar.font = usageBar.font.deriveFont(10f)
            usageBar.foreground = if (sub.requestsUsed >= sub.requestsLimit) Color(200, 60, 60) else JBColor.GRAY
            usageBar.alignmentX = Component.LEFT_ALIGNMENT
            inner.add(usageBar)

            inner.add(Box.createVerticalStrut(4))
            val badge = JLabel("✦ Current Plan")
            badge.font = badge.font.deriveFont(Font.BOLD, 10f)
            badge.foreground = Color(80, 140, 255)
            badge.alignmentX = Component.LEFT_ALIGNMENT
            inner.add(badge)
        }

        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun buildUltimateCard(current: SubscriptionTier): JPanel {
        val isActive = current == SubscriptionTier.ULTIMATE
        val card = JPanel(BorderLayout())
        card.background = UIManager.getColor("Panel.background")
        card.border = if (isActive) LineBorder(Color(80, 200, 120), 2, true)
                      else          LineBorder(JBColor.border(), 1, true)

        val inner = JPanel()
        inner.layout = BoxLayout(inner, BoxLayout.Y_AXIS)
        inner.isOpaque = false
        inner.border = EmptyBorder(16, 16, 16, 16)

        val name = JLabel("Ultimate")
        name.font = name.font.deriveFont(Font.BOLD, 14f)
        name.alignmentX = Component.LEFT_ALIGNMENT

        val priceRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        priceRow.isOpaque = false
        priceRow.alignmentX = Component.LEFT_ALIGNMENT
        val price = JLabel("\$9")
        price.font = price.font.deriveFont(Font.BOLD, 24f)
        val per = JLabel("/month")
        per.font = per.font.deriveFont(12f)
        per.foreground = JBColor.GRAY
        priceRow.add(price)
        priceRow.add(per)

        val sep = JSeparator()
        sep.alignmentX = Component.LEFT_ALIGNMENT
        sep.maximumSize = Dimension(Int.MAX_VALUE, 1)

        inner.add(name)
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

        if (isActive) {
            val sub = SubscriptionManager.getInstance().status

            if (sub.expiresAt.isNotBlank()) {
                // Pending cancellation
                val cancelNote = JLabel("Cancels on ${formatDate(sub.expiresAt)}")
                cancelNote.font = cancelNote.font.deriveFont(Font.BOLD, 11f)
                cancelNote.foreground = Color(200, 150, 0)
                cancelNote.alignmentX = Component.LEFT_ALIGNMENT
                inner.add(cancelNote)
            } else {
                // Active — show Cancel Plan
                val cancelBtn = JButton("Cancel Plan")
                cancelBtn.font = cancelBtn.font.deriveFont(11f)
                cancelBtn.foreground = Color(200, 60, 60)
                cancelBtn.isOpaque = false
                cancelBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                cancelBtn.alignmentX = Component.LEFT_ALIGNMENT
                cancelBtn.maximumSize = Dimension(Int.MAX_VALUE, 30)
                cancelBtn.toolTipText = "Manage or cancel your subscription at springforge.dev"
                cancelBtn.addActionListener { openUrl("https://www.springforge.dev/pricing") }
                inner.add(cancelBtn)
            }

            inner.add(Box.createVerticalStrut(6))

            val refreshBtn = JButton("Refresh Plan")
            refreshBtn.font = refreshBtn.font.deriveFont(11f)
            refreshBtn.alignmentX = Component.LEFT_ALIGNMENT
            refreshBtn.maximumSize = Dimension(Int.MAX_VALUE, 30)
            refreshBtn.toolTipText = "Re-check your subscription status from the server"
            refreshBtn.addActionListener {
                val token = SessionManager.getInstance().token ?: return@addActionListener
                refreshBtn.isEnabled = false
                refreshBtn.text = "Checking..."
                Thread {
                    SubscriptionManager.getInstance().refreshStatus(token)
                    // rebuild() is triggered automatically via onStatusChanged
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
            upgradeBtn.font = upgradeBtn.font.deriveFont(Font.BOLD, 12f)
            upgradeBtn.foreground = Color.WHITE
            upgradeBtn.background = Color(80, 140, 255)
            upgradeBtn.isBorderPainted = false
            upgradeBtn.isOpaque = true
            upgradeBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            upgradeBtn.alignmentX = Component.LEFT_ALIGNMENT
            upgradeBtn.maximumSize = Dimension(Int.MAX_VALUE, 34)
            upgradeBtn.addActionListener { openUrl("https://www.springforge.dev/pricing") }
            inner.add(upgradeBtn)
        }

        card.add(inner, BorderLayout.CENTER)
        return card
    }

    private fun featureRow(text: String): JLabel {
        val lbl = JLabel(text)
        lbl.font = lbl.font.deriveFont(11f)
        lbl.alignmentX = Component.LEFT_ALIGNMENT
        lbl.border = EmptyBorder(2, 0, 2, 0)
        if (text.startsWith("⚠")) lbl.foreground = Color(180, 120, 0)
        return lbl
    }

    private fun formatDate(raw: String): String {
        return try {
            val instant = Instant.parse(raw)
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } catch (e: Exception) { raw }
    }

    private fun openUrl(url: String) {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}
