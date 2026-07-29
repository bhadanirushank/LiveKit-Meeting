package com.jbcoder.meeting.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface MeetingApiService {

    @POST("/api/v1/session/bootstrap")
    suspend fun bootstrapSession(@Body request: SessionBootstrapRequest): Response<SessionResponse>

    @POST("/api/v1/session/refresh")
    suspend fun refreshSession(): Response<SessionResponse>

    @POST("/api/v1/meetings/{meetingCode}/join-request")
    suspend fun submitJoinRequest(
        @retrofit2.http.Path("meetingCode") meetingCode: String,
        @Body request: JoinRequestDto
    ): Response<JoinRequestResponse>

    @retrofit2.http.GET("/api/v1/join-requests/{requestId}")
    suspend fun getJoinRequestStatus(
        @retrofit2.http.Path("requestId") requestId: String
    ): Response<JoinRequestStatusResponse>

    @POST("/api/v1/join-requests/{requestId}/livekit-token")
    suspend fun getParticipantLiveKitToken(
        @retrofit2.http.Path("requestId") requestId: String,
        @retrofit2.http.Header("Idempotency-Key") idempotencyKey: String
    ): Response<LiveKitTokenResponse>

    @retrofit2.http.GET("/api/v1/meetings/{meetingCode}/status")
    suspend fun getMeetingStatus(
        @retrofit2.http.Path("meetingCode") meetingCode: String
    ): Response<MeetingStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/leave")
    suspend fun leaveMeeting(
        @retrofit2.http.Path("meetingCode") meetingCode: String,
        @retrofit2.http.Header("Idempotency-Key") idempotencyKey: String
    ): Response<Unit>
}
