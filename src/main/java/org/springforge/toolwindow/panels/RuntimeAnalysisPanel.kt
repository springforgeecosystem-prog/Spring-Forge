package org.springforge.toolwindow.panels

import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.springforge.runtimeanalysis.service.RuntimeAnalysisService
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder

class RuntimeAnalysisPanel(private val project: Project) : JPanel() {

    private val inputArea = JTextArea()
    private val resultPanel = object : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(v: Rectangle, o: Int, d: Int): Int = 16
        override fun getScrollableBlockIncrement(v: Rectangle, o: Int, d: Int): Int = 16
        override fun getScrollableTracksViewportWidth(): Boolean = true
        override fun getScrollableTracksViewportHeight(): Boolean = false
    }
    private val resultScroll = JBScrollPane(resultPanel).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    init {
        layout = BorderLayout(0, 0)
        setupUI()
    }

    // ─────────────────────────────────────────────────────────────
    // UI SETUP
    // ─────────────────────────────────────────────────────────────

    private fun setupUI() {
        val topBar = JPanel(BorderLayout(8, 0))
        topBar.border = EmptyBorder(10, 12, 8, 12)

        val title = JBLabel("Runtime Error Analysis")
        title.font = title.font.deriveFont(Font.BOLD, 14f)

        val analyzeBtn = JButton("Analyze Error")
        analyzeBtn.addActionListener {
            val text = inputArea.text.trim()
            val validationError = validateStacktrace(text)
            if (validationError != null) {
                showValidationError(validationError)
                return@addActionListener
            }
            analyzeError(text)
        }

        topBar.add(title, BorderLayout.WEST)
        topBar.add(analyzeBtn, BorderLayout.EAST)

        inputArea.font = monoFont(12)
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.rows = 6
        inputArea.border = EmptyBorder(8, 10, 8, 10)

        val inputScroll = JBScrollPane(inputArea)
        inputScroll.border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JBColor.border()), " Paste Error / Stacktrace "
        )
        inputScroll.preferredSize = Dimension(0, 130)

        val inputWrapper = JPanel(BorderLayout())
        inputWrapper.border = EmptyBorder(0, 12, 6, 12)
        inputWrapper.add(inputScroll)

        resultPanel.layout = BoxLayout(resultPanel, BoxLayout.Y_AXIS)
        resultPanel.border = EmptyBorder(8, 8, 8, 8)
        resultScroll.border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JBColor.border()), " Analysis Result "
        )

        val resultWrapper = JPanel(BorderLayout())
        resultWrapper.border = EmptyBorder(0, 12, 12, 12)
        resultWrapper.add(resultScroll)

        val center = JPanel(BorderLayout(0, 0))
        center.add(inputWrapper, BorderLayout.NORTH)
        center.add(resultWrapper, BorderLayout.CENTER)

        add(topBar, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns null if the input looks like a valid error of any kind,
     * or a user-friendly error message string if it does not.
     *
     * Accepts:
     *   - Runtime stacktraces:  SomeException: message / at com.example.Class.method(File.java:42)
     *   - Compile errors:       java: cannot find symbol / error: ... / File.java:42: error: ...
     *   - Build errors:         BUILD FAILED / FAILURE / compilation error
     *   - Gradle/Maven errors:  > Task :compileJava FAILED / [ERROR]
     *   - File-path errors:     path/File.java:42:10  (file:line:col format)
     */
    private fun validateStacktrace(text: String): String? {
        if (text.isBlank()) {
            return "Empty input — please paste an error or stacktrace."
        }

        // Too short to be meaningful
        if (text.length < 10) {
            return "Input is too short. Please paste the full error output from your IDE console or logs."
        }

        // Runtime exception / stack trace patterns
        val hasExceptionLine = Regex(
            """([A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*\.)*[A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*Exception[:\s]|""" +
            """([A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*\.)*[A-Za-z_${'$'}][A-Za-z0-9_${'$'}]*Error[:\s]|""" +
            """Caused by:|""" +
            """Exception in thread"""
        ).containsMatchIn(text)

        val hasStackFrame = Regex(
            """^\s*at\s+[\w${'$'}]+[\w${'$'}.]+\([\w${'$'}]+\.(?:java|kt):\d+\)""",
            RegexOption.MULTILINE
        ).containsMatchIn(text)

        // Compile error patterns (javac / IDE compiler output)
        val hasCompileError = Regex(
            """java:\s+(cannot find symbol|incompatible types|unreported exception|method .+ cannot be applied)|""" +
            """error:\s+.{5,}|""" +
            """cannot find symbol|""" +
            """symbol:\s+(method|variable|class)\s+""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        // File-path:line:col error format (e.g. File.java:42:10 or path/File.java:42)
        val hasFileLineError = Regex(
            """[\w/\\]+\.(?:java|kt|xml|gradle|properties):\d+""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        // Build tool errors (Gradle / Maven)
        val hasBuildError = Regex(
            """BUILD FAILED|FAILURE:|COMPILATION ERROR|""" +
            """>\s+Task\s+:\S+\s+FAILED|""" +
            """\[ERROR]|""" +
            """FAILED\s*$""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        ).containsMatchIn(text)

        // Spring Boot / application log errors
        val hasAppError = Regex(
            """APPLICATION FAILED TO START|""" +
            """Failed to configure|""" +
            """Process finished with exit code [^0]|""" +
            """\bERROR\b.*---|""" +
            """Description:\s*\n|""" +
            """Reason:\s+\S|""" +
            """Action:\s*\n|""" +
            """FailureAnalysisReporter|""" +
            """BeanCreationException|""" +
            """UnsatisfiedDependencyException|""" +
            """ApplicationContextException|""" +
            """Bean .+ could not be|""" +
            """Failed to (start|load|bind|instantiate)|""" +
            """No qualifying bean|""" +
            """port \d+ was already in use""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        ).containsMatchIn(text)

        // If any recognized error pattern matches, accept it
        if (hasExceptionLine || hasStackFrame || hasCompileError || hasFileLineError || hasBuildError || hasAppError) {
            return null // valid
        }

        // Reject only clearly non-error input: source code with no error hints
        val looksLikeCode = Regex(
            """^\s*(public|private|protected|class|fun|import|package)\s""",
            RegexOption.MULTILINE
        ).containsMatchIn(text)

        return if (looksLikeCode) {
            "This looks like source code, not an error log. Please paste the error output from your console or logs."
        } else {
            null // be permissive — let the AI backend decide if it's useful
        }
    }

    /**
     * Shows a styled inline validation error in the result panel
     * instead of a blocking popup dialog.
     */
    private fun showValidationError(message: String) {
        resultPanel.removeAll()

        val card = object : JPanel(BorderLayout()) {
            init {
                border = EmptyBorder(12, 14, 12, 14)
                alignmentX = LEFT_ALIGNMENT
                background = JBColor(Color(255, 243, 243), Color(80, 40, 40))
            }
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

        val box = object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
            }
        }

        val heading = JLabel("⚠️  Invalid Input")
        heading.font = heading.font.deriveFont(Font.BOLD, 12.5f)
        heading.foreground = JBColor(Color(180, 40, 40), Color(255, 120, 120))
        heading.alignmentX = LEFT_ALIGNMENT
        box.add(heading)
        box.add(Box.createVerticalStrut(10))

        // Each line of the message as its own label for proper wrapping
        message.lines().forEach { line ->
            val label = JLabel("<html><body style='width:100%'>${line.ifBlank { "&nbsp;" }}</body></html>")
            label.font = label.font.deriveFont(12f)
            label.alignmentX = LEFT_ALIGNMENT
            box.add(label)
            box.add(Box.createVerticalStrut(3))
        }

        card.add(box, BorderLayout.CENTER)
        resultPanel.add(card)
        resultPanel.revalidate()
        resultPanel.repaint()
    }

    // ─────────────────────────────────────────────────────────────
    // ANALYZE
    // ─────────────────────────────────────────────────────────────

    private fun analyzeError(errorText: String) {
        showStatus("⏳  Analyzing…")

        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(project, "SpringForge Analysis") {
                override fun run(indicator: ProgressIndicator) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                        RuntimeAnalysisService.analyze(
                            project, errorText,
                            onResult = { raw ->
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    renderResult(raw)
                                }
                            },
                            onError = { err ->
                                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                    showStatus("❌  $err")
                                }
                            }
                        )
                    }
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────────────────────

    private fun renderResult(raw: String) {
        resultPanel.removeAll()

        val trimmed = raw.trim()
        var answerText: String = trimmed
        var refs = emptyList<RetrievedRef>()

        if (trimmed.startsWith("{")) {
            try {
                // 🟢 NEW: Let Gson do all the heavy lifting!
                val jsonObject = JsonParser.parseString(trimmed).asJsonObject

                // Extract the answer
                val rawAnswer = jsonObject.get("answer")?.takeIf { !it.isJsonNull }?.asString ?: trimmed
                answerText = stripReferencesSection(rawAnswer)

                // Extract the references
                val retrievedDocs = mutableListOf<RetrievedRef>()
                val docsArray = jsonObject.getAsJsonArray("retrieved_docs")

                if (docsArray != null) {
                    for (element in docsArray) {
                        val obj = element.asJsonObject
                        val title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: ""
                        val url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: ""

                        if (title.isNotBlank() || url.isNotBlank()) {
                            retrievedDocs.add(RetrievedRef(title, url))
                        }
                    }
                }
                refs = retrievedDocs

            } catch (e: Exception) {
                // Fallback just in case the LLM outputs malformed JSON
                answerText = stripReferencesSection(trimmed)
                refs = emptyList()
            }
        } else {
            answerText = stripReferencesSection(trimmed)
            refs = emptyList()
        }

        parseAnswerIntoSections(answerText).forEach { section ->
            resultPanel.add(renderSection(section))
            resultPanel.add(vgap(6))
        }

        if (refs.isNotEmpty()) {
            resultPanel.add(renderRefsCard(refs))
            resultPanel.add(vgap(6))
        }

        resultPanel.revalidate()
        resultPanel.repaint()
        SwingUtilities.invokeLater { resultScroll.verticalScrollBar.value = 0 }
    }

    /**
     * Removes the "References:" heading and everything after it from the answer string.
     * The LLM often duplicates references inside the answer text — we show them
     * properly from retrieved_docs instead.
     */
    private fun stripReferencesSection(answer: String): String {
        val lines = answer.lines()
        val cutIndex = lines.indexOfFirst { it.trimStart().startsWith("References:") }
        return if (cutIndex != -1) {
            lines.take(cutIndex).joinToString("\n").trimEnd()
        } else {
            answer
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DATA MODELS
    // ─────────────────────────────────────────────────────────────

    data class RetrievedRef(val title: String, val url: String)

    data class Section(val type: SectionType, val title: String, val blocks: List<Block>)

    enum class SectionType { ERROR, ROOT_CAUSE, FIX, NOTES, PLAIN }

    sealed class Block {
        data class Text(val content: String) : Block()
        data class Code(val language: String, val content: String) : Block()
        data class BulletList(val items: List<String>) : Block()
    }

    // ─────────────────────────────────────────────────────────────
    // SECTION PARSING
    // ─────────────────────────────────────────────────────────────

    private val headingMap = mapOf(
        "Error:"         to SectionType.ERROR,
        "Root Cause:"    to SectionType.ROOT_CAUSE,
        "Suggested Fix:" to SectionType.FIX,
        "Notes:"         to SectionType.NOTES
        // "References:" intentionally excluded — handled via retrieved_docs
    )

    private fun parseAnswerIntoSections(answer: String): List<Section> {
        val lines = answer.lines()
        val rawSections = mutableListOf<Pair<Pair<SectionType, String>, MutableList<String>>>()
        var current: Pair<Pair<SectionType, String>, MutableList<String>>? = null

        for (line in lines) {
            val matchedKey = headingMap.keys.firstOrNull { line.trimStart().startsWith(it) }
            if (matchedKey != null) {
                val type  = headingMap[matchedKey]!!
                val title = matchedKey.trimEnd(':')
                val rest  = line.trimStart().removePrefix(matchedKey).trim()
                val newSection = Pair(Pair(type, title), mutableListOf<String>())
                if (rest.isNotEmpty()) newSection.second.add(rest)
                rawSections.add(newSection)
                current = newSection
            } else {
                if (current == null) {
                    val plain = Pair(Pair(SectionType.PLAIN, "Summary"), mutableListOf<String>())
                    rawSections.add(plain)
                    current = plain
                }
                current.second.add(line)
            }
        }

        return rawSections
            .filter { it.second.any { l -> l.isNotBlank() } }
            .map { (header, bodyLines) -> Section(header.first, header.second, parseBlocks(bodyLines)) }
    }

    private fun parseBlocks(lines: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trimStart().startsWith("```")) {
                val lang = line.trim().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                blocks.add(Block.Code(lang, codeLines.joinToString("\n").trimEnd()))
                continue
            }
            if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("• ")) {
                val items = mutableListOf<String>()
                while (i < lines.size &&
                    (lines[i].trimStart().startsWith("- ") || lines[i].trimStart().startsWith("• "))) {
                    items.add(lines[i].trimStart().removePrefix("- ").removePrefix("• ").trim())
                    i++
                }
                blocks.add(Block.BulletList(items))
                continue
            }
            val textLines = mutableListOf<String>()
            while (i < lines.size) {
                val l = lines[i]
                if (l.trimStart().startsWith("```") ||
                    l.trimStart().startsWith("- ") ||
                    l.trimStart().startsWith("• ")) break
                textLines.add(l)
                i++
            }
            val joined = textLines.joinToString("\n").trim()
            if (joined.isNotBlank()) blocks.add(Block.Text(joined))
        }
        return blocks
    }

    // ─────────────────────────────────────────────────────────────
    // SECTION CARD
    // ─────────────────────────────────────────────────────────────

    private fun renderSection(section: Section): JPanel {
        val card = cardPanel()
        val box  = columnBox()

        val icon = when (section.type) {
            SectionType.ERROR      -> "🚨"
            SectionType.ROOT_CAUSE -> "🔎"
            SectionType.FIX        -> "🛠"
            SectionType.NOTES      -> "💡"
            SectionType.PLAIN      -> "📋"
        }

        val headingLabel = JLabel("$icon  ${section.title}")
        headingLabel.font = headingLabel.font.deriveFont(Font.BOLD, 12.5f)
        headingLabel.alignmentX = LEFT_ALIGNMENT
        box.add(headingLabel)
        box.add(vgap(10))

        for (block in section.blocks) {
            when (block) {
                is Block.Text       -> { box.add(textBlock(block.content));                 box.add(vgap(6)) }
                is Block.Code       -> { box.add(codeBlock(block.language, block.content)); box.add(vgap(6)) }
                is Block.BulletList -> { box.add(bulletBlock(block.items));                 box.add(vgap(6)) }
            }
        }

        card.add(box, BorderLayout.CENTER)
        return card
    }

    // ─────────────────────────────────────────────────────────────
    // REFERENCES CARD
    // ─────────────────────────────────────────────────────────────

    private fun renderRefsCard(refs: List<RetrievedRef>): JPanel {
        val card = cardPanel()
        val box  = columnBox()

        val headingLabel = JLabel("🔗  References")
        headingLabel.font = headingLabel.font.deriveFont(Font.BOLD, 12.5f)
        headingLabel.alignmentX = LEFT_ALIGNMENT
        box.add(headingLabel)
        box.add(vgap(10))

        refs.forEach { ref ->
            box.add(refRow(ref))
            box.add(vgap(6))
        }

        card.add(box, BorderLayout.CENTER)
        return card
    }

    private fun refRow(ref: RetrievedRef): JComponent {
        // 🟢 FIXED: Switched from FlowLayout to BorderLayout so long text stays constrained
        val row = JPanel(BorderLayout(6, 0)) // 6px gap between the bullet and the text
        row.isOpaque = false
        row.alignmentX = LEFT_ALIGNMENT

        val bullet = JLabel("•")
        bullet.font = bullet.font.deriveFont(12f)
        bullet.verticalAlignment = SwingConstants.TOP // 🟢 Keeps bullet at the top if text wraps to multiple lines
        bullet.border = EmptyBorder(1, 0, 0, 0) // Tweak to align bullet with the first line of text
        row.add(bullet, BorderLayout.WEST)

        val isUrl = ref.url.startsWith("http://") || ref.url.startsWith("https://")
        val displayText = ref.title.ifBlank { ref.url }

        // Sanitize text just in case titles contain < or > characters which break HTML
        val safeText = displayText.replace("<", "&lt;").replace(">", "&gt;")

        if (isUrl) {
            // 🟢 FIXED: Wrapped in <html><body style='width:100%'> to force text-wrapping
            val link = JLabel("<html><body style='width:100%'><a href='${ref.url}'>$safeText</a></body></html>")
            link.font = link.font.deriveFont(12f)
            link.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            link.toolTipText = ref.url
            link.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    try { Desktop.getDesktop().browse(URI(ref.url)) }
                    catch (ex: Exception) { Messages.showErrorDialog(project, "Cannot open URL:\n${ref.url}", "SpringForge") }
                }
            })
            row.add(link, BorderLayout.CENTER)
        } else {
            val label = JLabel("<html><body style='width:100%'>$safeText</body></html>")
            label.font = label.font.deriveFont(12f)
            row.add(label, BorderLayout.CENTER)
        }

        return row
    }

    // ─────────────────────────────────────────────────────────────
    // BLOCK COMPONENTS
    // ─────────────────────────────────────────────────────────────

    private fun textBlock(text: String): JComponent {
        val label = JLabel("<html><body style='width:100%'>${text.replace("\n", "<br>")}</body></html>")
        label.font = label.font.deriveFont(12f)
        label.alignmentX = LEFT_ALIGNMENT
        return label
    }

    private fun codeBlock(language: String, code: String): JComponent {
        val wrapper = object : JPanel(BorderLayout(0, 0)) {
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        wrapper.alignmentX = LEFT_ALIGNMENT
        wrapper.border = BorderFactory.createLineBorder(JBColor.border())

        val header = JPanel(BorderLayout())
        header.background = JBColor(Color(228, 228, 228), Color(55, 55, 55))
        header.border = EmptyBorder(3, 10, 3, 6)

        val langLabel = JLabel(if (language.isNotBlank()) language else "code")
        langLabel.font = langLabel.font.deriveFont(Font.BOLD, 10f)
        langLabel.foreground = JBColor(Color(100, 100, 100), Color(170, 170, 170))

        val copyBtn = JButton("Copy")
        copyBtn.font = copyBtn.font.deriveFont(10f)
        copyBtn.isFocusPainted = false
        copyBtn.addActionListener {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
            copyBtn.text = "✓ Copied"
            Timer(1600) { copyBtn.text = "Copy" }.also { t -> t.isRepeats = false; t.start() }
        }
        header.add(langLabel, BorderLayout.WEST)
        header.add(copyBtn, BorderLayout.EAST)

        val area = JTextArea(code)
        area.isEditable = false
        area.font = monoFont(12)
        area.background = JBColor(Color(246, 248, 250), Color(38, 40, 42))
        area.foreground = JBColor(Color(36, 41, 46), Color(201, 209, 217))
        area.border = EmptyBorder(10, 12, 10, 12)
        area.lineWrap = false

        val codeScroll = JBScrollPane(area).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder() // Keeps your seamless design
        }

        wrapper.add(header, BorderLayout.NORTH)
        wrapper.add(codeScroll, BorderLayout.CENTER)
        return wrapper
    }

    private fun bulletBlock(items: List<String>): JComponent {
        val panel = columnBox()
        for (item in items) {
            val label = JLabel("<html><body>•&nbsp;&nbsp;$item</body></html>")
            label.font = label.font.deriveFont(12f)
            label.alignmentX = LEFT_ALIGNMENT
            label.border = EmptyBorder(2, 8, 2, 0)
            panel.add(label)
        }
        return panel
    }

    // ─────────────────────────────────────────────────────────────
    // LAYOUT HELPERS
    // ─────────────────────────────────────────────────────────────

    private fun showStatus(msg: String) {
        resultPanel.removeAll()
        val label = JLabel(msg)
        label.border = EmptyBorder(12, 12, 12, 12)
        label.alignmentX = LEFT_ALIGNMENT
        resultPanel.add(label)
        resultPanel.revalidate()
        resultPanel.repaint()
    }

    private fun cardPanel(): JPanel {
        return object : JPanel(BorderLayout()) {
            init {
                border = EmptyBorder(12, 14, 12, 14)
                alignmentX = LEFT_ALIGNMENT
            }
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun columnBox(): JPanel {
        return object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = LEFT_ALIGNMENT
            }
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
    }

    private fun vgap(h: Int): Component = Box.createVerticalStrut(h)

    private fun monoFont(size: Int): Font {
        for (name in listOf("JetBrains Mono", "Cascadia Code", "Consolas", "Menlo")) {
            val f = Font(name, Font.PLAIN, size)
            if (f.family == name) return f
        }
        return Font(Font.MONOSPACED, Font.PLAIN, size)
    }

    // ─────────────────────────────────────────────────────────────
    // JSON EXTRACTION
    // ─────────────────────────────────────────────────────────────

//    private fun extractJsonString(json: String, key: String): String? {
//        val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
//        return pattern.find(json)?.groupValues?.get(1)
//            ?.replace("\\n",  "\n")
//            ?.replace("\\\"", "\"")
//            ?.replace("\\\\", "\\")
//            ?.replace("\\t",  "\t")
//            ?.replace("\\r",  "")
//    }

    /**
     * Extracts retrieved_docs as RetrievedRef(title, url).
     * Real API shape: { "title": "...", "url": "https://..." }
     */
//    private fun extractRetrievedDocs(json: String): List<RetrievedRef> {
//        val refs = mutableListOf<RetrievedRef>()
//
//        val docsStart = json.indexOf("\"retrieved_docs\"")
//        if (docsStart == -1) return refs
//        val arrayStart = json.indexOf('[', docsStart)
//        if (arrayStart == -1) return refs
//
//        var depth = 0
//        var objStart = -1
//        var i = arrayStart
//
//        while (i < json.length) {
//            when (json[i]) {
//                '[' -> depth++
//                '{' -> { if (depth == 1) objStart = i; depth++ }
//                '}' -> {
//                    depth--
//                    if (depth == 1 && objStart != -1) {
//                        val obj = json.substring(objStart, i + 1)
//                        val title = extractJsonString(obj, "title") ?: ""
//                        val url   = extractJsonString(obj, "url")   ?: ""
//                        if (url.isNotBlank() || title.isNotBlank()) {
//                            refs.add(RetrievedRef(title = title, url = url))
//                        }
//                        objStart = -1
//                    }
//                    if (depth == 0) return refs
//                }
//                ']' -> { if (depth == 1) return refs; depth-- }
//            }
//            i++
//        }
//        return refs
//    }
}
