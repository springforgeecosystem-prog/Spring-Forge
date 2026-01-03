package org.springforge.toolwindow.panels

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.springforge.runtimeanalysis.actions.StartDebuggerAction
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Runtime Debugger Panel for SpringForge Tool Window
 */
class RuntimeDebuggerPanel(private val project: Project) : JPanel() {

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
        val titleLabel = JBLabel("Runtime Debugger")
        titleLabel.font = titleLabel.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)
        contentPanel.add(titleLabel, gbc)

        gbc.gridy++
        contentPanel.add(JSeparator(), gbc)

        // Description
        gbc.gridy++
        val descLabel = JBLabel(
            "<html>Advanced runtime analysis and<br>" +
            "debugging tools for Spring Boot applications.</html>"
        )
        descLabel.foreground = JBColor.GRAY
        contentPanel.add(descLabel, gbc)

        gbc.gridy++
        contentPanel.add(Box.createVerticalStrut(15), gbc)

        // Start Debugger button
        gbc.gridy++
        val debuggerButton = createActionButton(
            "Start Runtime Debugger",
            "Launch the runtime analysis debugger module"
        ) {
            val action = StartDebuggerAction()
            val event = createActionEvent()
            action.actionPerformed(event)
        }
        contentPanel.add(debuggerButton, gbc)

        // Add filler
        gbc.gridy++
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        contentPanel.add(Box.createVerticalGlue(), gbc)

        // Info panel
        gbc.gridy++
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        contentPanel.add(createInfoPanel(), gbc)

        // Wrap in scroll pane
        val scrollPane = JBScrollPane(contentPanel)
        add(scrollPane, BorderLayout.CENTER)
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

        val button = JButton("Start")
        button.addActionListener { action() }

        panel.add(contentPanel, BorderLayout.CENTER)
        panel.add(button, BorderLayout.EAST)

        return panel
    }

    private fun createInfoPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
            BorderFactory.createEmptyBorder(10, 5, 5, 5)
        )

        val infoLabel = JBLabel(
            "<html><small>" +
            "<b>Features:</b><br>" +
            "• Runtime performance monitoring<br>" +
            "• Memory leak detection<br>" +
            "• Request tracing<br>" +
            "• Metrics collection<br>" +
            "</small></html>"
        )
        infoLabel.foreground = JBColor.GRAY

        panel.add(infoLabel, BorderLayout.CENTER)

        return panel
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
