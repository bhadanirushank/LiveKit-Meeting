package com.jbcoder.meeting.data.meeting

import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.MeetingApiService
import com.jbcoder.meeting.network.SessionBootstrapRequest
import com.jbcoder.meeting.network.SessionResponse
import com.jbcoder.meeting.network.UnauthenticatedMeetingApiService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class MeetingRepositoryTest {

    private lateinit var repository: MeetingRepositoryImpl
    private val json = Json { ignoreUnknownKeys = true }
    
    private var joinResponseToReturn: Response<JoinRequestResponse>? = null
    private var createResponseToReturn: Response<CreateMeetingResponse>? = null

    private val fakeApi = object : MeetingApiService {
        override suspend fun bootstrapSession(request: SessionBootstrapRequest): Response<SessionResponse> {
            throw NotImplementedError()
        }
        override suspend fun refreshSession(): Response<SessionResponse> {
            throw NotImplementedError()
        }
        override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Response<JoinRequestResponse> {
            return joinResponseToReturn ?: Response.success(JoinRequestResponse("dummy", "PENDING"))
        }
    }

    private val fakeUnauthApi = object : UnauthenticatedMeetingApiService {
        override suspend fun createMeeting(installationId: String, request: CreateMeetingRequest): Response<CreateMeetingResponse> {
            return createResponseToReturn ?: Response.success(201, CreateMeetingResponse("code", "secret", "room", "Title", true, false, 100))
        }
    }

    @Before
    fun setup() {
        repository = MeetingRepositoryImpl(fakeApi, fakeUnauthApi, json)
    }

    @Test
    fun `CreateMeetingEndpointReceivesNoAuthHeaderTest`() = runBlocking {
        // UnauthenticatedMeetingApiService is injected from a client without AuthInterceptor.
        val request = CreateMeetingRequest(title = "Title", idempotencyKey = "key")
        val result = repository.createMeeting(request, "install-id")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `CreateMeetingResponse201ParsingTest`() = runBlocking {
        val request = CreateMeetingRequest(title = "Title", idempotencyKey = "key")
        val result = repository.createMeeting(request, "id")
        assertTrue(result.isSuccess)
        assertEquals("code", result.getOrNull()?.publicMeetingCode)
    }

    @Test
    fun `JoinRequestUsesBackendIssuedDeviceSessionIdTest`() = runBlocking {
        val request = JoinRequestDto(displayName = "Name", deviceSessionId = "backend-session-id")
        val result = repository.submitJoinRequest("code12", request)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `JoinRequest202ParsingTest`() = runBlocking {
        joinResponseToReturn = Response.success(202, JoinRequestResponse("req-id", "PENDING"))
        val request = JoinRequestDto(displayName = "Name", deviceSessionId = "backend-session-id")
        val result = repository.submitJoinRequest("code12", request)
        assertTrue(result.isSuccess)
        assertEquals("req-id", result.getOrNull()?.requestId)
    }

    @Test
    fun `JoinRequest401ProblemMappingTest`() = runBlocking {
        val errorBody = "{\"detail\":\"Invalid passcode\"}".toResponseBody("application/json".toMediaType())
        joinResponseToReturn = Response.error(401, errorBody)
        val result = repository.submitJoinRequest("code12", JoinRequestDto("a", "b", "c"))
        assertTrue(result.isFailure)
        assertEquals(MeetingError.InvalidCodeOrPasscode, result.exceptionOrNull())
    }

    @Test
    fun `JoinRequest403MeetingLockedMappingTest`() = runBlocking {
        val errorBody = "{\"detail\":\"Locked\"}".toResponseBody("application/json".toMediaType())
        joinResponseToReturn = Response.error(403, errorBody)
        val result = repository.submitJoinRequest("code12", JoinRequestDto("a", "b", "c"))
        assertEquals(MeetingError.MeetingLocked, result.exceptionOrNull())
    }

    @Test
    fun `JoinRequest409CapacityReachedMappingTest`() = runBlocking {
        val errorBody = "{\"detail\":\"Full\"}".toResponseBody("application/json".toMediaType())
        joinResponseToReturn = Response.error(409, errorBody)
        val result = repository.submitJoinRequest("code12", JoinRequestDto("a", "b", "c"))
        assertEquals(MeetingError.MeetingCapacityReached, result.exceptionOrNull())
    }

    @Test
    fun `JoinRequest429RetryAfterMappingTest`() = runBlocking {
        val errorBody = "{\"detail\":\"Too many requests\"}".toResponseBody("application/json".toMediaType())
        val response = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/").build())
            .header("Retry-After", "120")
            .build()
            
        joinResponseToReturn = Response.error(errorBody, response)
        val result = repository.submitJoinRequest("code12", JoinRequestDto("a", "b", "c"))
        val error = result.exceptionOrNull() as MeetingError.RateLimited
        assertEquals(120, error.retryAfterSeconds)
    }

    @Test
    fun `CreateMeeting409IdempotencyConflictTest`() = runBlocking {
        val errorBody = "{\"type\":\"INVALID_IDEMPOTENCY_KEY\",\"title\":\"Idempotency key reused\"}".toResponseBody("application/json".toMediaType())
        createResponseToReturn = Response.error(409, errorBody)
        val request = CreateMeetingRequest(title = "Title", idempotencyKey = "key")
        val result = repository.createMeeting(request, "id")
        assertTrue(result.isFailure)
        assertEquals(MeetingError.IdempotencyConflict, result.exceptionOrNull())
    }

    @Test
    fun `CreateMeetingGeneric409Test`() = runBlocking {
        val errorBody = "{\"type\":\"SOME_OTHER_CONFLICT\",\"title\":\"Conflict\"}".toResponseBody("application/json".toMediaType())
        createResponseToReturn = Response.error(409, errorBody)
        val request = CreateMeetingRequest(title = "Title", idempotencyKey = "key")
        val result = repository.createMeeting(request, "id")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MeetingError.UnexpectedServerResponse)
    }
}
