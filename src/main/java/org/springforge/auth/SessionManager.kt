package org.springforge.auth

import com.intellij.openapi.components.Service
import org.springforge.subscription.SubscriptionManager

/**
 * Application-level singleton that holds the current user session.
 * Lives for the entire IDE lifetime — all projects share the same login state.
 */
@Service(Service.Level.APP)
class SessionManager {

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var currentUser: UserInfo? = null
        private set

    val isLoggedIn: Boolean
        get() = token != null && currentUser != null

    fun login(token: String, user: UserInfo) {
        this.token = token
        this.currentUser = user
        // Fetch subscription status in background after login
        Thread {
            SubscriptionManager.getInstance().fetchStatus(token)
        }.start()
    }

    fun logout() {
        token = null
        currentUser = null
        SubscriptionManager.getInstance().reset()
    }

    companion object {
        fun getInstance(): SessionManager =
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(SessionManager::class.java)
    }
}

data class UserInfo(
    val id: Int,
    val email: String,
    val fullName: String
)
