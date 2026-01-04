package org.springforge.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBTabbedPane
import org.springforge.toolwindow.panels.CICDPanel
import org.springforge.toolwindow.panels.CodeGenerationPanel
import org.springforge.toolwindow.panels.QualityAssurancePanel
import org.springforge.toolwindow.panels.RuntimeAnalysisPanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Main SpringForge Tool Window Panel
 * Contains all SpringForge features in a tabbed interface
 */
class SpringForgeToolWindowPanel(private val project: Project) : JPanel() {

    private val tabbedPane = JBTabbedPane()

    // Individual module panels
    private val codeGenPanel = CodeGenerationPanel(project)
    private val cicdPanel = CICDPanel(project)
    private val qualityPanel = QualityAssurancePanel(project)
    private val runtimePanel = RuntimeAnalysisPanel(project)

    init {
        layout = BorderLayout()
        setupUI()
    }

    private fun setupUI() {
        // Add header
        add(createHeader(), BorderLayout.NORTH)

        // Add tabbed pane with all modules
        tabbedPane.apply {
            addTab("Code Gen", codeGenPanel)
            addTab("CI/CD", cicdPanel)
            addTab("Quality", qualityPanel)
            addTab("Runtime", runtimePanel)
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
        titleLabel.font = titleLabel.font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
        titleLabel.horizontalAlignment = SwingConstants.LEFT

        val versionLabel = JLabel("v1.0.0")
        versionLabel.foreground = JBColor.GRAY
        versionLabel.horizontalAlignment = SwingConstants.RIGHT

        headerPanel.add(titleLabel, BorderLayout.WEST)
        headerPanel.add(versionLabel, BorderLayout.EAST)

        return headerPanel
    }

    /**
     * Switch to a specific tab by name
     */
    fun switchToTab(tabName: String) {
        when (tabName.lowercase()) {
            "codegen", "code generation" -> tabbedPane.selectedIndex = 0
            "cicd", "ci/cd" -> tabbedPane.selectedIndex = 1
            "quality", "qa" -> tabbedPane.selectedIndex = 2
            "runtime", "debugger" -> tabbedPane.selectedIndex = 3
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
}
