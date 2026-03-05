package org.springforge.cicdassistant.audit

enum class AuditEventType(val label: String) {
    GENERATION("Generation"),
    VALIDATION("Validation"),
    EXPLAINABILITY("Explainability"),
    CODE_GENERATION("Code Gen"),
    QUALITY_SCAN("Quality"),
    RUNTIME_ANALYSIS("Runtime")
}
