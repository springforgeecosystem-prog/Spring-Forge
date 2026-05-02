package org.springforge.cicdassistant

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springforge.cicdassistant.audit.AuditEvent
import org.springforge.cicdassistant.audit.AuditEventType

class AuditTest {

    // ── Test 1: All 6 AuditEventType values exist ─────────────────────────────

    @Test
    fun `AuditEventType all six values exist`() {
        val values = AuditEventType.values()
        assertEquals(6, values.size,
            "Expected 6 event types but found ${values.size}: ${values.map { it.name }}")
    }

    // ── Test 2: All event type labels are non-blank ───────────────────────────

    @Test
    fun `AuditEventType all labels are non blank`() {
        AuditEventType.values().forEach { type ->
            assertTrue(type.label.isNotBlank(),
                "AuditEventType.${type.name} has a blank label")
        }
    }

    // ── Test 3: AuditEvent default values are correct ─────────────────────────

    @Test
    fun `AuditEvent default values are correct`() {
        val event = AuditEvent(eventType = AuditEventType.GENERATION)

        assertEquals(0L,      event.id)
        assertTrue(event.success)
        assertEquals(0,       event.filesCount)
        assertEquals(0,       event.issuesError)
        assertEquals(0,       event.issuesWarn)
        assertEquals(0,       event.issuesInfo)
        assertEquals(0,       event.insightCount)
        assertEquals(0L,      event.durationMs)
        assertNull(event.errorMsg)
        assertNotNull(event.createdAt)
    }

    // ── Test 4: AuditEvent copy with overrides creates a distinct instance ────

    @Test
    fun `AuditEvent copy with overrides creates distinct instance`() {
        val original = AuditEvent(
            eventType  = AuditEventType.CODE_GENERATION,
            success    = true,
            durationMs = 1500L
        )

        val modified = original.copy(success = false, errorMsg = "API key missing")

        // Original is unchanged
        assertTrue(original.success)
        assertNull(original.errorMsg)
        assertEquals(1500L, original.durationMs)

        // Copy reflects overrides
        assertFalse(modified.success)
        assertEquals("API key missing", modified.errorMsg)
        assertEquals(1500L, modified.durationMs)

        // They are separate instances
        assertNotSame(original, modified)
    }
}
