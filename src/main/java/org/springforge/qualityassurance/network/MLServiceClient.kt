// src/main/java/org/springforge/qualityassurance/network/MLServiceClient.kt
// REPLACE your existing MLServiceClient.kt with this file
package org.springforge.qualityassurance.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.FixRequest
import org.springforge.qualityassurance.model.FixSuggestion
import org.springforge.qualityassurance.model.ProjectAnalysisRequest
import org.springforge.qualityassurance.model.ProjectFixResult
import org.springforge.qualityassurance.model.SingleFixRequest
import org.springforge.qualityassurance.utils.JsonUtil
import java.util.concurrent.TimeUnit

object MLServiceClient {

    private const val BASE_URL = "http://127.0.0.1:8081"

    // Increased read timeout to 90s to allow Gemini API calls to complete
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)   // ← increased from 60s for Gemini
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Existing methods (unchanged) ──────────────────────────────────────────

    fun analyzeProjectFull(files: List<FileFeatureModel>): CombinedAnalysisResult {
        val requestData = ProjectAnalysisRequest(files = files)
        val json = JsonUtil.toJson(requestData)
        println("🔥 Sending ${files.size} files to /analyze-project-full ...")
        val responseText = post("/analyze-project-full", json)
        println("🟩 Combined analysis complete.")
        return JsonUtil.fromJson(responseText, CombinedAnalysisResult::class.java)
    }

    fun analyzeProject(files: List<FileFeatureModel>): EnhancedPredictionResult {
        val requestData = ProjectAnalysisRequest(files = files)
        val json = JsonUtil.toJson(requestData)
        println("🔥 Analyzing ${files.size} files...")
        val responseText = post("/analyze-project", json)
        return JsonUtil.fromJson(responseText, EnhancedPredictionResult::class.java)
    }

    // ── NEW: Gemini AI fix methods ────────────────────────────────────────────

    /**
     * Calls POST /generate-fixes to get AI fix suggestions for all anti-patterns
     * detected in the project. Called after analyzeProjectFull() completes.
     */
    fun generateProjectFixes(analysisResult: CombinedAnalysisResult): ProjectFixResult {
        val request = FixRequest(
            anti_patterns        = analysisResult.anti_patterns,
            architecture_pattern = analysisResult.architecture_pattern
        )
        val json = JsonUtil.toJson(request)
        println("🤖 Calling Gemini for ${analysisResult.anti_patterns.size} fix suggestions...")
        val responseText = post("/generate-fixes", json)
        println("🟩 Gemini fix suggestions received.")
        return JsonUtil.fromJson(responseText, ProjectFixResult::class.java)
    }

    /**
     * Calls POST /generate-fix to get an AI fix for a single anti-pattern.
     * Can be used for a "Get Fix" button on individual violations.
     */
    fun generateSingleFix(
        antiPatternType : String,
        files           : List<String>,
        architecture    : String,
        layer           : String,
        severity        : String,
        description     : String
    ): FixSuggestion {
        val request = SingleFixRequest(
            anti_pattern         = antiPatternType,
            files                = files,
            architecture_pattern = architecture,
            affected_layer       = layer,
            severity             = severity,
            description          = description
        )
        val json = JsonUtil.toJson(request)
        println("🤖 Calling Gemini for single fix: $antiPatternType ...")
        val responseText = post("/generate-fix", json)
        return JsonUtil.fromJson(responseText, FixSuggestion::class.java)
    }

    // ── Private HTTP helper ───────────────────────────────────────────────────

    private fun post(path: String, json: String): String {
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("ML Service error ${response.code} on $path")
        }
        return response.body?.string()
            ?: throw Exception("Empty response from ML Service on $path")
    }
}