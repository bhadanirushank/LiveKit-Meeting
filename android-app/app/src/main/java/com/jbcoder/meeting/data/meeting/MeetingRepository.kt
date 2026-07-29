package com.jbcoder.meeting.data.meeting

import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse
import com.jbcoder.meeting.network.HostExchangeRequest
import com.jbcoder.meeting.network.HostExchangeResponse
import com.jbcoder.meeting.network.WaitingRoomResponse
import com.jbcoder.meeting.network.AdmitResponse
import com.jbcoder.meeting.network.RejectResponse
import com.jbcoder.meeting.network.StartMeetingResponse
import com.jbcoder.meeting.network.LiveKitTokenResponse
import com.jbcoder.meeting.network.JoinRequestStatusResponse

interface MeetingRepository {
    suspend fun createMeeting(request: CreateMeetingRequest, installationId: String): Result<CreateMeetingResponse>
    suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Result<JoinRequestResponse>
    suspend fun exchangeHostSession(request: HostExchangeRequest, installationId: String): Result<HostExchangeResponse>
    suspend fun getWaitingRoom(meetingCode: String): Result<WaitingRoomResponse>
    suspend fun admitParticipant(meetingCode: String, requestId: String, installationId: String): Result<AdmitResponse>
    suspend fun rejectParticipant(meetingCode: String, requestId: String, reason: String?): Result<RejectResponse>
    suspend fun startMeeting(meetingCode: String, installationId: String): Result<StartMeetingResponse>
    suspend fun getHostLiveKitToken(): Result<LiveKitTokenResponse>
    
    suspend fun getJoinRequestStatus(requestId: String): Result<JoinRequestStatusResponse>
    suspend fun getParticipantLiveKitToken(requestId: String): Result<LiveKitTokenResponse>
}
