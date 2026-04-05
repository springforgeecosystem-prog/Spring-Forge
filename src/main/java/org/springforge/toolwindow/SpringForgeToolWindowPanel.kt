package org.springforge.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTabbedPane
import org.springforge.auth.SessionManager
import org.springforge.cicdassistant.audit.ui.AuditDashboardPanel
import org.springforge.toolwindow.panels.CICDPanel
import org.springforge.toolwindow.panels.CodeGenerationPanel
import org.springforge.toolwindow.panels.QualityAssurancePanel
import org.springforge.toolwindow.panels.RuntimeAnalysisPanel
import org.springforge.feedback.ui.FeedbackDialog
import org.springforge.subscription.SubscriptionManager
import org.springforge.subscription.SubscriptionTier
import org.springforge.subscription.ui.SubscriptionPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Main SpringForge Tool Window Panel
 * Contains all SpringForge features in a tabbed interface
 */
class SpringForgeToolWindowPanel(private val project: Project) : JPanel() {

    private val tabbedPane = JBTabbedPane()

    // Subscription header components — updated live via SubscriptionManager callback
    private var usageLabel: JLabel? = null
    private var tierBadge: JLabel? = null
    private var upgradeBtn: JButton? = null

    // Individual module panels
    private val codeGenPanel  = CodeGenerationPanel(project)
    private val cicdPanel     = CICDPanel(project)
    private val qualityPanel  = QualityAssurancePanel(project)
    private val runtimePanel  = RuntimeAnalysisPanel(project)
    private val auditPanel    = AuditDashboardPanel(project)
    private val planPanel     = SubscriptionPanel()

    init {
        layout = BorderLayout()
        setupUI()
    }

    private fun setupUI() {
        // Add header
        add(createHeader(), BorderLayout.NORTH)
        // Refresh header live when subscription status changes
        SubscriptionManager.getInstance().onStatusChanged = ::refreshSubscriptionHeader
        refreshSubscriptionHeader()

        // Add tabbed pane with all modules
        tabbedPane.apply {
            addTab("Code Gen", codeGenPanel)
            addTab("CI/CD",    cicdPanel)
            addTab("Quality",  qualityPanel)
            addTab("Runtime",  runtimePanel)
            addTab("Audit",    auditPanel)
            addTab("Plan",     planPanel)
        }

        add(tabbedPane, BorderLayout.CENTER)
    }

    private fun createHeader(): JPanel {
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )

        val titleLabel = JLabel("SpringForge Tools")
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(Font.BOLD)
        titleLabel.horizontalAlignment = SwingConstants.LEFT

        // Right side: user info + logout
        val rightPanel = JPanel()
        rightPanel.isOpaque = false
        rightPanel.layout = java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0)

        val session = SessionManager.getInstance()
        if (session.isLoggedIn) {
            // Usage label — shown for Community, hidden for Ultimate
            usageLabel = JLabel()
            usageLabel!!.font = usageLabel!!.font.deriveFont(10f)
            rightPanel.add(usageLabel!!)

            // Tier badge — shown for Ultimate, hidden for Community (clickable → Plan tab)
            tierBadge = JLabel("✦ Ultimate")
            tierBadge!!.font = tierBadge!!.font.deriveFont(Font.BOLD, 10f)
            tierBadge!!.foreground = Color(80, 200, 120)
            tierBadge!!.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            tierBadge!!.toolTipText = "Manage your plan"
            tierBadge!!.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) = switchToTab("plan")
            })
            rightPanel.add(tierBadge!!)

            // Upgrade button — shown for Community only
            upgradeBtn = JButton("✦ Upgrade")
            upgradeBtn!!.preferredSize = Dimension(85, 24)
            upgradeBtn!!.font = upgradeBtn!!.font.deriveFont(Font.BOLD, 10f)
            upgradeBtn!!.foreground = Color.WHITE
            upgradeBtn!!.background = Color(80, 140, 255)
            upgradeBtn!!.isBorderPainted = false
            upgradeBtn!!.isOpaque = true
            upgradeBtn!!.toolTipText = "Upgrade to Ultimate for unlimited requests"
            upgradeBtn!!.addActionListener { switchToTab("plan") }
            rightPanel.add(upgradeBtn!!)

            val feedbackButton = JButton("\u2606 Feedback")
            feedbackButton.preferredSize = Dimension(90, 24)
            feedbackButton.font = feedbackButton.font.deriveFont(10f)
            feedbackButton.toolTipText = "Share your feedback about SpringForge"
            feedbackButton.addActionListener {
                FeedbackDialog.showForModule(project, "springforge", "SpringForge")
            }
            rightPanel.add(feedbackButton)

            val logoutButton = JButton("Logout")
            logoutButton.preferredSize = Dimension(70, 24)
            logoutButton.font = logoutButton.font.deriveFont(10f)
            logoutButton.addActionListener {
                session.logout()
                // Replace content with login panel
                val service = project.getService(SpringForgeToolWindowService::class.java)
                val tw = service.toolWindow ?: return@addActionListener
                tw.contentManager.removeAllContents(true)
                val loginPanel = LoginRequiredPanel(project) {
                    tw.contentManager.removeAllContents(true)
                    val content = com.intellij.ui.content.ContentFactory.getInstance()
                        .createContent(SpringForgeToolWindowPanel(project), "", false)
                    tw.contentManager.addContent(content)
                }
                val content = com.intellij.ui.content.ContentFactory.getInstance()
                    .createContent(loginPanel, "", false)
                tw.contentManager.addContent(content)
            }
            rightPanel.add(logoutButton)
        } else {
            val versionLabel = JLabel("v1.0.0")
            versionLabel.foreground = JBColor.GRAY
            rightPanel.add(versionLabel)
        }

        headerPanel.add(titleLabel, BorderLayout.WEST)
        headerPanel.add(rightPanel, BorderLayout.EAST)

        return headerPanel
    }

    /** Updates the subscription UI in the header without rebuilding the whole panel. */
    private fun refreshSubscriptionHeader() {
        val sub = SubscriptionManager.getInstance().status
        val isUltimate = sub.tier == SubscriptionTier.ULTIMATE

        usageLabel?.apply {
            text = sub.usageDisplay()
            foreground = if (sub.requestsUsed >= sub.requestsLimit) Color(200, 60, 60) else JBColor.GRAY
            isVisible = !isUltimate
        }
        tierBadge?.isVisible = isUltimate
        upgradeBtn?.isVisible = !isUltimate
    }

    /**
     * Switch to a specific tab by name
     */
    fun switchToTab(tabName: String) {
        when (tabName.lowercase()) {
            "codegen", "code generation" -> tabbedPane.selectedIndex = 0
            "cicd", "ci/cd"              -> tabbedPane.selectedIndex = 1
            "quality", "qa"              -> tabbedPane.selectedIndex = 2
            "runtime", "debugger"        -> tabbedPane.selectedIndex = 3
            "audit"                      -> tabbedPane.selectedIndex = 4
            "plan", "pricing"            -> tabbedPane.selectedIndex = 5
        }
    }

    /**
     * Get reference to CI/CD panel
     */
    fun getCICDPanel(): CICDPanel = cicdPanel

    /**
     * Get reference to Code Generation panel
     */
    fun getCodeGenPanel(): CodeGenerationPanel = codeGenPanel

    /**
     * Get reference to Quality Assurance panel
     */
    fun getQualityPanel(): QualityAssurancePanel = qualityPanel

    /**
     * Get reference to Runtime Debugger panel
     */
    fun getRuntimePanel(): RuntimeAnalysisPanel = runtimePanel

    fun getAuditPanel(): AuditDashboardPanel = auditPanel
}
