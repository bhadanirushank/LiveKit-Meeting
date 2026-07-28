package com.jbcoder.meeting.data.meeting

sealed class MeetingError(override val message: String) : Exception(message) {
    data class CreateFailed(val detail: String) : MeetingError(detail)
    object MeetingNotFound : MeetingError("Meeting not found")
    object MeetingLocked : MeetingError("Meeting locked")
    object MeetingCapacityReached : MeetingError("Capacity reached")
    object InvalidCodeOrPasscode : MeetingError("Invalid code or passcode")
    object WaitingRoomRequired : MeetingError("Waiting room required")
    data class RateLimited(val retryAfterSeconds: Int) : MeetingError("Rate limited")
    object SessionExpired : MeetingError("Session expired")
    object Unauthorized : MeetingError("Unauthorized")
    object Offline : MeetingError("Device is offline")
    object ServiceUnavailable : MeetingError("Service unavailable")
    object ContractMismatch : MeetingError("Contract mismatch")
    data class UnexpectedServerResponse(val code: String) : MeetingError("Unexpected error: $code")
}
