package org.springforge.codegeneration.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

class ArchitecturePredictor(
    private val serverUrl: String = "http://localhost:8000"
) {

    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun predict(features: Map<String, Int>): ArchitectureResult? {
        try {
            val payload = mapOf("data" to features)

            val json = mapper.writeValueAsString(payload)
            val body = RequestBody.create("application/json".toMediaType(), json)

            val req = Request.Builder()
                .url("$serverUrl/predict")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    println("SpringForge ML Server Error: ${resp.code}")
                    return null
                }

                val node = mapper.readTree(resp.body!!.string())

                return ArchitectureResult(
                    predicted = node["predicted_architecture"].asText(),
                    confidence = node["confidence"].asDouble(),
                    probabilities = node["probabilities"].fields().asSequence()
                        .associate { it.key to it.value.asDouble() }
                )
            }

        } catch (ex: Exception) {
            println("ML prediction failed: ${ex.message}")
            return null
        }
    }
}

data class ArchitectureResult(
    val predicted: String,
    val confidence: Double,
    val probabilities: Map<String, Double>
)
