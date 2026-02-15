package org.springforge.toolwindow.panels

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.springforge.qualityassurance.actions.AnalyzeQualityAction
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Quality Assurance Panel for SpringForge Tool Window
 */
class QualityAssurancePanel(private val project: Project) : JPanel() {

    private val resultsArea = JBTextArea()

    init {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        setupUI()
    }

    private fun setupUI() {
        val contentPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0

        // Title
        val titleLabel = JBLabel("Code Quality Analysis")
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)
        contentPanel.add(titleLabel, gbc)

        gbc.gridy++
        contentPanel.add(JSeparator(), gbc)

        // Description
        gbc.gridy++
        val descLabel = JBLabel(
            "<html>Detect architecture violations and<br>" +
            "anti-patterns using ML-powered analysis.</html>"
        )
        descLabel.foreground = JBColor.GRAY
        contentPanel.add(descLabel, gbc)

        gbc.gridy++
        contentPanel.add(Box.createVerticalStrut(15), gbc)

        // Analyze button
        gbc.gridy++
        val analyzeButton = createActionButton(
            "Analyze Code Quality",
            "Run ML-based architecture violation detection"
        ) {
            val action = AnalyzeQualityAction()
            val event = createActionEvent()
            action.actionPerformed(event)
        }
        contentPanel.add(analyzeButton, gbc)

        // Add filler
        gbc.gridy++
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        contentPanel.add(Box.createVerticalGlue(), gbc)

        // Results section
        gbc.gridy++
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        contentPanel.add(createSeparator("Analysis Results"), gbc)

        // Wrap content in scroll pane
        val scrollPane = JBScrollPane(contentPanel)
        scrollPane.preferredSize = Dimension(400, 300)

        // Results area
        resultsArea.isEditable = false
        resultsArea.lineWrap = true
        resultsArea.wrapStyleWord = true
        resultsArea.text = "No analysis results yet.\n\nClick 'Analyze Code Quality' to start."
        val resultsScrollPane = JBScrollPane(resultsArea)
        resultsScrollPane.preferredSize = Dimension(400, 300)

        add(scrollPane, BorderLayout.NORTH)
        add(resultsScrollPane, BorderLayout.CENTER)
    }

    private fun createSeparator(title: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createEmptyBorder(10, 0, 5, 0)

        val label = JBLabel(title)
        label.font = label.font.deriveFont(java.awt.Font.BOLD)
        panel.add(label, BorderLayout.WEST)

        val separator = JSeparator()
        panel.add(separator, BorderLayout.SOUTH)

        return panel
    }

    private fun createActionButton(
        title: String,
        description: String,
        action: () -> Unit
    ): JPanel {
        val panel = JPanel(BorderLayout(10, 5))
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        )

        val contentPanel = JPanel(BorderLayout(5, 2))

        val titleLabel = JBLabel(title)
        titleLabel.font = titleLabel.font.deriveFont(java.awt.Font.BOLD)

        val descLabel = JBLabel("<html><small>$description</small></html>")
        descLabel.foreground = JBColor.GRAY

        contentPanel.add(titleLabel, BorderLayout.NORTH)
        contentPanel.add(descLabel, BorderLayout.CENTER)

        val button = JButton("Analyze")
        button.addActionListener { action() }

        panel.add(contentPanel, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)

        return panel
    }

    fun showResults(results: String) {
        resultsArea.text = results
    }

    private fun createActionEvent(): com.intellij.openapi.actionSystem.AnActionEvent {
        val dataContext = com.intellij.openapi.actionSystem.DataContext { dataId ->
            when (dataId) {
                com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                else -> null
            }
        }

        return com.intellij.openapi.actionSystem.AnActionEvent(
            null,
            dataContext,
            com.intellij.openapi.actionSystem.ActionPlaces.UNKNOWN,
            com.intellij.openapi.actionSystem.Presentation(),
            com.intellij.openapi.actionSystem.ActionManager.getInstance(),
            0
        )
    }
}
