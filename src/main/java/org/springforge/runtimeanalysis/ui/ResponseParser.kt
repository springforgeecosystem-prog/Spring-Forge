package org.springforge.runtimeanalysis.ui

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Parser for SpringForge API response
 * Extracts structured data from the "answer" field
 */
object ResponseParser {

    data class ParsedResponse(
            val errorSummary: String,
            val rootCause: String,
            val suggestedFix: String,
            val codeSnippet: String,
            val references: List<Reference>,
            val notes: String?
    )

    /**
     * Parse the JSON response from the backend
     *
     * Expected JSON structure:
     * {
     *   "answer": "Error:\n...\n\nRoot Cause:\n...\n\nSuggested Fix:\n...\n\n```java\n...\n```\n\nReferences:\n...\n\nNotes:\n...",
     *   "retrieved_docs": [{"title": "...", "url": "..."}]
     * }
     */
    fun parse(jsonResponse: String): ParsedResponse {
        val gson = Gson()
        val jsonObject = gson.fromJson(jsonResponse, JsonObject::class.java)

        val answer = jsonObject.get("answer").asString
        val retrievedDocs = jsonObject.getAsJsonArray("retrieved_docs")

        // Extract sections from answer
        val errorSummary = extractSection(answer, "Error:", "Root Cause:")
        val rootCause = extractSection(answer, "Root Cause:", "Suggested Fix:")
        val suggestedFix = extractSection(answer, "Suggested Fix:", "```")
        val codeSnippet = extractCodeBlock(answer)
        val notes = extractNotes(answer)

        // Parse references from retrieved_docs
        val references = mutableListOf<Reference>()
        retrievedDocs?.forEach { doc ->
            val docObj = doc.asJsonObject
            references.add(
                    Reference(
                            title = docObj.get("title").asString,
                            url = docObj.get("url").asString
                    )
            )
        }

        return ParsedResponse(
                errorSummary = errorSummary.trim(),
                rootCause = rootCause.trim(),
                suggestedFix = suggestedFix.trim(),
                codeSnippet = codeSnippet.trim(),
                references = references,
                notes = notes?.trim()
        )
    }

    private fun extractSection(text: String, startMarker: String, endMarker: String): String {
        val startIndex = text.indexOf(startMarker)
        if (startIndex == -1) return ""

        val contentStart = startIndex + startMarker.length
        val endIndex = text.indexOf(endMarker, contentStart)

        return if (endIndex == -1) {
            text.substring(contentStart)
        } else {
            text.substring(contentStart, endIndex)
        }
    }

    private fun extractCodeBlock(text: String): String {
        val codeBlockRegex = "```(?:java|kotlin)?\\s*([\\s\\S]*?)```".toRegex()
        val match = codeBlockRegex.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun extractNotes(text: String): String? {
        val notesIndex = text.indexOf("Notes:")
        if (notesIndex == -1) return null

        val contentStart = notesIndex + "Notes:".length
        val referencesIndex = text.indexOf("References:", contentStart)

        val notesText = if (referencesIndex == -1) {
            text.substring(contentStart)
        } else {
            text.substring(contentStart, referencesIndex)
        }

        return notesText.trim().takeIf { it.isNotEmpty() }
    }
}
