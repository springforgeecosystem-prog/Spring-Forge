package org.springforge.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.util.concurrent.TimeUnit

/**
 * HTTP client for SpringForge backend authentication endpoints.
 */
object AuthService {

    private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(AuthService::class.java)
    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String = EnvironmentConfig.Backend.url.trimEnd('/')

    /**
     * POST /api/auth/login
     * @return [LoginResponse] on success
     * @throws AuthException on failure
     */
    fun login(email: String, password: String): LoginResponse {
        val url = "${baseUrl()}/api/auth/login"
        LOG.info("[SpringForge] Login request to $url for $email")
        val body = mapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: throw AuthException("Empty response from server")
            LOG.info("[SpringForge] Login response: ${resp.code}")
            if (!resp.isSuccessful) {
                val error = try {
                    mapper.readTree(respBody)["message"]?.asText()
                } catch (_: Exception) { null }
                val msg = error ?: "Login failed (${resp.code})"
                LOG.warn("[SpringForge] Login failed: $msg")
                throw AuthException(msg)
            }
            return mapper.readValue(respBody)
        }
    }

    /**
     * GET /api/auth/me — verify token is still valid.
     * @return [UserInfo] on success
     * @throws AuthException if token is invalid/expired
     */
    fun verifyToken(token: String): UserInfo {
        val url = "${baseUrl()}/api/auth/me"
        LOG.info("[SpringForge] Verifying token at $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { resp ->
            val respBody = resp.body?.string() ?: throw AuthException("Empty response from server")
            LOG.info("[SpringForge] Token verify response: ${resp.code}")
            if (!resp.isSuccessful) {
                val error = try {
                    mapper.readTree(respBody)["message"]?.asText()
                } catch (_: Exception) { null }
                val msg = error ?: "Token verification failed (${resp.code})"
                LOG.warn("[SpringForge] Token verify failed: $msg")
                throw AuthException(msg)
            }
            return mapper.readValue(respBody)
        }
    }
}

data class LoginResponse(
    val token: String,
    val user: UserInfo
)

class AuthException(message: String) : RuntimeException(message)
