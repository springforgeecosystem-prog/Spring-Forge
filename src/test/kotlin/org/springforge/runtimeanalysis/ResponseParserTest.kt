package org.springforge.runtimeanalysis

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springforge.runtimeanalysis.ui.ResponseParser

class ResponseParserTest {

    // ── Helper: build a well-formed JSON response ─────────────────────────────

    private fun buildJson(
        answer: String,
        docs: String = "[]"
    ) = """{"answer": ${jsonStr(answer)}, "retrieved_docs": $docs}"""

    private fun jsonStr(s: String) = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

    // ── Test 1: Full valid JSON extracts all sections ─────────────────────────

    @Test
    fun `parse full valid json extracts all sections`() {
        val answer = """
            Error: NullPointerException at line 42
            Root Cause: The user object was not initialised before use
            Suggested Fix: Add a null check before calling getUserId()
            ```java
            if (user != null) { user.getId(); }
            ```
            Notes: Consider using Optional<User> to avoid null checks
        """.trimIndent()

        val json = buildJson(answer)
        val result = ResponseParser.parse(json)

        assertTrue(result.errorSummary.isNotBlank(), "errorSummary should not be blank")
        assertTrue(result.rootCause.isNotBlank(),    "rootCause should not be blank")
        assertTrue(result.suggestedFix.isNotBlank(), "suggestedFix should not be blank")
        assertNotNull(result.notes)
    }

    // ── Test 2: Answer with no section headers returns empty strings ──────────

    @Test
    fun `parse json missing answer sections returns empty strings`() {
        val json = buildJson("This is just a plain text answer with no section headers.")

        val result = ResponseParser.parse(json)

        assertEquals("", result.errorSummary)
        assertEquals("", result.rootCause)
        assertEquals("", result.suggestedFix)
    }

    // ── Test 3: Java code block is extracted correctly ────────────────────────

    @Test
    fun `parse json with java code block extracts code correctly`() {
        val answer = """
            Error: CompilationError
            Root Cause: Missing return type
            Suggested Fix: Add return type
            ```java
            public String getName() { return this.name; }
            ```
        """.trimIndent()

        val json = buildJson(answer)
        val result = ResponseParser.parse(json)

        assertTrue(
            result.codeSnippet.contains("getName"),
            "Code snippet should contain 'getName', got: '${result.codeSnippet}'"
        )
    }

    // ── Test 4: retrieved_docs array parses into Reference list ──────────────

    @Test
    fun `parse json with retrieved docs parses references`() {
        val docs = """
            [
                {"title": "Spring Boot Docs", "url": "https://spring.io/docs"},
                {"title": "Kotlin Null Safety", "url": "https://kotlinlang.org/docs/null-safety.html"}
            ]
        """.trimIndent()

        val json = buildJson("Error: Something went wrong", docs)
        val result = ResponseParser.parse(json)

        assertEquals(2, result.references.size)
        assertEquals("Spring Boot Docs", result.references[0].title)
        assertEquals("https://spring.io/docs", result.references[0].url)
        assertEquals("Kotlin Null Safety", result.references[1].title)
    }
}
