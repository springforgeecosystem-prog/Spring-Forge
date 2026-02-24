package org.springforge.codegeneration.ui

import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Represents a Spring Boot dependency with its Initializr id, display name,
 * category (for grouping/searching), and a short description.
 */
data class SpringDependency(
    val id: String,
    val name: String,
    val category: String,
    val description: String
) {
    override fun toString(): String = name
}

class DependencySelector : JPanel(BorderLayout()) {

    // ─── FULL DEPENDENCY CATALOG (sourced from start.spring.io/metadata/client) ───

    private val allDependencies = listOf(

        // ── Developer Tools ──
        SpringDependency("devtools", "Spring Boot DevTools", "Developer Tools", "Fast application restarts, LiveReload, and enhanced dev experience."),
        SpringDependency("lombok", "Lombok", "Developer Tools", "Annotation library to reduce boilerplate code (getters, setters, builders, etc.)."),
        SpringDependency("configuration-processor", "Spring Configuration Processor", "Developer Tools", "Generate metadata for custom configuration keys (application.properties)."),
        SpringDependency("docker-compose", "Docker Compose Support", "Developer Tools", "Provides Docker Compose support for enhanced development experience."),
        SpringDependency("modulith", "Spring Modulith", "Developer Tools", "Support for building modular monolithic applications."),
        SpringDependency("native", "GraalVM Native Support", "Developer Tools", "Support for compiling Spring apps to native executables using GraalVM."),

        // ── Web ──
        SpringDependency("web", "Spring Web", "Web", "Build web & RESTful apps using Spring MVC. Uses embedded Tomcat."),
        SpringDependency("webflux", "Spring Reactive Web", "Web", "Build reactive web applications with Spring WebFlux and Netty."),
        SpringDependency("graphql", "Spring for GraphQL", "Web", "Build GraphQL applications with Spring for GraphQL and GraphQL Java."),
        SpringDependency("data-rest", "Rest Repositories", "Web", "Expose Spring Data repositories over REST via Spring Data REST."),
        SpringDependency("data-rest-explorer", "REST Repositories HAL Explorer", "Web", "Browse Spring Data REST repositories in your browser."),
        SpringDependency("hateoas", "Spring HATEOAS", "Web", "Create RESTful APIs that follow the HATEOAS principle."),
        SpringDependency("web-services", "Spring Web Services", "Web", "Contract-first SOAP development using Spring WS."),
        SpringDependency("jersey", "Jersey", "Web", "Framework for developing RESTful Web Services with JAX-RS APIs."),
        SpringDependency("websocket", "WebSocket", "Web", "Build Servlet-based WebSocket applications with SockJS and STOMP."),
        SpringDependency("rsocket", "RSocket", "Web", "RSocket.io applications with Spring Messaging and Netty."),

        // ── Template Engines ──
        SpringDependency("thymeleaf", "Thymeleaf", "Template Engines", "Modern server-side Java template engine for web and standalone environments."),
        SpringDependency("freemarker", "Apache FreeMarker", "Template Engines", "Generate HTML, emails, config files, source code based on templates."),
        SpringDependency("mustache", "Mustache", "Template Engines", "Logic-less templates. No if/else/for — only tags."),
        SpringDependency("groovy-templates", "Groovy Templates", "Template Engines", "Groovy templating engine."),
        SpringDependency("jte", "JTE", "Template Engines", "Secure and lightweight template engine for Java and Kotlin."),

        // ── Security ──
        SpringDependency("security", "Spring Security", "Security", "Highly customizable authentication and access-control framework."),
        SpringDependency("oauth2-client", "OAuth2 Client", "Security", "Spring Security's OAuth2/OpenID Connect client features."),
        SpringDependency("oauth2-authorization-server", "OAuth2 Authorization Server", "Security", "Spring Boot integration for Spring Authorization Server."),
        SpringDependency("oauth2-resource-server", "OAuth2 Resource Server", "Security", "Spring Security's OAuth2 resource server features."),
        SpringDependency("data-ldap", "Spring Data LDAP", "Security", "Spring Data support for LDAP."),

        // ── SQL Databases ──
        SpringDependency("data-jpa", "Spring Data JPA", "SQL", "Persist data in SQL stores using JPA with Spring Data and Hibernate."),
        SpringDependency("data-jdbc", "Spring Data JDBC", "SQL", "Persist data in SQL stores with plain JDBC using Spring Data."),
        SpringDependency("data-r2dbc", "Spring Data R2DBC", "SQL", "Reactive Relational Database Connectivity for reactive apps."),
        SpringDependency("jdbc", "JDBC API", "SQL", "Database Connectivity API for connecting and querying databases."),
        SpringDependency("mybatis", "MyBatis Framework", "SQL", "Persistence framework with custom SQL, stored procedures, and mappings."),
        SpringDependency("liquibase", "Liquibase Migration", "SQL", "Database migration and source control library."),
        SpringDependency("flyway", "Flyway Migration", "SQL", "Version control for your database schema migrations."),
        SpringDependency("jooq", "JOOQ Access Layer", "SQL", "Generate Java code from your database; build type-safe SQL queries."),

        // ── SQL Drivers ──
        SpringDependency("mysql", "MySQL Driver", "SQL Driver", "MySQL JDBC driver."),
        SpringDependency("postgresql", "PostgreSQL Driver", "SQL Driver", "JDBC & R2DBC driver for PostgreSQL databases."),
        SpringDependency("sqlserver", "MS SQL Server Driver", "SQL Driver", "JDBC & R2DBC driver for Microsoft SQL Server and Azure SQL."),
        SpringDependency("oracle", "Oracle Driver", "SQL Driver", "JDBC driver for Oracle databases."),
        SpringDependency("mariadb", "MariaDB Driver", "SQL Driver", "MariaDB JDBC and R2DBC driver."),
        SpringDependency("h2", "H2 Database", "SQL Driver", "Fast in-memory database (JDBC & R2DBC). Great for testing."),
        SpringDependency("hsql", "HyperSQL Database", "SQL Driver", "Lightweight 100% Java SQL Database Engine."),
        SpringDependency("derby", "Apache Derby Database", "SQL Driver", "Open source relational database implemented entirely in Java."),
        SpringDependency("db2", "IBM DB2 Driver", "SQL Driver", "JDBC driver for IBM DB2."),

        // ── NoSQL ──
        SpringDependency("data-redis", "Spring Data Redis", "NoSQL", "Advanced Java Redis client for synchronous, async, and reactive usage."),
        SpringDependency("data-redis-reactive", "Spring Data Reactive Redis", "NoSQL", "Access Redis key-value stores in a reactive fashion."),
        SpringDependency("data-mongodb", "Spring Data MongoDB", "NoSQL", "Spring Data support for MongoDB document database."),
        SpringDependency("data-mongodb-reactive", "Spring Data Reactive MongoDB", "NoSQL", "Reactive Spring Data support for MongoDB."),
        SpringDependency("data-elasticsearch", "Spring Data Elasticsearch", "NoSQL", "Spring Data support for Elasticsearch search engine."),
        SpringDependency("data-cassandra", "Spring Data Cassandra", "NoSQL", "Spring Data support for Apache Cassandra."),
        SpringDependency("data-cassandra-reactive", "Spring Data Reactive Cassandra", "NoSQL", "Reactive Spring Data support for Apache Cassandra."),
        SpringDependency("data-couchbase", "Spring Data Couchbase", "NoSQL", "Spring Data support for Couchbase."),
        SpringDependency("data-couchbase-reactive", "Spring Data Reactive Couchbase", "NoSQL", "Reactive Spring Data support for Couchbase."),
        SpringDependency("data-neo4j", "Spring Data Neo4j", "NoSQL", "Spring Data support for Neo4j graph database."),

        // ── Messaging ──
        SpringDependency("amqp", "Spring for RabbitMQ", "Messaging", "Send and receive messages with RabbitMQ."),
        SpringDependency("amqp-streams", "Spring for RabbitMQ Streams", "Messaging", "Stream processing applications with RabbitMQ."),
        SpringDependency("kafka", "Spring for Apache Kafka", "Messaging", "Publish, subscribe, store, and process streams of records."),
        SpringDependency("kafka-streams", "Spring for Apache Kafka Streams", "Messaging", "Stream processing with Apache Kafka Streams."),
        SpringDependency("activemq", "Spring for ActiveMQ 5", "Messaging", "Spring JMS support with Apache ActiveMQ Classic."),
        SpringDependency("artemis", "Spring for ActiveMQ Artemis", "Messaging", "Spring JMS support with Apache ActiveMQ Artemis."),
        SpringDependency("pulsar", "Spring for Apache Pulsar", "Messaging", "Build messaging applications with Apache Pulsar."),
        SpringDependency("integration", "Spring Integration", "Messaging", "Enterprise Integration Patterns with lightweight messaging."),

        // ── I/O & Validation ──
        SpringDependency("validation", "Validation", "I/O", "Bean Validation with Hibernate Validator."),
        SpringDependency("mail", "Java Mail Sender", "I/O", "Send email using Java Mail and Spring's JavaMailSender."),
        SpringDependency("quartz", "Quartz Scheduler", "I/O", "Schedule jobs using Quartz."),
        SpringDependency("cache", "Spring Cache Abstraction", "I/O", "Provides cache-related operations (requires a cache provider)."),
        SpringDependency("batch", "Spring Batch", "I/O", "Batch applications with transactions, retry/skip and chunk processing."),

        // ── Ops & Observability ──
        SpringDependency("actuator", "Spring Boot Actuator", "Ops", "Monitor and manage your application with built-in endpoints."),
        SpringDependency("sbom-cyclone-dx", "CycloneDX SBOM Support", "Ops", "Creates a Software Bill of Materials in CycloneDX format."),
        SpringDependency("prometheus", "Prometheus", "Observability", "Expose Micrometer metrics in Prometheus format."),
        SpringDependency("distributed-tracing", "Distributed Tracing", "Observability", "Enable span and trace IDs in logs."),
        SpringDependency("zipkin", "Zipkin", "Observability", "Enable and expose span and trace IDs to Zipkin."),
        SpringDependency("datadog", "Datadog", "Observability", "Publish Micrometer metrics to Datadog."),
        SpringDependency("new-relic", "New Relic", "Observability", "Publish Micrometer metrics to New Relic."),
        SpringDependency("otlp-metrics", "OTLP for Metrics", "Observability", "Publish Micrometer metrics to OpenTelemetry Protocol backend."),

        // ── Testing ──
        SpringDependency("testcontainers", "Testcontainers", "Testing", "Lightweight, throwaway instances of databases, browsers, etc. in Docker."),
        SpringDependency("restdocs", "Spring REST Docs", "Testing", "Document RESTful services with hand-written + auto-generated snippets."),
        SpringDependency("unboundid-ldap", "Embedded LDAP Server", "Testing", "Platform neutral way for running LDAP server in unit tests."),

        // ── Session ──
        SpringDependency("session-jdbc", "Spring Session JDBC", "Web", "Manage user session information with JDBC."),
        SpringDependency("session-data-redis", "Spring Session Redis", "Web", "Manage user session information with Redis."),

        // ── Spring Cloud (Core) ──
        SpringDependency("cloud-starter", "Cloud Bootstrap", "Spring Cloud", "Non-specific Spring Cloud features (Bootstrap context, @RefreshScope)."),
        SpringDependency("cloud-config-client", "Config Client", "Spring Cloud", "Connect to a Spring Cloud Config Server for configuration."),
        SpringDependency("cloud-config-server", "Config Server", "Spring Cloud", "Central configuration management via Git, SVN, or Vault."),
        SpringDependency("cloud-eureka", "Eureka Discovery Client", "Spring Cloud", "REST-based service discovery for load balancing and failover."),
        SpringDependency("cloud-eureka-server", "Eureka Server", "Spring Cloud", "Spring Cloud Netflix Eureka Server."),
        SpringDependency("cloud-gateway", "Gateway", "Spring Cloud", "Route to APIs with cross-cutting concerns (security, metrics, resiliency)."),
        SpringDependency("cloud-gateway-reactive", "Reactive Gateway", "Spring Cloud", "Reactive API gateway with security, monitoring, and resiliency."),
        SpringDependency("cloud-feign", "OpenFeign", "Spring Cloud", "Declarative REST Client with JAX-RS or Spring MVC annotations."),
        SpringDependency("cloud-loadbalancer", "Cloud LoadBalancer", "Spring Cloud", "Client-side load-balancing with Spring Cloud LoadBalancer."),
        SpringDependency("cloud-resilience4j", "Resilience4J", "Spring Cloud", "Spring Cloud Circuit breaker with Resilience4j."),
        SpringDependency("cloud-bus", "Cloud Bus", "Spring Cloud", "Lightweight message broker for broadcasting state changes."),
        SpringDependency("cloud-stream", "Cloud Stream", "Spring Cloud", "Event-driven microservices with shared messaging systems."),

        // ── Azure ──
        SpringDependency("azure-support", "Azure Support", "Microsoft Azure", "Auto-configuration for Azure Services (Service Bus, Storage, AD, Key Vault)."),
        SpringDependency("azure-active-directory", "Azure Active Directory", "Microsoft Azure", "Spring Security integration with Azure AD for authentication."),
        SpringDependency("azure-cosmos-db", "Azure Cosmos DB", "Microsoft Azure", "Fully managed NoSQL database with Spring Data support."),
        SpringDependency("azure-keyvault", "Azure Key Vault", "Microsoft Azure", "Manage application secrets and certificates."),
        SpringDependency("azure-storage", "Azure Storage", "Microsoft Azure", "Blob, file share, and queue storage features.")
    )

    // FIX: Use empty constructor to avoid Kotlin constructor ambiguity
    private val availableModel = CollectionListModel<SpringDependency>()
    private val selectedModel = CollectionListModel<SpringDependency>()

    private val availableList = JBList(availableModel)
    private val selectedList = JBList(selectedModel)
    private val searchField = JBTextField()
    private val countLabel = JLabel("${allDependencies.size} dependencies available")

    init {
        availableModel.add(allDependencies)

        // Custom cell renderer — shows [Category] Name with distinct styling
        val depRenderer = object : ColoredListCellRenderer<SpringDependency>() {
            override fun customizeCellRenderer(
                list: JList<out SpringDependency>,
                value: SpringDependency?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value == null) return
                append("[${value.category}] ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                toolTipText = value.description
            }
        }

        availableList.cellRenderer = depRenderer
        selectedList.cellRenderer = depRenderer

        // 1. Search Logic
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = filter()
            override fun removeUpdate(e: DocumentEvent?) = filter()
            override fun changedUpdate(e: DocumentEvent?) = filter()
        })

        // 2. Setup Lists
        availableList.setEmptyText("No matching dependencies found")
        selectedList.setEmptyText("No dependencies selected")

        // Double-click to add (available list)
        availableList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) addSelected()
            }
        })
        // Double-click to remove (selected list)
        selectedList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) removeSelected()
            }
        })

        val moveRightBtn = JButton("Add \u25B6").apply {
            addActionListener { addSelected() }
        }

        val moveLeftBtn = JButton("\u25C0 Remove").apply {
            addActionListener { removeSelected() }
        }

        // 3. Layout (Split Pane)
        val leftPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Available:"), BorderLayout.NORTH)
            add(JBScrollPane(availableList), BorderLayout.CENTER)
            add(moveRightBtn, BorderLayout.SOUTH)
        }

        val rightPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Selected:"), BorderLayout.NORTH)
            add(JBScrollPane(selectedList), BorderLayout.CENTER)
            add(moveLeftBtn, BorderLayout.SOUTH)
        }

        // Main layout
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        centerSplit.dividerLocation = 300
        centerSplit.resizeWeight = 0.55

        // Search bar + count at top
        val searchPanel = JPanel(BorderLayout(5, 0))
        searchPanel.add(JLabel("Search: "), BorderLayout.WEST)
        searchPanel.add(searchField, BorderLayout.CENTER)
        searchPanel.add(countLabel, BorderLayout.EAST)

        add(searchPanel, BorderLayout.NORTH)
        add(centerSplit, BorderLayout.CENTER)

        preferredSize = Dimension(650, 450)
    }

    private fun addSelected() {
        val selected = availableList.selectedValue
        if (selected != null && !selectedModel.items.contains(selected)) {
            selectedModel.add(selected)
        }
    }

    private fun removeSelected() {
        val selected = selectedList.selectedValue
        if (selected != null) {
            selectedModel.remove(selected)
        }
    }

    private fun filter() {
        val query = searchField.text.lowercase().trim()
        val filtered = allDependencies.filter {
            it.name.lowercase().contains(query) ||
            it.id.lowercase().contains(query) ||
            it.category.lowercase().contains(query) ||
            it.description.lowercase().contains(query)
        }

        availableModel.removeAll()
        availableModel.add(filtered)
        countLabel.text = if (query.isEmpty()) "${allDependencies.size} total"
                          else "${filtered.size} / ${allDependencies.size}"
    }

    fun getSelectedDependencies(): List<String> {
        return selectedModel.items.map { it.id }
    }
}