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

@Serializable
data class CreateMeetingRequest(
    val title: String,
    val passcode: String? = null,
    val waitingRoomEnabled: Boolean = true,
    val joinBeforeHostEnabled: Boolean = false,
    val maximumParticipants: Int = 100,
    val idempotencyKey: String
)

@Serializable
data class CreateMeetingResponse(
    val publicMeetingCode: String,
    val hostSecret: String,
    val livekitRoomName: String,
    val title: String,
    val waitingRoomEnabled: Boolean,
    val joinBeforeHostEnabled: Boolean,
    val maximumParticipants: Int
)

@Serializable
data class JoinRequestDto(
    val displayName: String,
    val deviceSessionId: String,
    val passcode: String? = null
)

@Serializable
data class JoinRequestResponse(
    val requestId: String,
    val status: String
)
