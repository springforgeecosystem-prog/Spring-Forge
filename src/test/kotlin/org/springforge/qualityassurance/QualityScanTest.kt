package org.springforge.qualityassurance

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springforge.qualityassurance.model.AntiPatternDetail
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.FixSuggestion

class QualityScanTest {

    // ── Test 1: AntiPatternDetail stores CRITICAL severity correctly ──────────

    @Test
    fun `AntiPatternDetail critical severity field preserved`() {
        val detail = AntiPatternDetail(
            type             = "GOD_CLASS",
            severity         = "CRITICAL",
            affected_layer   = "service",
            confidence       = 0.95,
            files            = listOf("UserService.java"),
            description      = "Class has too many responsibilities",
            recommendation   = "Split into smaller classes"
        )

        assertEquals("CRITICAL", detail.severity)
        assertEquals("GOD_CLASS", detail.type)
        assertEquals(1, detail.files.size)
    }

    // ── Test 2: FixSuggestion ai_powered defaults to false ────────────────────

    @Test
    fun `FixSuggestion ai powered default is false`() {
        val suggestion = FixSuggestion(
            anti_pattern   = "GOD_CLASS",
            layer          = "service",
            severity       = "HIGH",
            recommendation = "Refactor into smaller components"
        )

        assertFalse(suggestion.ai_powered, "ai_powered should default to false")
        assertEquals("", suggestion.gemini_fix)
    }

    // ── Test 3: FileFeatureModel stores all fields correctly ──────────────────

    @Test
    fun `FileFeatureModel all fields set correctly retrieved`() {
        val model = FileFeatureModel(
            file_name              = "UserService.kt",
            file_path              = "src/main/kotlin/service/UserService.kt",
            layer                  = "service",
            loc                    = 120,
            methods                = 8,
            violates_layer_separation = true,
            has_business_logic     = true
        )

        assertEquals("UserService.kt", model.file_name)
        assertEquals("service", model.layer)
        assertEquals(120, model.loc)
        assertEquals(8, model.methods)
        assertTrue(model.violates_layer_separation)
        assertTrue(model.has_business_logic)
    }

    // ── Test 4: CombinedAnalysisResult with empty anti_patterns has 0 violations

    @Test
    fun `CombinedAnalysisResult empty anti patterns total violations is zero`() {
        val result = CombinedAnalysisResult(
            architecture_pattern = "Layered",
            total_files_analyzed = 10,
            total_violations     = 0,
            anti_patterns        = emptyList()
        )

        assertEquals(0, result.total_violations)
        assertTrue(result.anti_patterns.isEmpty())
        assertEquals(10, result.total_files_analyzed)
    }
}
