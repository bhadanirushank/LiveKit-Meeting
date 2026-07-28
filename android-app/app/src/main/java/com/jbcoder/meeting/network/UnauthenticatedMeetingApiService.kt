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
}
