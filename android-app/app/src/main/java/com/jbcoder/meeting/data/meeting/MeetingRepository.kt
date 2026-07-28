package com.jbcoder.meeting.data.meeting

import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.CreateMeetingResponse
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.JoinRequestResponse

interface MeetingRepository {
    suspend fun createMeeting(request: CreateMeetingRequest, installationId: String): Result<CreateMeetingResponse>
    suspend fun submitJoinRequest(meetingCode: String, request: JoinRequestDto): Result<JoinRequestResponse>
}
