package com.jbcoder.meeting.security

import kotlinx.serialization.Serializable

@Serializable
data class MobileSessionData(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiry: Long,
    val refreshExpiry: Long
)

interface SecureSessionStorage {
    suspend fun saveSession(data: MobileSessionData)
    suspend fun loadSession(): MobileSessionData?
    suspend fun clearSession()
}
