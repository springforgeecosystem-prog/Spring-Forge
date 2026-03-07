// src/main/java/org/springforge/qualityassurance/model/FixSuggestion.kt
// NEW FILE — add to: src/main/java/org/springforge/qualityassurance/model/
package org.springforge.qualityassurance.model

/**
 * One AI-powered fix suggestion for a single anti-pattern.
 * Mirrors the Python FixSuggestion Pydantic schema exactly.
 */
data class FixSuggestion(
    val anti_pattern  : String       = "",
    val layer         : String       = "",
    val severity      : String       = "",
    val impact_points : Int          = 0,
    val problem       : String       = "",
    val recommendation: String       = "",   // static best-practice text, always present
    val files         : List<String> = emptyList(),
    val before_code   : String       = "",   // static ❌ BEFORE code example
    val after_code    : String       = "",   // static ✅ AFTER code example
    val gemini_fix    : String       = "",   // AI-generated file-specific text (may be "")
    val ai_powered    : Boolean      = false // true when Gemini responded successfully
)

/** Full project fix result returned by POST /generate-fixes. */
data class ProjectFixResult(
    val architecture_pattern : String              = "",
    val total_fixes          : Int                 = 0,
    val suggestions          : List<FixSuggestion> = emptyList()
)

/** Request body for POST /generate-fix (single violation). */
data class SingleFixRequest(
    val anti_pattern         : String       = "",
    val files                : List<String> = emptyList(),
    val architecture_pattern : String       = "layered",
    val affected_layer       : String       = "unknown",
    val severity             : String       = "MEDIUM",
    val description          : String       = ""
)

/** Request body for POST /generate-fixes (full project batch). */
data class FixRequest(
    val anti_patterns        : List<AntiPatternDetail> = emptyList(),
    val architecture_pattern : String                  = "layered",
    // v3: optional source code map for context-aware fix generation
    val file_sources         : Map<String, String>?    = null
)