package org.springforge.qualityassurance.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.qualityassurance.model.FeatureModel
import org.springforge.qualityassurance.model.PredictionResult
import org.springforge.qualityassurance.utils.JsonUtil

object MLServiceClient {

    private const val URL = "http://127.0.0.1:8081/predict-antipattern"

    private val client = OkHttpClient()

    fun predict(features: FeatureModel): PredictionResult {

        // Convert to JSON
        val json = JsonUtil.toJson(features)

        // Debug log
        println("🔥 Sending request to ML service...")
        println("🔥 JSON Payload: $json")

        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute()

        val text = response.body?.string() ?: "{}"

        println("🔥 ML Response Received: $text")

        return JsonUtil.fromJson(text, PredictionResult::class.java)
    }
}
