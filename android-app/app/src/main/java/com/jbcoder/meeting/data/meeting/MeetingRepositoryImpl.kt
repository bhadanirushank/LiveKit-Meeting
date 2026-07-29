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
    private val hostMeetingApiService: com.jbcoder.meeting.network.HostMeetingApiService,
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
                } else if (response.code() == 409 && (problem?.type == "INVALID_IDEMPOTENCY_KEY" || problem?.type == "CONCURRENT_REQUEST_PROCESSING")) {
                    Result.failure(MeetingError.IdempotencyConflict)
                } else if (response.code() >= 500) {
                    Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                } else {
                    Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: IOException) {
            android.util.Log.e("MeetingRepository", "Network error during createMeeting", e)
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
            android.util.Log.e("MeetingRepository", "Error during submitJoinRequest", e)
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

    override suspend fun exchangeHostSession(request: com.jbcoder.meeting.network.HostExchangeRequest, installationId: String): Result<com.jbcoder.meeting.network.HostExchangeResponse> {
        return try {
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            val response = unauthenticatedApiService.exchangeHostSession(installationId, idempotencyKey, request)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    409 -> Result.failure(MeetingError.IdempotencyConflict)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun getWaitingRoom(meetingCode: String): Result<com.jbcoder.meeting.network.WaitingRoomResponse> {
        return try {
            val response = hostMeetingApiService.getWaitingRoom(meetingCode)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    404 -> Result.failure(MeetingError.MeetingNotFound)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun admitParticipant(meetingCode: String, requestId: String, installationId: String): Result<com.jbcoder.meeting.network.AdmitResponse> {
        return try {
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            val response = hostMeetingApiService.admitParticipant(meetingCode, requestId, installationId, idempotencyKey)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    404 -> Result.failure(MeetingError.JoinRequestNotFound)
                    409 -> Result.failure(MeetingError.IdempotencyConflict)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun rejectParticipant(meetingCode: String, requestId: String, reason: String?): Result<com.jbcoder.meeting.network.RejectResponse> {
        return try {
            val response = hostMeetingApiService.rejectParticipant(meetingCode, requestId, com.jbcoder.meeting.network.RejectRequest(reason))
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    404 -> Result.failure(MeetingError.JoinRequestNotFound)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun startMeeting(meetingCode: String, installationId: String): Result<com.jbcoder.meeting.network.StartMeetingResponse> {
        return try {
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            val response = hostMeetingApiService.startMeeting(meetingCode, installationId, idempotencyKey)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    404 -> Result.failure(MeetingError.MeetingNotFound)
                    409 -> Result.failure(MeetingError.IdempotencyConflict)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun getHostLiveKitToken(): Result<com.jbcoder.meeting.network.LiveKitTokenResponse> {
        return try {
            val response = hostMeetingApiService.getHostLiveKitToken()
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.HostUnauthorized)
                    404 -> Result.failure(MeetingError.MeetingNotFound)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun getJoinRequestStatus(requestId: String): Result<com.jbcoder.meeting.network.JoinRequestStatusResponse> {
        return try {
            val response = apiService.getJoinRequestStatus(requestId)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.Unauthorized)
                    403 -> Result.failure(MeetingError.ParticipantRejected)
                    404 -> Result.failure(MeetingError.JoinRequestNotFound)
                    410 -> Result.failure(MeetingError.JoinRequestExpired)
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }

    override suspend fun getParticipantLiveKitToken(requestId: String): Result<com.jbcoder.meeting.network.LiveKitTokenResponse> {
        return try {
            val idempotencyKey = java.util.UUID.randomUUID().toString()
            val response = apiService.getParticipantLiveKitToken(requestId, idempotencyKey)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) } ?: Result.failure(MeetingError.UnexpectedServerResponse("Empty body"))
            } else {
                when (response.code()) {
                    401 -> Result.failure(MeetingError.Unauthorized)
                    403 -> Result.failure(MeetingError.ParticipantRejected)
                    404 -> Result.failure(MeetingError.JoinRequestNotFound)
                    409 -> Result.failure(MeetingError.MeetingCapacityReached)
                    410 -> Result.failure(MeetingError.JoinRequestExpired)
                    429 -> Result.failure(MeetingError.RateLimited(response.headers()["Retry-After"]?.toIntOrNull() ?: 5))
                    else -> Result.failure(MeetingError.UnexpectedServerResponse(response.code().toString()))
                }
            }
        } catch (e: java.io.IOException) {
            Result.failure(MeetingError.Offline)
        } catch (e: Exception) {
            Result.failure(MeetingError.UnexpectedServerResponse(e.message ?: "Unknown error"))
        }
    }
}
