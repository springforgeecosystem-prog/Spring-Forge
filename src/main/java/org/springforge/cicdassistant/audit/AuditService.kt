package org.springforge.cicdassistant.audit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant

/**
 * Project-level light service that writes audit events to PostgreSQL.
 *
 * All writes run on a pooled background thread — the EDT is never blocked.
 * If PostgreSQL is not configured (missing .env keys) every log call is a no-op
 * and the plugin continues to work normally.
 *
 * Schema is auto-created on first connection (CREATE TABLE IF NOT EXISTS).
 */
@Service(Service.Level.PROJECT)
class AuditService(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): AuditService =
            project.getService(AuditService::class.java)

        private const val DDL = """
            CREATE TABLE IF NOT EXISTS sf_audit_events (
                id            BIGSERIAL    PRIMARY KEY,
                event_type    VARCHAR(30)  NOT NULL,
                project_path  VARCHAR(1000),
                source_type   VARCHAR(20),
                artifacts     TEXT,
                files_count   INT          DEFAULT 0,
                success       BOOLEAN      NOT NULL DEFAULT TRUE,
                error_msg     TEXT,
                issues_error  INT          DEFAULT 0,
                issues_warn   INT          DEFAULT 0,
                issues_info   INT          DEFAULT 0,
                insight_count INT          DEFAULT 0,
                duration_ms   BIGINT       DEFAULT 0,
                created_at    TIMESTAMPTZ  DEFAULT NOW()
            );
            CREATE INDEX IF NOT EXISTS sf_audit_events_created_idx ON sf_audit_events (created_at DESC);
            CREATE INDEX IF NOT EXISTS sf_audit_events_type_idx    ON sf_audit_events (event_type);
        """

        private const val INSERT_SQL = """
            INSERT INTO sf_audit_events
                (event_type, project_path, source_type, artifacts, files_count,
                 success, error_msg, issues_error, issues_warn, issues_info,
                 insight_count, duration_ms)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
        """

        private const val SELECT_RECENT = """
            SELECT id, event_type, project_path, source_type, artifacts, files_count,
                   success, error_msg, issues_error, issues_warn, issues_info,
                   insight_count, duration_ms, created_at
            FROM sf_audit_events
            ORDER BY created_at DESC
            LIMIT ?
        """

        private const val SELECT_STATS = """
            SELECT event_type,
                   COUNT(*)                                          AS total,
                   SUM(CASE WHEN success THEN 1 ELSE 0 END)         AS successes,
                   COALESCE(AVG(duration_ms), 0)                    AS avg_ms
            FROM sf_audit_events
            GROUP BY event_type
        """

        private const val DELETE_ALL = "DELETE FROM sf_audit_events"
    }

    private val config = DatabaseConfig()
    private var conn: Connection? = null
    private var connected = false

    /** Set by AuditDashboardPanel to auto-refresh the UI after every write. */
    var onNewEvent: (() -> Unit)? = null

    // ── Connection ─────────────────────────────────────────────────────────────

    fun isConfigured(): Boolean = config.isConfigured()

    private fun getConnection(): Connection? {
        if (!config.isConfigured()) return null
        try {
            val c = conn
            if (c != null && !c.isClosed) return c
            Class.forName("org.postgresql.Driver")
            val newConn = DriverManager.getConnection(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
            conn = newConn
            connected = true
            ensureSchema(newConn)
            return newConn
        } catch (e: Exception) {
            connected = false
            println("[AuditService] Cannot connect to PostgreSQL: ${e.message}")
            return null
        }
    }

    private fun ensureSchema(c: Connection) {
        try {
            c.createStatement().use { it.execute(DDL) }
        } catch (e: Exception) {
            println("[AuditService] Schema creation failed: ${e.message}")
        }
    }

    // ── Public logging API ─────────────────────────────────────────────────────

    fun logGeneration(
        source: String,
        artifacts: List<String>,
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.GENERATION,
            projectPath  = project.basePath,
            sourceType   = source,
            artifacts    = artifacts.joinToString(","),
            filesCount   = artifacts.size,
            success      = success,
            errorMsg     = errorMsg,
            durationMs   = durationMs
        )
    )

    fun logValidation(
        filesCount: Int,
        errorCount: Int,
        warnCount: Int,
        infoCount: Int,
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.VALIDATION,
            projectPath  = project.basePath,
            filesCount   = filesCount,
            success      = success,
            errorMsg     = errorMsg,
            issuesError  = errorCount,
            issuesWarn   = warnCount,
            issuesInfo   = infoCount,
            durationMs   = durationMs
        )
    )

    fun logExplainability(
        filesCount: Int,
        insightCount: Int,
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.EXPLAINABILITY,
            projectPath  = project.basePath,
            filesCount   = filesCount,
            success      = success,
            errorMsg     = errorMsg,
            insightCount = insightCount,
            durationMs   = durationMs
        )
    )

    /**
     * Code Generation module — logs a Gemini-powered code generation run.
     * Field mapping:
     *   filesCount   = total files from LLM
     *   insightCount = files actually written
     *   issuesWarn   = files skipped (already exist)
     *   issuesError  = files that failed to write
     *   sourceType   = "GEMINI"
     *   artifacts    = "java_files"
     */
    fun logCodeGeneration(
        filesGenerated: Int,
        filesWritten: Int,
        filesSkipped: Int,
        fileErrors: Int,
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.CODE_GENERATION,
            projectPath  = project.basePath,
            sourceType   = "GEMINI",
            artifacts    = "java_files",
            filesCount   = filesGenerated,
            insightCount = filesWritten,
            issuesWarn   = filesSkipped,
            issuesError  = fileErrors,
            success      = success,
            errorMsg     = errorMsg,
            durationMs   = durationMs
        )
    )

    /**
     * Quality Scan module — logs an ML + Gemini quality analysis run.
     * Field mapping:
     *   filesCount   = total files analyzed
     *   issuesWarn   = total violations found
     *   issuesError  = critical violations
     *   insightCount = AI fix suggestions generated
     *   sourceType   = architecture pattern selected (e.g. "Layered")
     *   artifacts    = "ml_analysis"
     */
    fun logQualityScan(
        filesAnalyzed: Int,
        totalViolations: Int,
        criticalViolations: Int,
        aiFixCount: Int,
        architecture: String,
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.QUALITY_SCAN,
            projectPath  = project.basePath,
            sourceType   = architecture,
            artifacts    = "ml_analysis",
            filesCount   = filesAnalyzed,
            issuesWarn   = totalViolations,
            issuesError  = criticalViolations,
            insightCount = aiFixCount,
            success      = success,
            errorMsg     = errorMsg,
            durationMs   = durationMs
        )
    )

    /**
     * Runtime Analysis module — logs a stacktrace/error analysis run.
     * Field mapping:
     *   filesCount = 1 (one stacktrace submitted)
     *   artifacts  = "stacktrace"
     *   sourceType = "MANUAL_INPUT"
     */
    fun logRuntimeAnalysis(
        durationMs: Long,
        success: Boolean,
        errorMsg: String? = null
    ) = writeAsync(
        AuditEvent(
            eventType    = AuditEventType.RUNTIME_ANALYSIS,
            projectPath  = project.basePath,
            sourceType   = "MANUAL_INPUT",
            artifacts    = "stacktrace",
            filesCount   = 1,
            success      = success,
            errorMsg     = errorMsg,
            durationMs   = durationMs
        )
    )

    // ── Read API (called from UI, runs on pooled thread via caller) ─────────────

    fun getRecentEvents(limit: Int = 50): List<AuditEvent> {
        val c = getConnection() ?: return emptyList()
        return try {
            c.prepareStatement(SELECT_RECENT).use { ps ->
                ps.setInt(1, limit)
                val rs = ps.executeQuery()
                val list = mutableListOf<AuditEvent>()
                while (rs.next()) {
                    list += AuditEvent(
                        id           = rs.getLong("id"),
                        eventType    = AuditEventType.valueOf(rs.getString("event_type")),
                        projectPath  = rs.getString("project_path"),
                        sourceType   = rs.getString("source_type"),
                        artifacts    = rs.getString("artifacts"),
                        filesCount   = rs.getInt("files_count"),
                        success      = rs.getBoolean("success"),
                        errorMsg     = rs.getString("error_msg"),
                        issuesError  = rs.getInt("issues_error"),
                        issuesWarn   = rs.getInt("issues_warn"),
                        issuesInfo   = rs.getInt("issues_info"),
                        insightCount = rs.getInt("insight_count"),
                        durationMs   = rs.getLong("duration_ms"),
                        createdAt    = rs.getTimestamp("created_at").toInstant()
                    )
                }
                list
            }
        } catch (e: Exception) {
            println("[AuditService] Query failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Returns: eventType → Triple(total, successes, avgDurationMs)
     */
    fun getSummaryStats(): Map<AuditEventType, Triple<Int, Int, Double>> {
        val c = getConnection() ?: return emptyMap()
        return try {
            c.createStatement().use { stmt ->
                val rs = stmt.executeQuery(SELECT_STATS)
                val map = mutableMapOf<AuditEventType, Triple<Int, Int, Double>>()
                while (rs.next()) {
                    val type = runCatching { AuditEventType.valueOf(rs.getString("event_type")) }.getOrNull()
                        ?: continue
                    map[type] = Triple(
                        rs.getInt("total"),
                        rs.getInt("successes"),
                        rs.getDouble("avg_ms")
                    )
                }
                map
            }
        } catch (e: Exception) {
            println("[AuditService] Stats query failed: ${e.message}")
            emptyMap()
        }
    }

    fun clearAllEvents() {
        val c = getConnection() ?: return
        try {
            c.createStatement().use { it.executeUpdate(DELETE_ALL) }
        } catch (e: Exception) {
            println("[AuditService] Clear failed: ${e.message}")
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun writeAsync(event: AuditEvent) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val c = getConnection() ?: return@executeOnPooledThread
            try {
                c.prepareStatement(INSERT_SQL).use { ps ->
                    ps.setString(1,  event.eventType.name)
                    ps.setString(2,  event.projectPath)
                    ps.setString(3,  event.sourceType)
                    ps.setString(4,  event.artifacts)
                    ps.setInt(5,     event.filesCount)
                    ps.setBoolean(6, event.success)
                    ps.setString(7,  event.errorMsg)
                    ps.setInt(8,     event.issuesError)
                    ps.setInt(9,     event.issuesWarn)
                    ps.setInt(10,    event.issuesInfo)
                    ps.setInt(11,    event.insightCount)
                    ps.setLong(12,   event.durationMs)
                    ps.executeUpdate()
                }
                javax.swing.SwingUtilities.invokeLater { onNewEvent?.invoke() }
            } catch (e: Exception) {
                println("[AuditService] Write failed: ${e.message}")
            }
        }
    }

    override fun dispose() {
        try { conn?.close() } catch (_: Exception) {}
        conn = null
    }
}
