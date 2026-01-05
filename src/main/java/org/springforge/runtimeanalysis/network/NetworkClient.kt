package org.springforge.runtimeanalysis.network

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object NetworkClient {

    private val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()


    private const val ENDPOINT = "http://127.0.0.1:5000/analyze-error"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun analyzeError(jsonPayload: String): AnalysisResponse {
        val body = jsonPayload.toRequestBody(JSON)

        val request = Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Request failed: ${response.code}")
            }

            val json = response.body?.string() ?: "{}"
            return Gson().fromJson(json, AnalysisResponse::class.java)
        }
    }

}
