package org.springforge.qualityassurance.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Architecture selection dialog shown before every analysis run.
 *
 * The four architecture keys returned by [getSelectedArchitecture] match the
 * ML-service contract EXACTLY (lowercase, underscore for clean_architecture):
 *
 *   "layered"             → Layered (Controller → Service → Repository)
 *   "mvc"                 → MVC     (Model-View-Controller)
 *   "hexagonal"           → Hexagonal (Ports & Adapters)
 *   "clean_architecture"  → Clean Architecture (Dependency Rule)
 *
 * This is the canonical source-of-truth for architecture strings in the plugin.
 * Every other class (PsiFeatureExtractor, QualityAssurancePanel, MLServiceClient)
 * relies on these exact values — do not change them without updating the ML service.
 */
class ArchitectureSelectDialog : DialogWrapper(true) {

    // ── Architecture metadata ─────────────────────────────────────────────────
    //   Triple(apiKey, displayName, description)
    private val architectures = listOf(
        Triple("layered",            "Layered",            "Controller → Service → Repository"),
        Triple("mvc",                "MVC",                "Model-View-Controller pattern"),
        Triple("hexagonal",          "Hexagonal",          "Ports & Adapters / Onion"),
        Triple("clean_architecture", "Clean Architecture", "Dependency Rule enforced")
    )

    private var selectedKey = "layered"

    // Card panels keyed by apiKey
    private val cards = mutableMapOf<String, JPanel>()

    init {
        title = "SpringForge — Select Architecture Pattern"
        init()
    }

    // ── Dialog content ────────────────────────────────────────────────────────

    override fun createCenterPanel(): JComponent {
        val root = JPanel().apply {
            layout  = BoxLayout(this, BoxLayout.Y_AXIS)
            border  = JBUI.Borders.empty(12, 16, 8, 16)
            isOpaque = false
        }

        // Subtitle
        root.add(JBLabel("Choose the architectural style of the project under analysis.").apply {
            font       = JBUI.Fonts.label(11f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })
        root.add(Box.createVerticalStrut(14))

        // 2 × 2 card grid
        val grid = JPanel(GridLayout(2, 2, 10, 10)).apply {
            isOpaque    = false
            alignmentX  = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(480, 140)
        }

        architectures.forEach { (key, name, desc) ->
            val card = buildCard(key, name, desc, key == selectedKey)
            cards[key] = card
            grid.add(card)
        }
        root.add(grid)
        root.add(Box.createVerticalStrut(10))

        // Hint row
        root.add(JBLabel("💡  The ML model was trained on all four patterns — pick the one closest to your project.").apply {
            font       = JBUI.Fonts.label(10f)
            foreground = JBColor.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })

        return root
    }

    // ── Card builder ──────────────────────────────────────────────────────────

    private fun buildCard(key: String, name: String, desc: String, selected: Boolean): JPanel {
        val icons = mapOf(
            "layered"            to "🏗️",
            "mvc"                to "🔄",
            "hexagonal"          to "⬡",
            "clean_architecture" to "◎"
        )

        val card = JPanel(BorderLayout(10, 0)).apply {
            cursor   = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isOpaque = true
            applyStyle(this, selected)
        }

        val iconLabel = JBLabel(icons[key] ?: "◻").apply { font = font.deriveFont(18f) }

        val col = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        col.add(JBLabel(name).apply {
            font       = JBUI.Fonts.label(12f).asBold()
            foreground = if (selected)
                JBColor(Color(0x1D4ED8), Color(0x93C5FD))
            else
                UIUtil.getLabelForeground()
            this.name  = "nameLabel"   // tag for later lookup
        })
        col.add(Box.createVerticalStrut(2))
        col.add(JBLabel(desc).apply {
            font       = JBUI.Fonts.label(9f)
            foreground = JBColor.GRAY
        })

        card.add(iconLabel, BorderLayout.WEST)
        card.add(col,       BorderLayout.CENTER)

        // Click handler
        val clickListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = selectCard(key)
            override fun mouseEntered(e: MouseEvent) {
                if (key != selectedKey) card.background = JBColor(Color(0xF1F5F9), Color(0x1E293B))
            }
            override fun mouseExited(e: MouseEvent) {
                if (key != selectedKey) applyStyle(card, false)
            }
        }
        card.addMouseListener(clickListener)
        iconLabel.addMouseListener(clickListener)
        col.addMouseListener(clickListener)

        return card
    }

    private fun applyStyle(card: JPanel, selected: Boolean) {
        card.background = if (selected)
            JBColor(Color(0xEFF6FF), Color(0x1E3A5F))
        else
            UIUtil.getPanelBackground()

        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                if (selected) JBColor(Color(0x3B82F6), Color(0x60A5FA)) else JBColor.border(),
                if (selected) 2 else 1,
                true
            ),
            EmptyBorder(8, 12, 8, 12)
        )
    }

    private fun selectCard(key: String) {
        val prev = selectedKey
        selectedKey = key

        // Deselect old card
        cards[prev]?.let { card ->
            applyStyle(card, false)
            findNameLabel(card)?.foreground = UIUtil.getLabelForeground()
        }
        // Select new card
        cards[key]?.let { card ->
            applyStyle(card, true)
            findNameLabel(card)?.foreground = JBColor(Color(0x1D4ED8), Color(0x93C5FD))
        }
    }

    /** Walks the card's component tree to find the JBLabel tagged "nameLabel". */
    private fun findNameLabel(card: JPanel): JBLabel? {
        fun scan(c: Component): JBLabel? {
            if (c is JBLabel && c.name == "nameLabel") return c
            if (c is Container) c.components.forEach { child -> scan(child)?.let { return it } }
            return null
        }
        return scan(card)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the ML-service API key for the selected architecture.
     * Values: "layered" | "mvc" | "hexagonal" | "clean_architecture"
     */
    fun getSelectedArchitecture(): String = selectedKey
}