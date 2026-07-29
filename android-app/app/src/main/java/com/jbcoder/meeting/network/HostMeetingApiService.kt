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

    @POST("/api/v1/meetings/{meetingCode}/lock")
    suspend fun lockMeeting(
        @Path("meetingCode") meetingCode: String,
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<LockResponse>

    @POST("/api/v1/meetings/{meetingCode}/unlock")
    suspend fun unlockMeeting(
        @Path("meetingCode") meetingCode: String,
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<LockResponse>

    @POST("/api/v1/meetings/{meetingCode}/end")
    suspend fun endMeeting(
        @Path("meetingCode") meetingCode: String,
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String
    ): Response<MeetingStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/remove")
    suspend fun removeParticipant(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/mute")
    suspend fun muteParticipant(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/ask-to-unmute")
    suspend fun askToUnmute(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/disable-publishing")
    suspend fun disablePublishing(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/restore-publishing")
    suspend fun restorePublishing(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/promote-cohost")
    suspend fun promoteCohost(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>

    @POST("/api/v1/meetings/{meetingCode}/participants/{participantId}/demote-cohost")
    suspend fun demoteCohost(
        @Path("meetingCode") meetingCode: String,
        @Path("participantId") participantId: String
    ): Response<ParticipantStatusResponse>
}
