package org.springforge.toolwindow.panels

import com.google.gson.Gson
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.springforge.runtimeanalysis.collector.ErrorCollector
import org.springforge.runtimeanalysis.network.NetworkClient
import org.springforge.runtimeanalysis.ui.SpringForgeNotifier
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class RuntimeDebuggerPanel(private val project: Project) : JPanel() {

    private val log = Logger.getInstance(RuntimeDebuggerPanel::class.java)

    private val resultArea = JBTextArea()
    private val analyzeButton = JButton("Analyze Selected Error")
    private val statusLabel = JBLabel("Select an error in console/editor and click Analyze")

    init {
        layout = BorderLayout(10, 10)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        setupUI()
    }

    private fun setupUI() {
        // Top
        val topPanel = JPanel(BorderLayout())
        val title = JBLabel("Runtime Error Analyzer")
        title.font = title.font.deriveFont(14f).deriveFont(java.awt.Font.BOLD)

        analyzeButton.addActionListener { analyzeSelectedError() }

        topPanel.add(title, BorderLayout.WEST)
        topPanel.add(analyzeButton, BorderLayout.EAST)

        // Center
        resultArea.isEditable = false
        resultArea.lineWrap = true
        resultArea.wrapStyleWord = true
        resultArea.font = java.awt.Font("JetBrains Mono", java.awt.Font.PLAIN, 12)
        resultArea.text =
                "No analysis yet.\n\n" +
                        "Steps:\n" +
                        "1. Select a stacktrace in Run console or editor\n" +
                        "2. Click 'Analyze Selected Error'\n" +
                        "3. AI analysis will appear here"

        val scrollPane = JBScrollPane(resultArea)

        // Bottom
        statusLabel.foreground = JBColor.GRAY

        add(topPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun analyzeSelectedError() {
        val dataContext = DataManager.getInstance().dataContext
        val editor = CommonDataKeys.EDITOR.getData(dataContext)

        val selectedText = editor?.selectionModel?.selectedText

        if (selectedText.isNullOrBlank()) {
            Messages.showWarningDialog(
                    project,
                    "Please select a stacktrace from console or editor.",
                    "SpringForge"
            )
            return
        }

        analyzeButton.isEnabled = false
        statusLabel.text = "Analyzing error with SpringForge AI…"
        statusLabel.foreground = JBColor(0x7A7A7A, 0x7A7A7A)
        resultArea.text = "⏳ Analyzing selected error...\n\nPlease wait."

        SpringForgeNotifier.info(project, "Analyzing error with SpringForge AI…")

        val payload = ErrorCollector.buildErrorPayload(selectedText, project)
        val json = Gson().toJson(payload)

        ProgressManager.getInstance().run(object :
                Task.Backgroundable(project, "SpringForge Runtime Analysis", false) {

            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val response = NetworkClient.analyzeError(json)

                    ApplicationManager.getApplication().invokeLater {
                        resultArea.text = "✓ Analysis Result\n\n${response.answer}"
                        resultArea.caretPosition = 0
                        statusLabel.text = "✓ Analysis complete"
                        statusLabel.foreground = JBColor(0x6A8759, 0x6A8759)
                    }

                } catch (ex: Exception) {
                    log.error("Runtime analysis failed", ex)

                    ApplicationManager.getApplication().invokeLater {
                        resultArea.text =
                                "❌ Analysis Failed\n\n${ex.message ?: "Unknown error"}"
                        statusLabel.text = "✗ Analysis failed"
                        statusLabel.foreground = JBColor.RED
                        SpringForgeNotifier.error(project, "Analysis failed")
                    }
                } finally {
                    ApplicationManager.getApplication().invokeLater {
                        analyzeButton.isEnabled = true
                    }
                }
            }
        })
    }
}
