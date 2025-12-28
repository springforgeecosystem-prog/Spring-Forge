package org.springforge.codegeneration.ui

import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

// Simple data class for dependencies
data class SpringDependency(val id: String, val name: String, val description: String) {
    override fun toString(): String = name // This is what the List shows
}

class DependencySelector : JPanel(BorderLayout()) {

    // HARDCODED DATA (TODO: Fetch this from https://start.spring.io/metadata/client)
    private val allDependencies = listOf(
        SpringDependency("web", "Spring Web", "Build web, including RESTful, applications using Spring MVC."),
        SpringDependency("data-jpa", "Spring Data JPA", "Persist data in SQL stores with Java Persistence API using Spring Data and Hibernate."),
        SpringDependency("lombok", "Lombok", "Java annotation library which helps to reduce boilerplate code."),
        SpringDependency("security", "Spring Security", "Highly customizable authentication and access-control framework."),
        SpringDependency("devtools", "Spring Boot DevTools", "Provides fast application restarts, LiveReload, and configurations for enhanced development experience."),
        SpringDependency("actuator", "Spring Boot Actuator", "Supports built in (or custom) endpoints that let you monitor and manage your application."),
        SpringDependency("validation", "Validation", "Bean Validation with Hibernate Validator."),
        SpringDependency("thymeleaf", "Thymeleaf", "A modern server-side Java template engine."),
        SpringDependency("docker-compose", "Docker Compose Support", "Provides rudimentary support for Docker Compose."),
        SpringDependency("postgresql", "PostgreSQL Driver", "A JDBC and R2DBC driver that allows Java programs to connect to a PostgreSQL database."),
        SpringDependency("mysql", "MySQL Driver", "MySQL JDBC and R2DBC driver.")
    )

    // FIX: Use empty constructor to avoid Kotlin constructor ambiguity
    private val availableModel = CollectionListModel<SpringDependency>()
    private val selectedModel = CollectionListModel<SpringDependency>()

    private val availableList = JBList(availableModel)
    private val selectedList = JBList(selectedModel)
    private val searchField = JBTextField()

    init {
        // FIX: Add data explicitly here
        availableModel.add(allDependencies)

        // 1. Search Logic
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filter()
            override fun removeUpdate(e: DocumentEvent?) = filter()
            override fun changedUpdate(e: DocumentEvent?) = filter()
        })

        // 2. Setup Lists
        availableList.setEmptyText("No matching dependencies found")
        selectedList.setEmptyText("No dependencies selected")

        // Double click to add/remove
        availableList.addListSelectionListener {
            val selected = availableList.selectedValue
            // Ensure we don't crash if nothing is selected
            if (selected != null) {
                availableList.toolTipText = selected.description
            }
        }

        val moveRightBtn = JButton("Add >").apply {
            addActionListener {
                val selected = availableList.selectedValue
                if (selected != null && !selectedModel.items.contains(selected)) {
                    selectedModel.add(selected)
                }
            }
        }

        val moveLeftBtn = JButton("< Remove").apply {
            addActionListener {
                val selected = selectedList.selectedValue
                if (selected != null) {
                    selectedModel.remove(selected)
                }
            }
        }

        // 3. Layout (Split Pane)
        val leftPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Available:"), BorderLayout.NORTH)
            add(JBScrollPane(availableList), BorderLayout.CENTER)
            add(moveRightBtn, BorderLayout.SOUTH) // Button at bottom of left panel
        }

        val rightPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Selected:"), BorderLayout.NORTH)
            add(JBScrollPane(selectedList), BorderLayout.CENTER)
            add(moveLeftBtn, BorderLayout.SOUTH) // Button at bottom of right panel
        }

        // Main layout
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        centerSplit.dividerLocation = 250 // Slightly wider for descriptions
        centerSplit.resizeWeight = 0.5

        // Add Search bar at top
        val searchPanel = JPanel(BorderLayout())
        searchPanel.add(JLabel("Search: "), BorderLayout.WEST)
        searchPanel.add(searchField, BorderLayout.CENTER)

        // Combine everything
        add(searchPanel, BorderLayout.NORTH)
        add(centerSplit, BorderLayout.CENTER)

        preferredSize = Dimension(600, 400)
    }

    private fun filter() {
        val query = searchField.text.lowercase().trim()
        val filtered = allDependencies.filter {
            it.name.lowercase().contains(query) || it.id.contains(query)
        }

        // CollectionListModel specific methods to clear and refill
        availableModel.removeAll()
        availableModel.add(filtered)
    }

    fun getSelectedDependencies(): List<String> {
        return selectedModel.items.map { it.id }
    }
}