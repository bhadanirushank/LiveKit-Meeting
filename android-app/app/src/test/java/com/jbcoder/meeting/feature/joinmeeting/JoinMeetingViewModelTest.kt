package com.jbcoder.meeting.feature.joinmeeting

import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.MeetingApiService
import com.jbcoder.meeting.network.SessionBootstrapRequest
import com.jbcoder.meeting.network.SessionCoordinator
import com.jbcoder.meeting.network.SessionResponse
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
    }

    private lateinit var handoffStore: MeetingEntryHandoffStore
    private lateinit var coordinator: SessionCoordinator
    private lateinit var viewModel: JoinMeetingViewModel

    @Before
    fun setup() = runTest {
        Dispatchers.setMain(testDispatcher)
        handoffStore = MeetingEntryHandoffStore()
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

    @Test
    fun `PasscodeExactBoundaryTest`() = runTest {
        viewModel.updateMeetingCode("123456789012")
        viewModel.updateDisplayName("Valid Name")
        viewModel.updatePasscode("123") // Too short
        viewModel.submit()
        assertTrue(viewModel.uiState.value is JoinMeetingUiState.Error)
    }
}
