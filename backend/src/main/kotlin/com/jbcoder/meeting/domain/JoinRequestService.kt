package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.CryptoService
import com.jbcoder.meeting.persistence.JoinRequestEntity
import com.jbcoder.meeting.persistence.JoinRequestsTable
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionEntity
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object JoinRequestService {
    
    data class JoinCommand(
        val publicMeetingCode: String,
        val passcode: String?,
        val displayName: String,
        val deviceSessionId: String
    )
    
    fun requestJoin(command: JoinCommand): Result<JoinRequestEntity> {
        val meeting = MeetingRepository.findByPublicCode(command.publicMeetingCode)
            ?: return Result.failure(Exception("Meeting not found or invalid passcode"))
            
        // Check state
        if (meeting.status == MeetingStatus.ENDED || meeting.status == MeetingStatus.CANCELLED || meeting.status == MeetingStatus.EXPIRED) {
            return Result.failure(Exception("Meeting has ended"))
        }
            
        // Passcode check
        if (meeting.passcodeHash != null) {
            if (command.passcode == null || !CryptoService.verify(command.passcode, meeting.passcodeHash)) {
                return Result.failure(Exception("Meeting not found or invalid passcode"))
            }
        }
        
        return transaction {
            // Lock the meeting row early for capacity concurrency race conditions
            val meetingLocked = MeetingRepository.findByIdForUpdate(meeting.id) 
                ?: return@transaction Result.failure(Exception("Meeting not found"))

            // Capacity Check at Join Request time
            if (!meetingLocked.waitingRoomEnabled) {
                val activeOrWaitingCount = ParticipantSessionsTable.selectAll()
                    .where { 
                        (ParticipantSessionsTable.meetingId eq meeting.id) and
                        (ParticipantSessionsTable.state inList listOf(ParticipantState.WAITING.name, ParticipantState.ADMITTED.name, ParticipantState.JOINED.name))
                    }.count()

                if (activeOrWaitingCount >= meetingLocked.maximumParticipants) {
                    return@transaction Result.failure(Exception("MEETING_CAPACITY_REACHED"))
                }
            } else {
                val activeCount = ParticipantSessionsTable.selectAll()
                    .where { 
                        (ParticipantSessionsTable.meetingId eq meeting.id) and
                        (ParticipantSessionsTable.state inList listOf(ParticipantState.ADMITTED.name, ParticipantState.JOINED.name))
                    }.count()

                if (activeCount >= meetingLocked.maximumParticipants) {
                    return@transaction Result.failure(Exception("MEETING_CAPACITY_REACHED"))
                }
            }

            val livekitIdentity = UUID.randomUUID().toString()
            
            // Create participant session in CREATED/WAITING state
            val participantId = UUID.randomUUID()
            
            ParticipantSessionsTable.insert {
                it[id] = participantId
                it[meetingId] = meeting.id
                it[this.livekitIdentity] = livekitIdentity
                it[this.displayName] = command.displayName
                it[role] = ParticipantRole.PARTICIPANT.name
                it[state] = ParticipantState.WAITING.name
                it[requestedAt] = Instant.now()
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            
            val joinRequestId = UUID.randomUUID()
            val expiresAt = Instant.now().plusSeconds(3600)
            
            JoinRequestsTable.insert {
                it[id] = joinRequestId
                it[meetingId] = meeting.id
                it[participantSessionId] = participantId
                it[status] = JoinRequestStatus.PENDING.name
                it[requestedAt] = Instant.now()
                it[this.expiresAt] = expiresAt
            }
            
            val requestEntity = JoinRequestsTable.selectAll().where { JoinRequestsTable.id eq joinRequestId }.single()
            
            Result.success(JoinRequestEntity(
                id = joinRequestId,
                meetingId = requestEntity[JoinRequestsTable.meetingId],
                participantSessionId = requestEntity[JoinRequestsTable.participantSessionId],
                status = JoinRequestStatus.valueOf(requestEntity[JoinRequestsTable.status]),
                requestedAt = requestEntity[JoinRequestsTable.requestedAt],
                reviewedAt = requestEntity[JoinRequestsTable.reviewedAt],
                reviewedByParticipantId = requestEntity[JoinRequestsTable.reviewedByParticipantId],
                rejectionReason = requestEntity[JoinRequestsTable.rejectionReason],
                expiresAt = requestEntity[JoinRequestsTable.expiresAt]
            ))
        }
    }
}
