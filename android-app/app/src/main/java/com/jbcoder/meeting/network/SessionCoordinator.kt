package com.jbcoder.meeting.network

import com.jbcoder.meeting.security.MobileSessionData
import com.jbcoder.meeting.security.SecureSessionStorage
import com.jbcoder.meeting.storage.InstallationIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

enum class SessionState {
    INITIALIZING,
    READY,
    ERROR,
    OFFLINE
}

@Singleton
class SessionCoordinator @Inject constructor(
    private val apiService: MeetingApiService,
    private val secureSessionStorage: SecureSessionStorage,
    private val installationIdProvider: InstallationIdProvider
) {
    private val _sessionState = MutableStateFlow(SessionState.INITIALIZING)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun initializeSession() {
        try {
            _sessionState.value = SessionState.INITIALIZING
            
            // Load credentials
            var session = secureSessionStorage.loadSession()
            val now = System.currentTimeMillis() / 1000

            if (session == null || isExpired(session.refreshExpiry, now)) {
                // No session or refresh token expired -> Bootstrap
                session = bootstrapNewSession()
            } else if (isExpired(session.accessExpiry, now)) {
                // Access expired, refresh valid -> Refresh
                session = performRefresh()
            }

            if (session != null) {
                _sessionState.value = SessionState.READY
            } else {
                _sessionState.value = SessionState.ERROR
            }
        } catch (e: Exception) {
            // Check if it's network error vs unknown
            _sessionState.value = SessionState.OFFLINE
        }
    }

    private suspend fun bootstrapNewSession(): MobileSessionData? {
        secureSessionStorage.clearSession()
        val installationId = installationIdProvider.getInstallationId()
        
        val response = apiService.bootstrapSession(SessionBootstrapRequest(installationId, "android"))
        if (response.isSuccessful && response.body() != null) {
            val body = response.body()!!
            val newSession = MobileSessionData(
                sessionId = body.sessionId,
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
                accessExpiry = body.accessTokenExpiresAt?.let { parseIso8601ToEpoch(it) } ?: ((System.currentTimeMillis() / 1000) + 3600),
                refreshExpiry = body.refreshTokenExpiresAt?.let { parseIso8601ToEpoch(it) } ?: ((System.currentTimeMillis() / 1000) + (86400 * 30))
            )
            secureSessionStorage.saveSession(newSession)
            return newSession
        }
        return null
    }

    suspend fun performRefresh(): MobileSessionData? = refreshMutex.withLock {
        // Double-check if another thread already refreshed
        val currentSession = secureSessionStorage.loadSession()
        val now = System.currentTimeMillis() / 1000
        
        if (currentSession != null && !isExpired(currentSession.accessExpiry, now)) {
            return currentSession // Already refreshed
        }

        // We can't use apiService directly here if it's protected by the interceptor, 
        // but apiService.refreshSession() is mapped in Retrofit. 
        // The interceptor must pass the RTK as bearer.
        try {
            val response = apiService.refreshSession()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val newSession = MobileSessionData(
                    sessionId = body.sessionId,
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken,
                    accessExpiry = body.accessTokenExpiresAt?.let { parseIso8601ToEpoch(it) } ?: ((System.currentTimeMillis() / 1000) + 3600),
                    refreshExpiry = body.refreshTokenExpiresAt?.let { parseIso8601ToEpoch(it) } ?: ((System.currentTimeMillis() / 1000) + (86400 * 30))
                )
                secureSessionStorage.saveSession(newSession)
                _sessionState.value = SessionState.READY
                return newSession
            } else {
                // Refresh failed (terminally invalid)
                return bootstrapNewSession()
            }
        } catch (e: Exception) {
            // Network failure during refresh
            return null
        }
    }

    suspend fun getAccessToken(): String? {
        val session = secureSessionStorage.loadSession() ?: return null
        val now = System.currentTimeMillis() / 1000

        if (isExpired(session.accessExpiry, now)) {
            val newSession = performRefresh() ?: return null
            return newSession.accessToken
        }
        return session.accessToken
    }
    
    suspend fun getRefreshToken(): String? {
        return secureSessionStorage.loadSession()?.refreshToken
    }

    suspend fun getDeviceSessionId(): String? {
        return secureSessionStorage.loadSession()?.sessionId
    }

    private fun isExpired(expiryTimeEpoch: Long, now: Long): Boolean {
        // Add 10s skew
        return (expiryTimeEpoch - 10) < now
    }

    private fun parseIso8601ToEpoch(iso8601: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val date = format.parse(iso8601)
            if (date != null) date.time / 1000 else (System.currentTimeMillis() / 1000) + 3600
        } catch (e: Exception) {
            // fallback if parsing fails, assume 1 hour valid
            (System.currentTimeMillis() / 1000) + 3600
        }
    }
}
