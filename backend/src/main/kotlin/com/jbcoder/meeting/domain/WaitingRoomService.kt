package com.jbcoder.meeting.domain

import com.jbcoder.meeting.domain.JoinRequestStatus
import com.jbcoder.meeting.domain.ParticipantState
import com.jbcoder.meeting.persistence.JoinRequestsTable
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object WaitingRoomService {
    
    fun admitParticipant(publicMeetingCode: String, joinRequestId: UUID): Result<Unit> {
        val meetingId = transaction { MeetingRepository.findByPublicCode(publicMeetingCode)?.id }
            ?: return Result.failure(Exception("Meeting not found"))

        return transaction {
            val meeting = MeetingRepository.findByIdForUpdate(meetingId)
                ?: return@transaction Result.failure(Exception("Meeting not found"))

            // Capacity Check
            val activeCount = ParticipantSessionsTable.selectAll()
                .where { 
                    (ParticipantSessionsTable.meetingId eq meeting.id) and
                    (ParticipantSessionsTable.state inList listOf(ParticipantState.ADMITTED.name, ParticipantState.JOINED.name))
                }.count()

            if (activeCount >= meeting.maximumParticipants) {
                return@transaction Result.failure(Exception("Meeting is at maximum capacity"))
            }
                
            val updatedRows = JoinRequestsTable.update({ 
                (JoinRequestsTable.id eq joinRequestId) and (JoinRequestsTable.meetingId eq meeting.id)
            }) {
                it[status] = JoinRequestStatus.ADMITTED.name
                it[reviewedAt] = Instant.now()
            }
            
            if (updatedRows > 0) {
                // Update participant state
                val requestRow = JoinRequestsTable.selectAll().where { JoinRequestsTable.id eq joinRequestId }.single()
                val participantSessionId = requestRow[JoinRequestsTable.participantSessionId]
                ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantSessionId }) {
                    it[state] = ParticipantState.ADMITTED.name
                    it[admittedAt] = Instant.now()
                }
                
                if (meeting.isLocked) {
                    com.jbcoder.meeting.persistence.LockedMeetingAuthorizationsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[this.meetingId] = meetingId
                        it[this.participantSessionId] = participantSessionId
                        it[issuedAt] = Instant.now()
                        it[expiresAt] = Instant.now().plusSeconds(120) // 2 minutes to consume
                    }
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Join request not found or could not be updated"))
            }
        }
    }
    
    fun rejectParticipant(publicMeetingCode: String, joinRequestId: UUID, reason: String?): Result<Unit> {
        return transaction {
            val meeting = MeetingRepository.findByPublicCode(publicMeetingCode)
                ?: return@transaction Result.failure(Exception("Meeting not found"))
                
            val updatedRows = JoinRequestsTable.update({ 
                (JoinRequestsTable.id eq joinRequestId) and (JoinRequestsTable.meetingId eq meeting.id)
            }) {
                it[status] = JoinRequestStatus.REJECTED.name
                it[reviewedAt] = Instant.now()
                it[rejectionReason] = reason
            }
            
            if (updatedRows > 0) {
                val requestRow = JoinRequestsTable.selectAll().where { JoinRequestsTable.id eq joinRequestId }.single()
                ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq requestRow[JoinRequestsTable.participantSessionId] }) {
                    it[state] = ParticipantState.REJECTED.name
                }
                Result.success(Unit)
            } else {
                Result.failure(Exception("Join request not found or could not be updated"))
            }
        }
    }

    data class PendingJoinRequest(
        val id: UUID,
        val displayName: String,
        val requestedAt: Instant
    )

    fun getPendingRequests(publicMeetingCode: String): Result<List<PendingJoinRequest>> {
        return transaction {
            val meeting = MeetingRepository.findByPublicCode(publicMeetingCode)
                ?: return@transaction Result.failure(Exception("Meeting not found"))

            val query = JoinRequestsTable.innerJoin(ParticipantSessionsTable)
                .selectAll()
                .where {
                    (JoinRequestsTable.meetingId eq meeting.id) and
                    (JoinRequestsTable.status eq JoinRequestStatus.PENDING.name)
                }

            val pending = query.map {
                PendingJoinRequest(
                    id = it[JoinRequestsTable.id],
                    displayName = it[ParticipantSessionsTable.displayName],
                    requestedAt = it[JoinRequestsTable.requestedAt]
                )
            }.sortedBy { it.requestedAt }

            Result.success(pending)
        }
    }
}
