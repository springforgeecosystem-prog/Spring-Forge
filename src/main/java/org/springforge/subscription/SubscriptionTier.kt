package org.springforge.subscription

enum class SubscriptionTier {
    COMMUNITY,
    ULTIMATE;

    fun isCommunity() = this == COMMUNITY
    fun isUltimate() = this == ULTIMATE
}
