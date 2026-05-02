package org.springforge.runtimeanalysis.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class AnalysisResultDialog(
        project: Project,
        private val errorSummary: String,
        private val rootCause: String,
        private val suggestedFix: String,
        private val codeSnippet: String,
        private val references: List<Reference>,
        private val notes: String?
) : DialogWrapper(project) {

    init {
        title = "SpringForge Analysis"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = JBUI.Borders.empty(0)

        // Create content panel with sections
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(16)

        // Add sections
        contentPanel.add(createErrorSection())
        contentPanel.add(Box.createVerticalStrut(20))
        contentPanel.add(createRootCauseSection())
        contentPanel.add(Box.createVerticalStrut(20))
        contentPanel.add(createSuggestedFixSection())
        contentPanel.add(Box.createVerticalStrut(20))
        contentPanel.add(createCodeSection())
        contentPanel.add(Box.createVerticalStrut(20))
        contentPanel.add(createReferencesSection())

        if (!notes.isNullOrBlank()) {
            contentPanel.add(Box.createVerticalStrut(20))
            contentPanel.add(createNotesSection())
        }

        // Wrap in scroll pane
        val scrollPane = JBScrollPane(contentPanel)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.preferredSize = Dimension(900, 650)

        mainPanel.add(scrollPane, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createErrorSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = JBUI.Borders.empty()

        val iconLabel = JLabel("⚠️")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("Error")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)
        titleLabel.foreground = JBColor.RED

        headerPanel.add(iconLabel)
        headerPanel.add(titleLabel)

        // Error content
        val errorLabel = createWrappingLabel(errorSummary)
        errorLabel.border = JBUI.Borders.emptyTop(8)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(errorLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createRootCauseSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = JBUI.Borders.empty()

        val iconLabel = JLabel("🔍")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("Root Cause")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)

        headerPanel.add(iconLabel)
        headerPanel.add(titleLabel)

        // Root cause content
        val causeLabel = createWrappingLabel(rootCause)
        causeLabel.border = JBUI.Borders.emptyTop(8)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(causeLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createSuggestedFixSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = JBUI.Borders.empty()

        val iconLabel = JLabel("💡")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("Suggested Fix")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)
        titleLabel.foreground = JBColor(Color(76, 175, 80), Color(129, 199, 132))

        headerPanel.add(iconLabel)
        headerPanel.add(titleLabel)

        // Fix content
        val fixLabel = createWrappingLabel(suggestedFix)
        fixLabel.border = JBUI.Borders.emptyTop(8)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(fixLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createCodeSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title with copy button
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty()

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        leftHeader.border = JBUI.Borders.empty()

        val iconLabel = JLabel("📝")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("Code Solution")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)

        leftHeader.add(iconLabel)
        leftHeader.add(titleLabel)

        // Copy button
        val copyButton = JButton("Copy Code")
        copyButton.font = Font(copyButton.font.name, Font.PLAIN, 12)
        copyButton.addActionListener {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(codeSnippet), null)
            copyButton.text = "✓ Copied!"
            Timer(2000) {
                SwingUtilities.invokeLater {
                    copyButton.text = "Copy Code"
                }
            }.apply {
                isRepeats = false
                start()
            }
        }

        headerPanel.add(leftHeader, BorderLayout.WEST)
        headerPanel.add(copyButton, BorderLayout.EAST)

        // Code text area
        val codeArea = JTextArea(codeSnippet)
        codeArea.isEditable = false
        codeArea.lineWrap = false
        codeArea.font = Font("JetBrains Mono", Font.PLAIN, 13)
        codeArea.background = UIUtil.getPanelBackground().darker()
        codeArea.foreground = JBColor(Color(169, 183, 198), Color(169, 183, 198))
        codeArea.border = JBUI.Borders.empty(12)
        codeArea.caretPosition = 0

        val scrollPane = JBScrollPane(codeArea)
        scrollPane.border = BorderFactory.createLineBorder(JBColor.border(), 1)
        scrollPane.preferredSize = Dimension(800, 200)

        val codePanel = JPanel(BorderLayout())
        codePanel.border = JBUI.Borders.emptyTop(8)
        codePanel.add(scrollPane, BorderLayout.CENTER)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(codePanel, BorderLayout.CENTER)

        return panel
    }

    private fun createReferencesSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = JBUI.Borders.empty()

        val iconLabel = JLabel("🔗")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("References")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)

        headerPanel.add(iconLabel)
        headerPanel.add(titleLabel)

        // References list
        val refsPanel = JPanel()
        refsPanel.layout = BoxLayout(refsPanel, BoxLayout.Y_AXIS)
        refsPanel.border = JBUI.Borders.emptyTop(8)
        refsPanel.alignmentX = Component.LEFT_ALIGNMENT

        references.forEach { ref ->
            refsPanel.add(createReferenceLink(ref))
            refsPanel.add(Box.createVerticalStrut(6))
        }

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(refsPanel, BorderLayout.CENTER)

        return panel
    }

    private fun createNotesSection(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        // Icon and title
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.border = JBUI.Borders.empty()

        val iconLabel = JLabel("📌")
        iconLabel.font = Font(iconLabel.font.name, Font.PLAIN, 20)
        iconLabel.border = JBUI.Borders.emptyRight(8)

        val titleLabel = JBLabel("Additional Notes")
        titleLabel.font = Font(titleLabel.font.name, Font.BOLD, 14)

        headerPanel.add(iconLabel)
        headerPanel.add(titleLabel)

        // Notes content
        val notesLabel = createWrappingLabel(notes ?: "")
        notesLabel.border = JBUI.Borders.emptyTop(8)
        notesLabel.foreground = JBColor.GRAY

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(notesLabel, BorderLayout.CENTER)

        return panel
    }

    private fun createReferenceLink(ref: Reference): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        panel.border = JBUI.Borders.empty()
        panel.alignmentX = Component.LEFT_ALIGNMENT

        val bulletLabel = JLabel("•")
        bulletLabel.border = JBUI.Borders.emptyRight(8)
        bulletLabel.foreground = JBColor.GRAY

        val linkLabel = JBLabel("<html><a href='${ref.url}'>${ref.title}</a></html>")
        linkLabel.foreground = JBColor(Color(33, 150, 243), Color(100, 181, 246))
        linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        linkLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                try {
                    Desktop.getDesktop().browse(java.net.URI(ref.url))
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        })

        panel.add(bulletLabel)
        panel.add(linkLabel)

        return panel
    }

    private fun createWrappingLabel(text: String): JLabel {
        val label = JLabel("<html><body style='width: 800px'>$text</body></html>")
        label.font = Font(label.font.name, Font.PLAIN, 13)
        label.foreground = UIUtil.getLabelForeground()
        return label
    }

    override fun createActions() = arrayOf(okAction)
}
