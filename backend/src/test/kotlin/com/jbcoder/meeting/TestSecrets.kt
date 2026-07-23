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
        System.setProperty("PORT", "8081")
        System.setProperty("DB_URL", "jdbc:postgresql://localhost:15432/livekit_meeting")
        System.setProperty("DB_USER", "postgres")
        System.setProperty("DB_PASSWORD", "postgres")
        System.setProperty("REDIS_HOST", "localhost")
        System.setProperty("REDIS_PORT", "16379")
        System.setProperty("REDIS_PASSWORD", "redis_password_placeholder")
        System.setProperty("LIVEKIT_API_KEY", liveKitApiKey)
        System.setProperty("LIVEKIT_API_SECRET", liveKitApiSecret)
        System.setProperty("JWT_SECRET", jwtSecret)
        System.setProperty("JWT_ISSUER", "livekit-meeting-app")
        System.setProperty("JWT_AUDIENCE", "livekit-meeting-app")
    }
}
