package com.jbcoder.meeting.network

import com.jbcoder.meeting.security.MobileSessionData
import com.jbcoder.meeting.security.SecureSessionStorage
import com.jbcoder.meeting.storage.InstallationIdProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class SessionCoordinatorTest {

    private lateinit var coordinator: SessionCoordinator
    
    private val mockStorage = object : SecureSessionStorage {
        var currentSession: MobileSessionData? = null
        override suspend fun saveSession(data: MobileSessionData) { currentSession = data }
        override suspend fun loadSession(): MobileSessionData? = currentSession
        override suspend fun clearSession() { currentSession = null }
    }
    
    private val mockProvider = object : InstallationIdProvider {
        override suspend fun getInstallationId(): String = "test-install-id"
    }

    private val mockApi = object : MeetingApiService {
        override suspend fun bootstrapSession(request: SessionBootstrapRequest): Response<SessionResponse> {
            return Response.success(
                SessionResponse(
                    "sess-123", "atk-123", "rtk-123", 
                    Instant.now().plusSeconds(3600).toString(),
                    Instant.now().plusSeconds(7200).toString()
                )
            )
        }

        override suspend fun refreshSession(): Response<SessionResponse> {
            return Response.success(
                SessionResponse(
                    "sess-123", "atk-456", "rtk-456",
                    Instant.now().plusSeconds(3600).toString(),
                    Instant.now().plusSeconds(7200).toString()
                )
            )
        }

        override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Response<JoinRequestResponse> {
            return Response.success(JoinRequestResponse("dummy-req", "PENDING"))
        }

        override suspend fun getJoinRequestStatus(requestId: String): Response<JoinRequestStatusResponse> {
            return Response.success(JoinRequestStatusResponse("APPROVED"))
        }

        override suspend fun getParticipantLiveKitToken(requestId: String, idempotencyKey: String): Response<LiveKitTokenResponse> {
            return Response.success(LiveKitTokenResponse("lk-token-dummy"))
        }

        override suspend fun getMeetingStatus(meetingCode: String): Response<MeetingStatusResponse> {
            return Response.success(MeetingStatusResponse("ACTIVE"))
        }

        override suspend fun leaveMeeting(meetingCode: String, idempotencyKey: String): Response<Unit> {
            return Response.success(Unit)
        }
    }

    // Dummy mock to bypass constructor error
    private fun <T> mock(): T = null as T

    @Before
    fun setup() {
        coordinator = SessionCoordinator(mockApi, mockStorage, mockProvider)
    }

    @Test
    fun `backendConnectivityTest`() = runBlocking {
        coordinator.initializeSession()
        assertEquals(SessionState.READY, coordinator.sessionState.value)
        val token = coordinator.getAccessToken()
        assertEquals("atk-123", token)
    }

    @Test
    fun `test access token refresh`() = runBlocking {
        // Setup expired access token
        mockStorage.saveSession(
            MobileSessionData(
                "sess-123", "atk-expired", "rtk-123",
                Instant.now().minusSeconds(100).epochSecond,
                Instant.now().plusSeconds(3600).epochSecond
            )
        )
        coordinator.initializeSession()
        assertEquals(SessionState.READY, coordinator.sessionState.value)
        
        // When we get access token, it should refresh and return new one
        val token = coordinator.getAccessToken()
        assertEquals("atk-456", token)
    }
}
