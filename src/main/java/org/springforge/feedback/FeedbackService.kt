package org.springforge.feedback

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.auth.SessionManager
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.util.concurrent.TimeUnit

/**
 * HTTP client for submitting user feedback to the SpringForge backend.
 */
object FeedbackService {

    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(FeedbackService::class.java)
    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String = EnvironmentConfig.Backend.url.trimEnd('/')

    /**
     * POST /api/feedback
     * @param rating 1-5 star rating
     * @param comment user comment text
     * @param module one of: code-generation, quality-assurance, runtime-analysis, cicd-assistant
     * @throws RuntimeException on failure
     */
    fun submitFeedback(rating: Int, comment: String, module: String) {
        val token = SessionManager.getInstance().token
            ?: throw RuntimeException("Not logged in — cannot submit feedback")

        val payload = mutableMapOf<String, Any>(
            "rating" to rating,
            "comment" to comment
        )
        if (module.isNotBlank()) {
            payload["module"] = module
        }

        val body = mapper.writeValueAsString(payload)
        val url = "${baseUrl()}/api/feedback"
        LOG.warn("[SpringForge-Feedback] POST $url")
        LOG.warn("[SpringForge-Feedback] Payload: $body")
        System.err.println("[SpringForge-Feedback] POST $url | Payload: $body")

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: ""
            LOG.warn("[SpringForge-Feedback] Response: ${resp.code} $respBody")
            System.err.println("[SpringForge-Feedback] Response: ${resp.code} $respBody")
            if (!resp.isSuccessful) {
                val error = try {
                    mapper.readTree(respBody)["message"]?.asText()
                } catch (_: Exception) { null }
                val msg = error ?: "HTTP ${resp.code}"
                LOG.warn("[SpringForge-Feedback] FAILED: $msg | Full body: $respBody")
                System.err.println("[SpringForge-Feedback] FAILED: $msg | Full body: $respBody")
                throw RuntimeException("$msg\n(HTTP ${resp.code} from $url)\nResponse: $respBody")
            }
        }
    }
}
