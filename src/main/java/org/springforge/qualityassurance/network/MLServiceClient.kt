package org.springforge.qualityassurance.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.qualityassurance.model.*
import org.springforge.qualityassurance.network.MLServiceClient.analyzeProjectFull
import org.springforge.qualityassurance.network.MLServiceClient.normaliseArchitecture
import org.springforge.qualityassurance.utils.JsonUtil
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the SpringForge ML Service (FastAPI on port 8081).
 *
 * v2 changes:
 *   - [normaliseArchitecture] helper ensures the architecture string in every
 *     FileFeatureModel payload always matches the exact keys the ML service
 *     expects:  "layered" | "mvc" | "hexagonal" | "clean_architecture"
 *
 *     Previously the plugin passed raw display names like "Layered", "Clean",
 *     "Hexagonal" which did not match the training data labels and produced
 *     incorrect predictions for every non-layered architecture.
 */
object MLServiceClient {

    private const val BASE_URL = "http://127.0.0.1:8000"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // =========================================================================
    //  Combined analysis (main entry point)
    // =========================================================================

    fun analyzeProjectFull(files: List<FileFeatureModel>): CombinedAnalysisResult {
        val normalised = files.map { it.copy(architecture_pattern = normaliseArchitecture(it.architecture_pattern)) }
        val json = JsonUtil.toJson(ProjectAnalysisRequest(files = normalised))
        println("🔥 Sending ${normalised.size} files to /analyze-project-full (arch: ${normalised.firstOrNull()?.architecture_pattern ?: "?"})…")
        val responseText = post("/analyze-project-full", json)
        println("🟩 Combined analysis complete.")
        return JsonUtil.fromJson(responseText, CombinedAnalysisResult::class.java)
    }

    // =========================================================================
    //  Legacy anti-pattern-only analysis
    // =========================================================================

    fun analyzeProject(files: List<FileFeatureModel>): EnhancedPredictionResult {
        val normalised = files.map { it.copy(architecture_pattern = normaliseArchitecture(it.architecture_pattern)) }
        val json = JsonUtil.toJson(ProjectAnalysisRequest(files = normalised))
        println("🔥 Analyzing ${normalised.size} files…")
        val responseText = post("/analyze-project", json)
        return JsonUtil.fromJson(responseText, EnhancedPredictionResult::class.java)
    }

    // =========================================================================
    //  Gemini AI fix suggestions
    // =========================================================================

    /**
     * Calls POST /generate-fixes to get batch AI fix suggestions for all
     * anti-patterns detected by [analyzeProjectFull].
     *
     * @param fileSources optional map of file_name → source_code for context-aware fixes
     */
    fun generateProjectFixes(
        analysisResult: CombinedAnalysisResult,
        fileSources: Map<String, String>? = null
    ): ProjectFixResult {
        val request = FixRequest(
            anti_patterns        = analysisResult.anti_patterns,
            architecture_pattern = normaliseArchitecture(analysisResult.architecture_pattern),
            file_sources         = fileSources
        )
        val json = JsonUtil.toJson(request)
        println("🤖 Calling Gemini for ${analysisResult.anti_patterns.size} fix suggestions…")
        val responseText = post("/generate-fixes", json)
        println("🟩 Gemini fix suggestions received.")
        return JsonUtil.fromJson(responseText, ProjectFixResult::class.java)
    }

    /**
     * Calls POST /generate-fix for a single anti-pattern.
     * Can be used for a per-violation "Get Fix" button in the UI.
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
            architecture_pattern = normaliseArchitecture(architecture),
            affected_layer       = layer,
            severity             = severity,
            description          = description
        )
        val json = JsonUtil.toJson(request)
        println("🤖 Calling Gemini for single fix: $antiPatternType…")
        val responseText = post("/generate-fix", json)
        return JsonUtil.fromJson(responseText, FixSuggestion::class.java)
    }

    // =========================================================================
    //  Architecture normalisation
    // =========================================================================

    /**
     * Converts any architecture string from the plugin UI into the exact key
     * the ML service / training data expects.
     *
     * Accepted inputs (case-insensitive):
     *   "layered"  / "Layered"
     *   "mvc"      / "MVC"
     *   "hexagonal"/ "Hexagonal"
     *   "clean"    / "Clean" / "clean_architecture" / "Clean Architecture"
     *
     * Returns one of: "layered" | "mvc" | "hexagonal" | "clean_architecture"
     * Falls back to "layered" for unknown values (safe default).
     */
    fun normaliseArchitecture(raw: String): String {
        val key = raw.trim().lowercase().replace(" ", "_").replace("-", "_")
        return when {
            key == "layered"                                -> "layered"
            key == "mvc"                                    -> "mvc"
            key == "hexagonal"                              -> "hexagonal"
            key == "clean" || key == "clean_architecture"
                           || key == "cleanarchitecture"   -> "clean_architecture"
            else -> {
                println("⚠️ Unknown architecture '$raw' — defaulting to 'layered'")
                "layered"
            }
        }
    }

    // =========================================================================
    //  Private HTTP helper
    // =========================================================================

    private fun post(path: String, json: String): String {
        val body    = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$BASE_URL$path").post(body).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("ML Service error ${response.code} on $path: ${response.body?.string()}")
        }
        return response.body?.string()
            ?: throw Exception("Empty response from ML Service on $path")
    }
}