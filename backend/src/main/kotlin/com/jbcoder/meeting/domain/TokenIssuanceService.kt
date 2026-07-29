package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.RedisService
import com.jbcoder.meeting.domain.JoinRequestStatus
import com.jbcoder.meeting.persistence.JoinRequestsTable
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.jbcoder.meeting.domain.ParticipantState
import java.time.Instant
import java.util.UUID

import com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable

object TokenIssuanceService {

    /**
     * Checks if a participant is explicitly blocked from re-entering.
     */
    suspend fun isBlocked(deviceSessionId: String): Boolean {
        return RedisService.get("blocked:device:$deviceSessionId") != null
    }

    /**
     * Issues a token with strong PostgreSQL-authoritative idempotency, one-time consumption, and encrypted storage at rest.
     */
    suspend fun issueMobileToken(joinRequestId: UUID, deviceSessionId: String, idempotencyKey: String, encryptionKeyB64: String): Result<String> {
        if (isBlocked(deviceSessionId)) {
            return Result.failure(Exception("Participant is blocked from joining"))
        }

        val idempotencyHash = com.jbcoder.meeting.infrastructure.CryptoService.sha256(idempotencyKey)
        val encryptionKeyBytes = java.util.Base64.getDecoder().decode(encryptionKeyB64)
        if (encryptionKeyBytes.size != 32) {
            return Result.failure(Exception("Invalid backend encryption key length"))
        }

        return transaction {
            // Lock the join request row for concurrency control
            val requestRow = JoinRequestsTable.selectAll()
                .where { JoinRequestsTable.id eq joinRequestId }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("JOIN_REQUEST_NOT_FOUND", "Join request not found", io.ktor.http.HttpStatusCode.NotFound))
            
            // Verify ownership
            if (requestRow[JoinRequestsTable.deviceSessionId]?.toString() != deviceSessionId) {
                return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("JOIN_REQUEST_NOT_OWNED", "JOIN_REQUEST_NOT_OWNED", io.ktor.http.HttpStatusCode.Forbidden))
            }
            
            val status = JoinRequestStatus.valueOf(requestRow[JoinRequestsTable.status])
            val expiresAt = requestRow[JoinRequestsTable.expiresAt]
            
            if (expiresAt.isBefore(Instant.now())) {
                return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("JOIN_REQUEST_EXPIRED", "Join request expired", io.ktor.http.HttpStatusCode.Forbidden))
            }
            val participantId = requestRow[JoinRequestsTable.participantSessionId]
            
            // Lock participant row
            val participantRow = ParticipantSessionsTable.selectAll()
                .where { ParticipantSessionsTable.id eq participantId }
                .forUpdate()
                .singleOrNull()
                ?: return@transaction Result.failure(Exception("Participant not found"))
                
            if (participantRow[ParticipantSessionsTable.state] == ParticipantState.REMOVED.name ||
                participantRow[ParticipantSessionsTable.state] == ParticipantState.LEFT.name) {
                return@transaction Result.failure(Exception("Participant is not eligible (LEFT/REMOVED)"))
            }

            // Check for existing delivery (REPLAY)
            val existingDelivery = com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.selectAll()
                .where { 
                    (com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.joinRequestId eq joinRequestId) 
                }.singleOrNull()
            
            if (existingDelivery != null) {
                // If the idempotency key matches AND replay window is valid, return success
                val storedHash = existingDelivery[com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.idempotencyKeyHash]
                val replayExpires = existingDelivery[com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.replayExpiresAt]
                
                if (storedHash == idempotencyHash) {
                    if (Instant.now().isAfter(replayExpires)) {
                        return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("REPLAY_EXPIRED", "Token replay window has expired", io.ktor.http.HttpStatusCode.Gone))
                    }
                    val encryptedPayload = existingDelivery[com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.encryptedTokenPayload]
                    val iv = existingDelivery[com.jbcoder.meeting.persistence.LiveKitTokenDeliveriesTable.encryptionIv]
                    val decryptedToken = com.jbcoder.meeting.infrastructure.CryptoService.decryptAesGcm(encryptedPayload, iv, encryptionKeyBytes)
                    return@transaction Result.success(decryptedToken)
                } else {
                    // Different idempotency key, same join request!
                    return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("TOKEN_ALREADY_ISSUED", "TOKEN_ALREADY_ISSUED", io.ktor.http.HttpStatusCode.Conflict))
                }
            }
                
            val meetingId = participantRow[ParticipantSessionsTable.meetingId]
            
            val meeting = MeetingRepository.findByIdForUpdate(meetingId)
                ?: return@transaction Result.failure(Exception("Meeting not found"))

            // Verify meeting state
            if (meeting.status != MeetingStatus.LIVE && meeting.status != MeetingStatus.SCHEDULED && meeting.status != MeetingStatus.READY) {
                return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("MEETING_ENDED", "MEETING_ENDED", io.ktor.http.HttpStatusCode.Forbidden))
            }
            if (meeting.status == MeetingStatus.SCHEDULED && meeting.scheduledStart != null) {
                val earlyWindow = meeting.scheduledStart.minusSeconds(900)
                if (Instant.now().isBefore(earlyWindow)) {
                    return@transaction Result.failure(Exception("Meeting has not started yet"))
                }
            }

            // 2. Enforce Lock Rules
            if (meeting.isLocked) {
                // Check and atomically consume one-time admission authorization
                val consumedRows = com.jbcoder.meeting.persistence.LockedMeetingAuthorizationsTable.update({
                    (com.jbcoder.meeting.persistence.LockedMeetingAuthorizationsTable.participantSessionId eq participantId) and
                    (com.jbcoder.meeting.persistence.LockedMeetingAuthorizationsTable.consumedAt.isNull()) and
                    (com.jbcoder.meeting.persistence.LockedMeetingAuthorizationsTable.expiresAt greaterEq Instant.now())
                }) {
                    it[consumedAt] = Instant.now()
                }
                if (consumedRows == 0) {
                    return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("MEETING_LOCKED", "MEETING_LOCKED", io.ktor.http.HttpStatusCode.Forbidden))
                }
            }

            var currentStatus = status

            if (currentStatus == JoinRequestStatus.PENDING && !meeting.waitingRoomEnabled && !meeting.isLocked) {
                val activeCount = ParticipantSessionsTable.selectAll()
                    .where {
                        (ParticipantSessionsTable.meetingId eq meetingId) and
                        (ParticipantSessionsTable.state inList listOf(ParticipantState.ADMITTED.name, ParticipantState.JOINED.name))
                    }.count()
                if (activeCount >= meeting.maximumParticipants) {
                    return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("MEETING_CAPACITY_REACHED", "Meeting capacity reached", io.ktor.http.HttpStatusCode.Conflict))
                }
                
                // Explicitly update to maintain state machine consistency
                JoinRequestsTable.update({ JoinRequestsTable.id eq joinRequestId }) {
                    it[JoinRequestsTable.status] = JoinRequestStatus.ADMITTED.name
                }
                ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantId }) {
                    it[state] = ParticipantState.ADMITTED.name
                    it[updatedAt] = Instant.now()
                }
                
                currentStatus = JoinRequestStatus.ADMITTED
            }

            if (currentStatus != JoinRequestStatus.ADMITTED) {
                if (currentStatus == JoinRequestStatus.PENDING) {
                    return@transaction Result.failure(Exception("WAITING_ROOM")) // Caught and mapped to 202
                }
                return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("PARTICIPANT_REJECTED", "PARTICIPANT_REJECTED", io.ktor.http.HttpStatusCode.Forbidden))
            }

            // Consume authorization (atomic transition to TOKEN_ISSUED)
            val updatedRows = ParticipantSessionsTable.update({ 
                (ParticipantSessionsTable.id eq participantId) and 
                (ParticipantSessionsTable.tokenIssuedAt.isNull()) 
            }) {
                it[tokenIssuedAt] = Instant.now()
            }
            
            if (updatedRows == 0) {
                // Another thread might have just issued it? But we locked `join_requests`. 
                // Either way, if token already issued natively, reject.
                return@transaction Result.failure(com.jbcoder.meeting.domain.AppError("TOKEN_ALREADY_ISSUED", "TOKEN_ALREADY_ISSUED", io.ktor.http.HttpStatusCode.Conflict))
            }

            // Generate Token
            val livekitIdentity = participantRow[ParticipantSessionsTable.livekitIdentity]
            val displayName = participantRow[ParticipantSessionsTable.displayName]
            val roleName = participantRow[ParticipantSessionsTable.role]
            val publishRestricted = participantRow[ParticipantSessionsTable.publishRestricted]
            val screenShareAllowed = participantRow[ParticipantSessionsTable.screenShareAllowed]
            
            val metadataJson = """{"id":"${participantId.toString()}","role":"$roleName"}"""
            
            val rawToken = LiveKitTokenService.createToken(
                roomName = meeting.livekitRoomName,
                participantIdentity = livekitIdentity,
                participantName = displayName,
                metadata = metadataJson,
                role = roleName,
                publishRestricted = publishRestricted,
                screenShareAllowed = screenShareAllowed
            )

            // Encrypt and store securely (AT REST ONLY)
            val (encryptedPayload, iv) = com.jbcoder.meeting.infrastructure.CryptoService.encryptAesGcm(rawToken, encryptionKeyBytes)
            
            LiveKitTokenDeliveriesTable.insert {
                it[LiveKitTokenDeliveriesTable.id] = UUID.randomUUID()
                it[LiveKitTokenDeliveriesTable.joinRequestId] = joinRequestId
                it[LiveKitTokenDeliveriesTable.deviceSessionId] = UUID.fromString(deviceSessionId)
                it[LiveKitTokenDeliveriesTable.idempotencyKeyHash] = idempotencyHash
                it[LiveKitTokenDeliveriesTable.encryptedTokenPayload] = encryptedPayload
                it[LiveKitTokenDeliveriesTable.encryptionIv] = iv
                it[LiveKitTokenDeliveriesTable.tokenExpiresAt] = Instant.now().plusSeconds(7200)
                it[LiveKitTokenDeliveriesTable.replayExpiresAt] = Instant.now().plusSeconds(60) // 60s replay window
                it[LiveKitTokenDeliveriesTable.createdAt] = Instant.now()
            }

            Result.success(rawToken)
        }
    }
}
