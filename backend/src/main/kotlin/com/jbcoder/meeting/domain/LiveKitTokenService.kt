package com.jbcoder.meeting.domain

import com.jbcoder.meeting.configuration.AppConfig
import io.livekit.server.AccessToken

object LiveKitTokenService {

    /**
     * Generates a short-lived LiveKit connection token for a participant.
     */
    fun createToken(
        roomName: String,
        participantIdentity: String,
        participantName: String,
        metadata: String = "",
        role: String = "PARTICIPANT",
        publishRestricted: Boolean = false,
        screenShareAllowed: Boolean = false
    ): String {
        val config = AppConfig.load()
        val token = AccessToken(config.livekitKey, config.livekitSecret)
        
        // Apply strictly scoped grants
        LiveKitPermissionMapper.applyGrants(token, roomName, role, publishRestricted, screenShareAllowed)
        
        token.identity = participantIdentity
        token.name = participantName
        if (metadata.isNotBlank()) {
            token.metadata = metadata
        }
        
        // Explicitly set short TTL (e.g. 5 minutes)
        // LiveKit Java SDK treats ttl as seconds for some, but if it expects seconds and we pass 300, wait, auth0 jwt expiresAt is in milliseconds.
        // The LiveKit server SDK Java 'ttl' property might be in SECONDS or MILLISECONDS. Let's set it to 5 * 60 * 1000 (300,000 ms)
        token.ttl = 300L * 1000L
        
        return token.toJwt()
    }
}
