package com.jbcoder.meeting.network

import kotlinx.serialization.Serializable

@Serializable
data class SessionBootstrapRequest(
    val installationId: String,
    val platform: String
)

@Serializable
data class SessionResponse(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String? = null,
    val refreshTokenExpiresAt: String? = null
)

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null
)
