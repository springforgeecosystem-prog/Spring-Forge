package org.springforge.subscription

data class SubscriptionStatus(
    val tier: SubscriptionTier = SubscriptionTier.COMMUNITY,
    val requestsUsed: Int = 0,
    val requestsLimit: Int = 5,
    val resetAt: String = "",
    val expiresAt: String = ""   // non-blank = cancellation pending, subscription ends on this date
) {
    fun canMakeRequest(): Boolean =
        tier == SubscriptionTier.ULTIMATE || requestsUsed < requestsLimit

    fun usageDisplay(): String =
        if (tier == SubscriptionTier.ULTIMATE) "Unlimited" else "$requestsUsed/$requestsLimit requests"
}
