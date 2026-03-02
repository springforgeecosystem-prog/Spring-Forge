// MLServiceClient.kt
package org.springforge.qualityassurance.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.qualityassurance.model.CombinedAnalysisResult
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.ProjectAnalysisRequest
import org.springforge.qualityassurance.utils.JsonUtil
import java.util.concurrent.TimeUnit

object MLServiceClient {

    private const val BASE_URL = "http://127.0.0.1:8081"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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