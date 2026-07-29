package com.jbcoder.meeting.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface HostMeetingApiService {

    @GET("/api/v1/meetings/{meetingCode}/waiting-room")
    suspend fun getWaitingRoom(
        @Path("meetingCode") meetingCode: String
    ): Response<WaitingRoomResponse>

    @POST("/api/v1/meetings/{meetingCode}/waiting-room/{requestId}/admit")
    suspend fun admitParticipant(
        @Path("meetingCode") meetingCode: String,
        @Path("requestId") requestId: String,
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<AdmitResponse>

    @POST("/api/v1/meetings/{meetingCode}/waiting-room/{requestId}/reject")
    suspend fun rejectParticipant(
        @Path("meetingCode") meetingCode: String,
        @Path("requestId") requestId: String,
        @Body request: RejectRequest
    ): Response<RejectResponse>

    @POST("/api/v1/meetings/{meetingCode}/start")
    suspend fun startMeeting(
        @Path("meetingCode") meetingCode: String,
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<StartMeetingResponse>

    @POST("/api/v1/host-sessions/livekit-token")
    suspend fun getHostLiveKitToken(): Response<LiveKitTokenResponse>
}
