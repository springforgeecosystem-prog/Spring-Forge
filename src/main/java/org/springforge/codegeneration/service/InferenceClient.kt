package org.springforge.codegeneration.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class InferenceClient(private val serverUrl: String = "http://127.0.0.1:8000") {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()

    fun predictArchitecture(payload: Any): String? {
        val json = mapper.writeValueAsString(payload)
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val req = Request.Builder().url("$serverUrl/predict_architecture").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Server error: ${resp.code}")
            val node = mapper.readTree(resp.body!!.string())
            return node["predicted_architecture"]?.asText()
        }
    }

    fun generateCode(payload: Any): List<Map<String, String>> {
        val json = mapper.writeValueAsString(payload)
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), json)
        val req = Request.Builder().url("$serverUrl/generate_code").post(body).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("Server error: ${resp.code}")
            val node = mapper.readTree(resp.body!!.string())
            // expect {"files":[ {"path":"...", "content":"..."} ]}
            val files = node["files"]
            val list = mutableListOf<Map<String, String>>()
            files.forEach { f -> list.add(mapOf("path" to f["path"].asText(), "content" to f["content"].asText())) }
            return list
        }
    }
}
