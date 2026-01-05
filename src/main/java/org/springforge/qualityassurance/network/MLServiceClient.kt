// MLServiceClient.kt - ENHANCED VERSION
package org.springforge.qualityassurance.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.qualityassurance.model.EnhancedPredictionResult
import org.springforge.qualityassurance.model.FileFeatureModel
import org.springforge.qualityassurance.model.ProjectAnalysisRequest
import org.springforge.qualityassurance.utils.JsonUtil

object MLServiceClient {

    private const val BASE_URL = "http://127.0.0.1:8081"
    private val client = OkHttpClient()

    /**
     * Analyze entire project and get detailed results
     */
    fun analyzeProject(files: List<FileFeatureModel>): EnhancedPredictionResult {

        val requestData = ProjectAnalysisRequest(files = files)
        val json = JsonUtil.toJson(requestData)

        println("🔥 Analyzing ${files.size} files...")
        println("🔥 JSON Payload: ${json.take(500)}...")  // Show first 500 chars

        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/analyze-project")
            .post(body)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("ML Service returned error: ${response.code}")
        }

        val responseText = response.body?.string() ?: "{}"
        println("🔥 ML Response: ${responseText.take(500)}...")

        return JsonUtil.fromJson(responseText, EnhancedPredictionResult::class.java)
    }
}