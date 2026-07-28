package com.jbcoder.meeting.network

import kotlinx.serialization.Serializable

@Serializable
data class SessionBootstrapRequest(
    val deviceSessionId: String
)

@Serializable
data class SessionResponse(
    val mobileSessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiry: String,
    val refreshExpiry: String
)

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null
)
