package com.jbcoder.meeting.data.meeting

import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.MeetingApiService
import com.jbcoder.meeting.network.ProblemDetails
import com.jbcoder.meeting.network.UnauthenticatedMeetingApiService
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val apiService: MeetingApiService,
    private val unauthenticatedApiService: UnauthenticatedMeetingApiService,
    private val json: Json
) : MeetingRepository {

    override suspend fun createMeeting(request: CreateMeetingRequest, installationId: String): Result<CreateMeetingResponse> {
        return try {
            val response = unauthenticatedApiService.createMeeting(installationId, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                val problem = parseProblemDetails(response)
                if (response.code() == 400 && problem?.type == "CREATE_FAILED") {
                    Result.failure(MeetingError.CreateFailed(problem.detail ?: "Create failed"))
                } else if (response.code() >= 500) {
                    Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                } else {
                    Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Result<JoinRequestResponse> {
        return try {
            val response = apiService.submitJoinRequest(meetingCode, request)
            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                val problem = parseProblemDetails(response)
                val error = when (response.code()) {
                    429 -> {
                        val retryAfter = response.headers()["Retry-After"]?.toIntOrNull() ?: 60
                        MeetingError.RateLimited(retryAfter)
                    }
                    409 -> MeetingError.MeetingCapacityReached
                    403 -> MeetingError.MeetingLocked
                    401 -> MeetingError.InvalidCodeOrPasscode
                    else -> MeetingError.UnexpectedServerResponse(response.code().toString())
                }
                Result.failure(error)
            }
        } catch (e: IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    private fun parseProblemDetails(response: Response<*>): ProblemDetails? {
        val errorBody = response.errorBody()?.string() ?: return null
        return try {
            json.decodeFromString(ProblemDetails.serializer(), errorBody)
        } catch (e: Exception) {
            null
        }
    }
}
