package org.springforge.subscription

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springforge.cicdassistant.config.EnvironmentConfig
import java.util.concurrent.TimeUnit

/**
 * Application-level singleton managing the current user's subscription status.
 * Fetched from backend after login, reset on logout.
 */
@Service(Service.Level.APP)
class SubscriptionManager {

    private val LOG = Logger.getInstance(SubscriptionManager::class.java)
    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    var status: SubscriptionStatus = SubscriptionStatus()
        private set

    /** Called on EDT after any status change. Register from the tool window header. */
    var onStatusChanged: (() -> Unit)? = null

    fun canMakeRequest(): Boolean = status.canMakeRequest()

    private fun notifyChanged() {
        val cb = onStatusChanged ?: return
        javax.swing.SwingUtilities.invokeLater { cb() }
    }

    /**
     * Fetches subscription status from GET /api/subscription/status.
     * Called after login. Falls back to Community defaults on error.
     */
    fun fetchStatus(token: String) {
        try {
            val url = "${baseUrl()}/api/subscription/status"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LOG.warn("[SpringForge] Subscription status fetch failed: ${resp.code} — defaulting to Community")
                    return
                }
                val body = resp.body?.string() ?: return
                val parsed = mapper.readTree(body)
                val tier = when (parsed["tier"]?.asText()?.uppercase()) {
                    "ULTIMATE" -> SubscriptionTier.ULTIMATE
                    else -> SubscriptionTier.COMMUNITY
                }
                val used = parsed["requestsUsed"]?.asInt() ?: 0
                val limit = if (tier == SubscriptionTier.ULTIMATE) Int.MAX_VALUE
                            else (parsed["requestsLimit"]?.asInt() ?: 5)
                val resetAt = parsed["resetAt"]?.asText() ?: ""
                val expiresAt = parsed["expiresAt"]?.asText() ?: ""
                status = SubscriptionStatus(tier, used, limit, resetAt, expiresAt)
                LOG.info("[SpringForge] Subscription loaded: $tier, $used/${if (limit == Int.MAX_VALUE) "∞" else limit.toString()} requests")
                notifyChanged()
            }
        } catch (e: Exception) {
            LOG.warn("[SpringForge] Could not fetch subscription status: ${e.message} — defaulting to Community")
        }
    }

    /**
     * Increments usage count on the backend and updates local count.
     * Called after a successful AI request in any panel.
     * No-op if ULTIMATE or backend call fails.
     */
    fun incrementUsage(token: String) {
        if (status.tier == SubscriptionTier.ULTIMATE) return
        try {
            val url = "${baseUrl()}/api/subscription/usage/increment"
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val used = body?.let { mapper.readTree(it)["requestsUsed"]?.asInt() }
                        ?: (status.requestsUsed + 1)
                    status = status.copy(requestsUsed = used)
                    LOG.info("[SpringForge] Usage incremented: ${status.requestsUsed}/${status.requestsLimit}")
                    notifyChanged()
                }
            }
        } catch (e: Exception) {
            // Increment locally even if backend call fails
            status = status.copy(requestsUsed = status.requestsUsed + 1)
            LOG.warn("[SpringForge] Could not increment usage on backend, incremented locally: ${e.message}")
            notifyChanged()
        }
    }

    /**
     * Re-fetches subscription status. Called after user completes Stripe checkout.
     */
    fun refreshStatus(token: String) = fetchStatus(token)

    /**
     * Resets to Community defaults on logout.
     */
    fun reset() {
        status = SubscriptionStatus()
        LOG.info("[SpringForge] Subscription status reset to Community")
        notifyChanged()
    }

    private fun baseUrl(): String = EnvironmentConfig.Backend.url.trimEnd('/')

    companion object {
        fun getInstance(): SubscriptionManager =
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(SubscriptionManager::class.java)
    }
}
