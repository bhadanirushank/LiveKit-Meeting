package com.jbcoder.meeting.domain

import java.time.Instant
import java.util.UUID

enum class MeetingStatus {
    DRAFT, READY, SCHEDULED, STARTING, LIVE, ENDING, ENDED, EXPIRED, CANCELLED
}

enum class ParticipantRole {
    HOST, CO_HOST, PARTICIPANT
}

enum class ParticipantState {
    CREATED, WAITING, ADMITTED, JOINED, LEFT, REMOVED, REJECTED
}

enum class JoinRequestStatus {
    PENDING, ADMITTED, REJECTED, CANCELLED, EXPIRED
}
