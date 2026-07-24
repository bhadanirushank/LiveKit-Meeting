package com.jbcoder.meeting

import java.security.SecureRandom
import java.util.Base64

object TestSecrets {
    val liveKitApiSecret: String by lazy {
        "livekit_secret_placeholder_at_least_32_chars"
    }

    val liveKitApiKey: String by lazy {
        "devkey"
    }

    val jwtSecret: String by lazy {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    fun setupTestProperties() {
        val testDbName = System.getenv("POSTGRES_DB") ?: "livekit_meeting"
        System.setProperty("PORT", "8081")
        System.setProperty("POSTGRES_DB", testDbName)
        System.setProperty("POSTGRES_USER", System.getenv("POSTGRES_USER") ?: "postgres")
        System.setProperty("POSTGRES_PASSWORD", System.getenv("POSTGRES_PASSWORD") ?: "postgres_password_placeholder")
        System.setProperty("POSTGRES_HOST", "localhost")
        System.setProperty("POSTGRES_PORT", "5432")
        System.setProperty("REDIS_HOST", "localhost")
        System.setProperty("REDIS_PORT", "6379")
        System.setProperty("REDIS_PASSWORD", "redis_password_placeholder")
        System.setProperty("LIVEKIT_API_KEY", liveKitApiKey)
        System.setProperty("LIVEKIT_API_SECRET", liveKitApiSecret)
        System.setProperty("JWT_SECRET", jwtSecret)
        System.setProperty("JWT_ISSUER", "livekit-meeting-app")
        System.setProperty("JWT_AUDIENCE", "livekit-meeting-app")
        System.setProperty("RATE_LIMIT", "10000")
        System.setProperty("DB_MAX_POOL_SIZE", "3")
    }
}
