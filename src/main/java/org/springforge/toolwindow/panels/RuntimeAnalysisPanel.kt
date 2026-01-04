package org.springforge.toolwindow.panels

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.springforge.runtimeanalysis.service.RuntimeAnalysisService
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

class RuntimeAnalysisPanel(private val project: Project) : JPanel() {

    private val inputArea = JTextArea()
    private val outputArea = JTextArea()

    init {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        setupUI()
    }

    private fun setupUI() {

        // 🔹 Title
        val title = JBLabel("Runtime Error Analysis")
        title.font = title.font.deriveFont(Font.BOLD, 14f)

        // 🔹 Button
        val analyzeButton = JButton("Analyze Error")

        analyzeButton.addActionListener {
            val errorText = inputArea.text.trim()

            if (errorText.isBlank()) {
                Messages.showWarningDialog(
                    project,
                    "Please paste an error stacktrace in the input area.",
                    "SpringForge"
                )
                return@addActionListener
            }

            analyzeError(errorText)
        }

        // 🔹 Input Area
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.border = BorderFactory.createLineBorder(JBColor.border())
        inputArea.rows = 6

        // 🔹 Output Area
        outputArea.isEditable = false
        outputArea.lineWrap = true
        outputArea.wrapStyleWord = true
        outputArea.border = BorderFactory.createLineBorder(JBColor.border())

        // 🔹 Top Panel
        val topPanel = JPanel(BorderLayout(5, 5))
        topPanel.add(title, BorderLayout.WEST)
        topPanel.add(analyzeButton, BorderLayout.EAST)

        // 🔹 Middle Panel (Input)
        val inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.preferredSize = java.awt.Dimension(inputScrollPane.preferredSize.width, 150)
        
        val inputPanel = JPanel(BorderLayout())
        inputPanel.add(JBLabel("Error Input (Paste Here)"), BorderLayout.NORTH)
        inputPanel.add(inputScrollPane, BorderLayout.CENTER)

        // 🔹 Bottom Panel (Output)
        val outputPanel = JPanel(BorderLayout())
        outputPanel.add(JBLabel("Analysis Result"), BorderLayout.NORTH)
        outputPanel.add(JBScrollPane(outputArea), BorderLayout.CENTER)

        // 🔹 Split between input and output
        val centerPanel = JPanel(BorderLayout(0, 10))
        centerPanel.add(inputPanel, BorderLayout.NORTH)
        centerPanel.add(outputPanel, BorderLayout.CENTER)

        add(topPanel, BorderLayout.NORTH)
        add(centerPanel, BorderLayout.CENTER)
    }

    private fun analyzeError(errorText: String) {
        outputArea.text = "⏳ Analyzing error…"

        RuntimeAnalysisService.analyze(
            project,
            errorText,
            onResult = { result ->
                outputArea.text = result
            },
            onError = { err ->
                outputArea.text = "❌ $err"
            }
        )
    }
}
