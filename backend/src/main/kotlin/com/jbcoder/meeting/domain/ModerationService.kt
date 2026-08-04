package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import livekit.LivekitModels
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

object ModerationService {
    private val logger = LoggerFactory.getLogger(ModerationService::class.java)

    /**
     * Removes a participant from the meeting.
     * Also adds them to the deny list.
     */
    suspend fun removeParticipant(meetingId: UUID, participantId: UUID, removedBy: UUID, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId) ?: return@withContext Result.failure(Exception("Meeting not found"))
        val participant = transaction {
            ParticipantSessionsTable.select(ParticipantSessionsTable.columns)
                .where { ParticipantSessionsTable.id eq participantId }.singleOrNull()
        } ?: return@withContext Result.failure(Exception("Participant not found"))

        val livekitIdentity = participant[ParticipantSessionsTable.livekitIdentity]
        val deviceSessionId = participant[ParticipantSessionsTable.deviceSessionId]

        val outboxId = UUID.randomUUID()
        transaction {
            ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantId }) {
                it[state] = ParticipantState.REMOVED.name
                it[removedAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            
            // Add to deny list for the duration of the meeting
            ParticipantDenyListTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[this.participantSessionId] = participantId
                it[this.livekitIdentity] = livekitIdentity
                it[this.reason] = reason
                it[this.removedBy] = removedBy
                it[createdAt] = Instant.now()
                it[expiresAt] = meeting.expiresAt
            }

            // Revoke backend sessions
            MeetingAuthorizationSessionsTable.update({
                (MeetingAuthorizationSessionsTable.participantSessionId eq participantId) and 
                (MeetingAuthorizationSessionsTable.revokedAt.isNull())
            }) {
                it[revokedAt] = Instant.now()
            }

            // Write audit event
            AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "PARTICIPANT_REMOVED"
                it[this.participantSessionId] = participantId
                it[details] = "Removed by: $removedBy, reason: $reason"
                it[createdAt] = Instant.now()
            }

            // Create pending outbox action
            ModerationOutboxTable.insert {
                it[id] = outboxId
                it[this.meetingId] = meetingId
                it[this.participantSessionId] = participantId
                it[actionType] = "REMOVE_PARTICIPANT"
                it[status] = "PENDING"
                it[createdAt] = Instant.now()
            }
        }

        // Call LiveKit asynchronously/after commit
        try {
            LiveKitParticipantService.removeParticipant(meeting.livekitRoomName, livekitIdentity).getOrThrow()
            transaction {
                ModerationOutboxTable.update({ ModerationOutboxTable.id eq outboxId }) {
                    it[status] = "COMPLETED"
                    it[completedAt] = Instant.now()
                }
            }
        } catch (e: Exception) {
            logger.error("LiveKit removal failed, queued for retry", e)
            transaction {
                ModerationOutboxTable.update({ ModerationOutboxTable.id eq outboxId }) {
                    with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                        it[retryCount] = ModerationOutboxTable.retryCount + 1
                    }
                    it[failureReason] = e.message
                }
            }
        }

        Result.success(Unit)
    }

    /**
     * Disables publishing (microphone, video, screen share) for a participant.
     */
    suspend fun disablePublishing(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        updateParticipantPermissions(meetingId, participantId, canPublish = false)
    }

    /**
     * Restores publishing (microphone, video, screen share) for a participant.
     */
    suspend fun restorePublishing(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        updateParticipantPermissions(meetingId, participantId, canPublish = true)
    }

    private suspend fun updateParticipantPermissions(meetingId: UUID, participantId: UUID, canPublish: Boolean): Result<Unit> {
        val meeting = MeetingRepository.findById(meetingId) ?: return Result.failure(Exception("Meeting not found"))
        val participant = transaction {
            val p = ParticipantSessionsTable.select(ParticipantSessionsTable.columns)
                .where { ParticipantSessionsTable.id eq participantId }.singleOrNull()
            if (p != null) {
                ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantId }) {
                    it[publishRestricted] = !canPublish
                }
            }
            p
        } ?: return Result.failure(Exception("Participant not found"))

        val livekitIdentity = participant[ParticipantSessionsTable.livekitIdentity]

        return try {
            val config = com.jbcoder.meeting.configuration.AppConfig.load()
            val client = io.livekit.server.RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            val permission = LivekitModels.ParticipantPermission.newBuilder()
                .setCanPublish(canPublish)
                .setCanSubscribe(true)
                .setCanPublishData(canPublish)
                .build()

            val response = client.updateParticipant(
                meeting.livekitRoomName,
                livekitIdentity,
                participantId.toString(),
                participant[ParticipantSessionsTable.displayName],
                permission
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                // If it returns 404, they aren't connected via WebRTC yet. 
                // That's fine because we already updated the database.
                if (response.code() == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("LiveKit API Error: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            // Also ignore connection errors if we consider DB persistence enough, but we return failure here
            Result.failure(e)
        }
    }

    /**
     * Mutes all audio tracks for a participant.
     */
    suspend fun muteParticipant(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId) ?: return@withContext Result.failure(Exception("Meeting not found"))
        val participant = transaction {
            ParticipantSessionsTable.select(ParticipantSessionsTable.columns)
                .where { ParticipantSessionsTable.id eq participantId }.singleOrNull()
        } ?: return@withContext Result.failure(Exception("Participant not found"))

        val livekitIdentity = participant[ParticipantSessionsTable.livekitIdentity]

        return@withContext try {
            val config = com.jbcoder.meeting.configuration.AppConfig.load()
            val client = io.livekit.server.RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            val infoResponse = client.getParticipant(meeting.livekitRoomName, livekitIdentity).execute()
            if (!infoResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Participant not in LiveKit room"))
            }

            val participantInfo = infoResponse.body()
            var mutedAny = false
            participantInfo?.tracksList?.forEach { track ->
                if (track.type == LivekitModels.TrackType.AUDIO) {
                    client.mutePublishedTrack(meeting.livekitRoomName, livekitIdentity, track.sid, true).execute()
                    mutedAny = true
                }
            }
            
            if (mutedAny) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("No audio tracks found to mute"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sends an Ask to Unmute request (data channel).
     */
    suspend fun askToUnmute(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId) ?: return@withContext Result.failure(Exception("Meeting not found"))
        val participant = transaction {
            ParticipantSessionsTable.select(ParticipantSessionsTable.columns)
                .where { ParticipantSessionsTable.id eq participantId }.singleOrNull()
        } ?: return@withContext Result.failure(Exception("Participant not found"))

        val livekitIdentity = participant[ParticipantSessionsTable.livekitIdentity]
        
        return@withContext try {
            val config = com.jbcoder.meeting.configuration.AppConfig.load()
            val client = io.livekit.server.RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            // Sending a data packet for asking to unmute
            val payload = "{\"type\":\"ASK_TO_UNMUTE\"}".toByteArray(Charsets.UTF_8)
            val response = client.sendData(
                meeting.livekitRoomName,
                payload,
                LivekitModels.DataPacket.Kind.RELIABLE,
                listOf(livekitIdentity)
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Promotes a participant to CO_HOST.
     */
    suspend fun promoteToCoHost(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        changeRole(meetingId, participantId, com.jbcoder.meeting.domain.ParticipantRole.CO_HOST)
    }

    /**
     * Demotes a participant to regular PARTICIPANT.
     */
    suspend fun demoteToParticipant(meetingId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        changeRole(meetingId, participantId, com.jbcoder.meeting.domain.ParticipantRole.PARTICIPANT)
    }

    private suspend fun changeRole(meetingId: UUID, participantId: UUID, newRole: com.jbcoder.meeting.domain.ParticipantRole): Result<Unit> = withContext(Dispatchers.IO) {
        transaction {
            val participant = ParticipantSessionsTable.select(ParticipantSessionsTable.columns)
                .where { ParticipantSessionsTable.id eq participantId }.singleOrNull()
            
            if (participant == null) {
                return@transaction Result.failure(Exception("Participant not found"))
            }

            if (participant[ParticipantSessionsTable.role] == ParticipantRole.HOST.name) {
                return@transaction Result.failure(Exception("Cannot change role of HOST"))
            }

            ParticipantSessionsTable.update({ ParticipantSessionsTable.id eq participantId }) {
                it[role] = newRole.name
                it[updatedAt] = Instant.now()
            }
            
            // If they are demoted to PARTICIPANT, revoke their backend sessions
            if (newRole == com.jbcoder.meeting.domain.ParticipantRole.PARTICIPANT) {
                MeetingAuthorizationSessionsTable.update({
                    (MeetingAuthorizationSessionsTable.participantSessionId eq participantId) and 
                    (MeetingAuthorizationSessionsTable.revokedAt.isNull())
                }) {
                    it[revokedAt] = Instant.now()
                }
            }

            // Write audit event
            AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "ROLE_CHANGED"
                it[this.participantSessionId] = participantId
                it[details] = "Role changed to ${newRole.name}"
                it[createdAt] = Instant.now()
            }

            // Propagate role to LiveKit metadata
            try {
                val config = com.jbcoder.meeting.configuration.AppConfig.load()
                val client = io.livekit.server.RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
                
                val livekitIdentity = participant[ParticipantSessionsTable.livekitIdentity]
                val displayName = participant[ParticipantSessionsTable.displayName]
                val publishRestricted = participant[ParticipantSessionsTable.publishRestricted]
                val metadataJson = """{"id":"${participantId.toString()}","role":"${newRole.name}"}"""
                
                // Get existing participant to preserve permissions except what we might change
                val permission = LivekitModels.ParticipantPermission.newBuilder()
                    .setCanPublish(!publishRestricted)
                    .setCanSubscribe(true)
                    .setCanPublishData(!publishRestricted)
                    .build()

                client.updateParticipant(
                    MeetingRepository.findById(meetingId)!!.livekitRoomName,
                    livekitIdentity,
                    metadataJson,
                    displayName,
                    permission
                ).execute()
            } catch (e: Exception) {
                logger.error("Failed to propagate role change to LiveKit for $participantId", e)
            }

            Result.success(Unit)
        }
    }
}
