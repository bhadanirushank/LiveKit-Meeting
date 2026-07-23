package com.jbcoder.meeting.infrastructure

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jbcoder.meeting.configuration.AppConfig
import java.util.Date

object JwtService {
    private val config = AppConfig.load()
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    fun generateHostSessionToken(meetingId: String, sessionIdentifier: String, expiryMs: Long): String {
        return JWT.create()
            .withIssuer("jbcoder-meeting")
            .withClaim("meetingId", meetingId)
            .withClaim("sessionId", sessionIdentifier)
            .withClaim("role", "HOST")
            .withExpiresAt(Date(System.currentTimeMillis() + expiryMs))
            .sign(algorithm)
    }

    fun verify(token: String) = JWT.require(algorithm)
        .withIssuer("jbcoder-meeting")
        .build()
        .verify(token)
}
