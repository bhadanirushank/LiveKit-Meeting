package com.jbcoder.meeting.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface UnauthenticatedMeetingApiService {
    @POST("/api/v1/meetings")
    suspend fun createMeeting(
        @Header("X-Installation-Id") installationId: String,
        @Body request: CreateMeetingRequest
    ): Response<CreateMeetingResponse>

    @POST("/api/v1/host-sessions/exchange")
    suspend fun exchangeHostSession(
        @Header("X-Installation-Id") installationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: HostExchangeRequest
    ): Response<HostExchangeResponse>
}
