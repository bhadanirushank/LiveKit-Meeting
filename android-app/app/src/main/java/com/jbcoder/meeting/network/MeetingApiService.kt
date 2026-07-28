package com.jbcoder.meeting.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface MeetingApiService {

    @POST("/api/v1/session/bootstrap")
    suspend fun bootstrapSession(@Body request: SessionBootstrapRequest): Response<SessionResponse>

    @POST("/api/v1/session/refresh")
    suspend fun refreshSession(): Response<SessionResponse>
}
