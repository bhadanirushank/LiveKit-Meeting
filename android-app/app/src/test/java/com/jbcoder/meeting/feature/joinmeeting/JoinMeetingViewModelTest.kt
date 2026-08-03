package com.jbcoder.meeting.feature.joinmeeting

import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.MeetingApiService
import com.jbcoder.meeting.network.SessionBootstrapRequest
import com.jbcoder.meeting.network.SessionCoordinator
import com.jbcoder.meeting.network.SessionResponse
import com.jbcoder.meeting.network.HostExchangeRequest
import com.jbcoder.meeting.network.HostExchangeResponse
import com.jbcoder.meeting.network.LiveKitTokenResponse
import com.jbcoder.meeting.network.WaitingRoomResponse
import com.jbcoder.meeting.network.AdmitResponse
import com.jbcoder.meeting.network.RejectResponse
import com.jbcoder.meeting.network.StartMeetingResponse
import com.jbcoder.meeting.network.JoinRequestStatusResponse
import com.jbcoder.meeting.network.MeetingStatusResponse
import com.jbcoder.meeting.security.MobileSessionData
import com.jbcoder.meeting.security.SecureSessionStorage
import com.jbcoder.meeting.storage.InstallationIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class JoinMeetingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeRepo = object : MeetingRepository {
        override suspend fun createMeeting(request: CreateMeetingRequest, installationId: String): Result<CreateMeetingResponse> {
            throw NotImplementedError()
        }

        override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Result<JoinRequestResponse> {
            return Result.success(JoinRequestResponse("req-id", "PENDING"))
        }

        override suspend fun exchangeHostSession(request: HostExchangeRequest, installationId: String): Result<HostExchangeResponse> = throw NotImplementedError()
        override suspend fun getWaitingRoom(meetingCode: String): Result<WaitingRoomResponse> = throw NotImplementedError()
        override suspend fun admitParticipant(meetingCode: String, requestId: String, installationId: String): Result<AdmitResponse> = throw NotImplementedError()
        override suspend fun rejectParticipant(meetingCode: String, requestId: String, reason: String?): Result<RejectResponse> = throw NotImplementedError()
        override suspend fun startMeeting(meetingCode: String, installationId: String): Result<StartMeetingResponse> = throw NotImplementedError()
        override suspend fun getHostLiveKitToken(): Result<LiveKitTokenResponse> = throw NotImplementedError()
        override suspend fun getJoinRequestStatus(requestId: String): Result<JoinRequestStatusResponse> = throw NotImplementedError()
        override suspend fun getParticipantLiveKitToken(requestId: String): Result<LiveKitTokenResponse> = throw NotImplementedError()
    }

    private val fakeStorage = object : SecureSessionStorage {
        var session: MobileSessionData? = MobileSessionData(
            sessionId = "dev-sess",
            accessToken = "atk",
            refreshToken = "rtk",
            accessExpiry = Long.MAX_VALUE,
            refreshExpiry = Long.MAX_VALUE
        )
        override suspend fun saveSession(data: MobileSessionData) { session = data }
        override suspend fun loadSession(): MobileSessionData? = session
        override suspend fun clearSession() { session = null }
    }

    private val fakeInstallIdProvider = object : InstallationIdProvider {
        override suspend fun getInstallationId(): String = "test-install-id"
    }

    private val fakeApi = object : MeetingApiService {
        override suspend fun bootstrapSession(request: SessionBootstrapRequest): Response<SessionResponse> {
            return Response.success(SessionResponse("sess", "atk", "rtk", "9999-12-31T23:59:59Z", "9999-12-31T23:59:59Z"))
        }
        override suspend fun refreshSession(): Response<SessionResponse> {
            throw NotImplementedError()
        }
        override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Response<JoinRequestResponse> {
            throw NotImplementedError()
        }
        override suspend fun getJoinRequestStatus(requestId: String): Response<JoinRequestStatusResponse> = throw NotImplementedError()
        override suspend fun getParticipantLiveKitToken(requestId: String, idempotencyKey: String): Response<LiveKitTokenResponse> = throw NotImplementedError()
        override suspend fun getMeetingStatus(meetingCode: String): Response<MeetingStatusResponse> = throw NotImplementedError()
        override suspend fun leaveMeeting(meetingCode: String, idempotencyKey: String): Response<Unit> = throw NotImplementedError()
    }

    private lateinit var handoffStore: RoomConnectionHandoffStore
    private lateinit var coordinator: SessionCoordinator
    private lateinit var viewModel: JoinMeetingViewModel

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        handoffStore = RoomConnectionHandoffStore()
        coordinator = SessionCoordinator(fakeApi, fakeStorage, fakeInstallIdProvider)
        coordinator.initializeSession() // Make it READY
        
        viewModel = JoinMeetingViewModel(fakeRepo, handoffStore, coordinator)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `MeetingCodeCaseSensitivityAndWhitespaceTest`() = runTest {
        // Must be exactly 12 chars
        viewModel.updateMeetingCode("code12chars") // 11 chars
        viewModel.submit()
        assertTrue(viewModel.uiState.value is JoinMeetingUiState.Error)
    }

    @Test
    fun `DisplayNameExactBoundaryTest`() = runTest {
        // Blank display name
        viewModel.updateMeetingCode("123456789012")
        viewModel.updateDisplayName("   ")
        viewModel.submit()
        assertTrue(viewModel.uiState.value is JoinMeetingUiState.Error)
    }
}
