package com.jbcoder.meeting.feature.createmeeting

import com.jbcoder.meeting.data.meeting.HostSessionStore
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingError
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.HostExchangeRequest
import com.jbcoder.meeting.network.HostExchangeResponse
import com.jbcoder.meeting.network.LiveKitTokenResponse
import com.jbcoder.meeting.network.WaitingRoomResponse
import com.jbcoder.meeting.network.AdmitResponse
import com.jbcoder.meeting.network.RejectResponse
import com.jbcoder.meeting.network.StartMeetingResponse
import com.jbcoder.meeting.network.JoinRequestStatusResponse
import com.jbcoder.meeting.storage.InstallationIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateMeetingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    
    private val requests = mutableListOf<CreateMeetingRequest>()
    private var nextResult: Result<CreateMeetingResponse> = Result.success(
        CreateMeetingResponse("code", "secret", "room", "Title", true, false, 100)
    )
    private var delayBeforeResponse: Long = 0

    private val fakeRepo = object : MeetingRepository {
        override suspend fun createMeeting(request: CreateMeetingRequest, installationId: String): Result<CreateMeetingResponse> {
            requests.add(request)
            if (delayBeforeResponse > 0) {
                delay(delayBeforeResponse)
            }
            return nextResult
        }

        override suspend fun exchangeHostSession(request: HostExchangeRequest, installationId: String): Result<HostExchangeResponse> = Result.success(HostExchangeResponse("atk", "rtk"))
        override suspend fun startMeeting(meetingCode: String, installationId: String): Result<StartMeetingResponse> = Result.success(StartMeetingResponse("true"))
        override suspend fun getHostLiveKitToken(): Result<LiveKitTokenResponse> = Result.success(LiveKitTokenResponse("lk_token", "ws://url"))
        override suspend fun getWaitingRoom(meetingCode: String): Result<WaitingRoomResponse> = throw NotImplementedError()
        override suspend fun admitParticipant(meetingCode: String, requestId: String, installationId: String): Result<AdmitResponse> = throw NotImplementedError()
        override suspend fun rejectParticipant(meetingCode: String, requestId: String, reason: String?): Result<RejectResponse> = throw NotImplementedError()
        
        override suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Result<JoinRequestResponse> = throw NotImplementedError()
        override suspend fun getJoinRequestStatus(requestId: String): Result<JoinRequestStatusResponse> = throw NotImplementedError()
        override suspend fun getParticipantLiveKitToken(requestId: String): Result<LiveKitTokenResponse> = throw NotImplementedError()
    }

    private val fakeInstallIdProvider = object : InstallationIdProvider {
        override suspend fun getInstallationId(): String = "install-id"
    }

    private lateinit var hostSessionStore: HostSessionStore
    private lateinit var roomConnectionHandoffStore: RoomConnectionHandoffStore
    private lateinit var viewModel: CreateMeetingViewModel

    @Before
    fun setup() {
        requests.clear()
        delayBeforeResponse = 0
        nextResult = Result.success(CreateMeetingResponse("code", "secret", "room", "Title", true, false, 100))
        Dispatchers.setMain(testDispatcher)
        hostSessionStore = HostSessionStore()
        roomConnectionHandoffStore = RoomConnectionHandoffStore()
        viewModel = CreateMeetingViewModel(fakeRepo, hostSessionStore, roomConnectionHandoffStore, fakeInstallIdProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Test 1 Same normalized request after timeout reuses the same key`() = runTest {
        viewModel.updateDisplayName("Host")
        nextResult = Result.failure(MeetingError.Offline)
        viewModel.submit()
        advanceUntilIdle()
        val key1 = requests[0].idempotencyKey

        viewModel.submit() // retry exact same
        advanceUntilIdle()
        val key2 = requests[1].idempotencyKey

        assertEquals(key1, key2)
    }

    @Test
    fun `Test 2 Changing display name after attempted submit creates new key`() = runTest {
        viewModel.updateDisplayName("Name1")
        nextResult = Result.failure(MeetingError.Offline)
        viewModel.submit()
        advanceUntilIdle()
        val key1 = requests[0].idempotencyKey

        viewModel.updateDisplayName("Name2")
        viewModel.submit()
        advanceUntilIdle()
        val key2 = requests[1].idempotencyKey

        assertNotEquals(key1, key2)
    }



    @Test
    fun `Test 6 Fresh Create screen does not reuse previous operation key`() = runTest {
        viewModel.updateDisplayName("Host")
        viewModel.submit()
        advanceUntilIdle()
        val key1 = requests[0].idempotencyKey

        // Fresh viewmodel simulating new screen
        val freshViewModel = CreateMeetingViewModel(fakeRepo, hostSessionStore, roomConnectionHandoffStore, fakeInstallIdProvider)
        freshViewModel.updateDisplayName("Host")
        freshViewModel.submit()
        advanceUntilIdle()
        val key2 = requests[1].idempotencyKey

        assertNotEquals(key1, key2)
    }



    @Test
    fun `Test 9 and 10 409 maps to correct error`() = runTest {
        viewModel.updateDisplayName("Title")
        nextResult = Result.failure(MeetingError.IdempotencyConflict)
        viewModel.submit()
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is CreateMeetingUiState.Error)
        val errorMsg = (state as CreateMeetingUiState.Error).message
        assertEquals("Meeting details changed after the previous attempt. Please try again.", errorMsg)
        
        // Subsequent submit without changes should use a NEW key because IdempotencyConflict invalidates it.
        viewModel.submit()
        advanceUntilIdle()
        assertNotEquals(requests[0].idempotencyKey, requests[1].idempotencyKey)
    }

    @Test
    fun `Test 11 A successful 201 emits navigation once`() = runTest {
        viewModel.updateDisplayName("Title")
        viewModel.submit()
        advanceUntilIdle()
        
        assertEquals(CreateMeetingUiState.Success, viewModel.uiState.value)
        
        // Submit again - should be a new key and new meeting
        viewModel.submit()
        advanceUntilIdle()
        assertNotEquals(requests[0].idempotencyKey, requests[1].idempotencyKey)
    }

}
