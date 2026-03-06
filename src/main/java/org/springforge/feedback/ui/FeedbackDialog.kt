package org.springforge.feedback.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.springforge.feedback.FeedbackService
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Feedback dialog with 5-star rating and a comment box.
 * Shown after a module operation completes successfully.
 */
class FeedbackDialog(
    private val project: Project?,
    private val moduleName: String,
    private val moduleDisplayName: String
) : DialogWrapper(project) {

    private var selectedRating = 0
    private val starLabels = Array(5) { createStarLabel(it + 1) }
    private val commentArea = JTextArea(4, 30).apply {
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(6)
        )
    }
    private val statusLabel = JBLabel("").apply { isVisible = false }

    init {
        title = "Rate Your Experience"
        setOKButtonText("Submit Feedback")
        setCancelButtonText("Skip")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(12)

        // Module label
        val moduleLabel = JBLabel("How was your experience with $moduleDisplayName?")
        moduleLabel.font = moduleLabel.font.deriveFont(Font.BOLD, 14f)
        moduleLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(moduleLabel)

        panel.add(Box.createVerticalStrut(12))

        // Star rating row
        val starsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        starsPanel.alignmentX = Component.LEFT_ALIGNMENT
        val ratingLabel = JBLabel("Rating: ")
        starsPanel.add(ratingLabel)
        starLabels.forEach { starsPanel.add(it) }
        panel.add(starsPanel)

        panel.add(Box.createVerticalStrut(12))

        // Comment label
        val commentLabel = JBLabel("Comments (optional):")
        commentLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(commentLabel)
        panel.add(Box.createVerticalStrut(4))

        // Comment area
        val scrollPane = JBScrollPane(commentArea)
        scrollPane.alignmentX = Component.LEFT_ALIGNMENT
        scrollPane.preferredSize = Dimension(350, 100)
        panel.add(scrollPane)

        panel.add(Box.createVerticalStrut(8))

        // Status label
        statusLabel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(statusLabel)

        return panel
    }

    private fun createStarLabel(starNumber: Int): JLabel {
        val label = JLabel(STAR_EMPTY)
        label.font = label.font.deriveFont(24f)
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                selectedRating = starNumber
                updateStars()
            }

            override fun mouseEntered(e: MouseEvent?) {
                highlightStars(starNumber)
            }

            override fun mouseExited(e: MouseEvent?) {
                updateStars()
            }
        })
        return label
    }

    private fun highlightStars(upTo: Int) {
        for (i in starLabels.indices) {
            starLabels[i].text = if (i < upTo) STAR_FILLED else STAR_EMPTY
            starLabels[i].foreground = if (i < upTo) STAR_COLOR else JBColor.GRAY
        }
    }

    private fun updateStars() {
        highlightStars(selectedRating)
    }

    override fun doOKAction() {
        if (selectedRating == 0) {
            statusLabel.text = "Please select a rating (1-5 stars)"
            statusLabel.foreground = JBColor.RED
            statusLabel.isVisible = true
            return
        }

        isOKActionEnabled = false
        statusLabel.text = "Submitting..."
        statusLabel.foreground = JBColor.GRAY
        statusLabel.isVisible = true

        Thread {
            try {
                FeedbackService.submitFeedback(
                    rating = selectedRating,
                    comment = commentArea.text.trim(),
                    module = moduleName
                )
                SwingUtilities.invokeLater { close(OK_EXIT_CODE) }
            } catch (ex: Exception) {
                SwingUtilities.invokeLater {
                    isOKActionEnabled = true
                    statusLabel.text = "Failed: ${ex.message}"
                    statusLabel.foreground = JBColor.RED
                    statusLabel.isVisible = true
                }
            }
        }.start()
    }

    companion object {
        private const val STAR_FILLED = "\u2605"  // ★
        private const val STAR_EMPTY = "\u2606"   // ☆
        private val STAR_COLOR = JBColor(Color(0xFFB400), Color(0xFFD54F))

        /**
         * Show the feedback dialog for a module.
         * This is fire-and-forget — if the user skips, nothing happens.
         */
        fun showForModule(project: Project?, moduleName: String, moduleDisplayName: String) {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                FeedbackDialog(project, moduleName, moduleDisplayName).show()
            }
        }
    }
}
