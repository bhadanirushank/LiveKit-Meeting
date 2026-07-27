package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import com.jbcoder.meeting.persistence.JoinRequestsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object ParticipantService {
    suspend fun leaveMeeting(publicMeetingCode: String, deviceSessionId: String): Result<Unit> {
        return transaction {
            val meeting = MeetingRepository.findByPublicCode(publicMeetingCode)
                ?: return@transaction Result.failure(Exception("Meeting not found"))

            val joinRequest = JoinRequestsTable.selectAll().where {
                (JoinRequestsTable.meetingId eq meeting.id) and
                (JoinRequestsTable.deviceSessionId eq UUID.fromString(deviceSessionId))
            }.orderBy(JoinRequestsTable.requestedAt to SortOrder.DESC).limit(1).singleOrNull()
                ?: return@transaction Result.failure(Exception("Join request not found for device"))

            val participantId = joinRequest[JoinRequestsTable.participantSessionId]

            val participant = ParticipantSessionsTable.selectAll().where {
                ParticipantSessionsTable.id eq participantId
            }.forUpdate().singleOrNull()
                ?: return@transaction Result.failure(Exception("Participant not found"))

            val currentState = participant[ParticipantSessionsTable.state]
            if (currentState == ParticipantState.REMOVED.name) {
                return@transaction Result.failure(Exception("Participant already removed"))
            }
            if (currentState == ParticipantState.LEFT.name) {
                // Idempotent success
                return@transaction Result.success(Unit)
            }

            ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantId }) {
                it[state] = ParticipantState.LEFT.name
                it[leftAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            
            Result.success(Unit)
        }
    }
}
