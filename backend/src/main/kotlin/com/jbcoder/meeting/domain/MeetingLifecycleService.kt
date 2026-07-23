package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import java.util.UUID

object MeetingLifecycleService {

    suspend fun startMeeting(meetingId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        if (meeting.status != MeetingStatus.READY && meeting.status != MeetingStatus.SCHEDULED) {
            return@withContext Result.failure(Exception("Meeting is not in a valid state to start"))
        }

        // 1. Transition to STARTING
        val startingSuccess = MeetingRepository.updateStatusWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion,
            newStatus = MeetingStatus.STARTING
        )

        if (!startingSuccess) {
            return@withContext Result.failure(Exception("Concurrent modification detected. Please retry."))
        }

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_STARTING"
                it[details] = "Transitioned to STARTING while LiveKit room is being created"
                it[createdAt] = java.time.Instant.now()
            }
        }

        // 2. Create LiveKit Room
        val lkResult = LiveKitRoomService.createRoom(
            roomName = meeting.livekitRoomName,
            maxParticipants = meeting.maximumParticipants
        )

        if (lkResult.isFailure) {
            // Rollback to READY
            MeetingRepository.updateStatusWithOptimisticLock(
                id = meetingId,
                currentVersion = meeting.optimisticLockVersion + 1,
                newStatus = MeetingStatus.READY
            )
            return@withContext Result.failure(lkResult.exceptionOrNull() ?: Exception("LiveKit Room Creation Failed"))
        }

        // 3. Transition to LIVE
        val liveSuccess = MeetingRepository.updateStatusWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion + 1,
            newStatus = MeetingStatus.LIVE
        )

        if (!liveSuccess) {
            return@withContext Result.failure(Exception("Failed to transition to LIVE status"))
        }

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_STARTED"
                it[details] = "Meeting started and LiveKit room created"
                it[createdAt] = java.time.Instant.now()
            }
        }

        Result.success(Unit)
    }

    suspend fun endMeeting(meetingId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        if (meeting.status != MeetingStatus.LIVE) {
            return@withContext Result.failure(Exception("Only LIVE meetings can be ended"))
        }

        // 1. Transition to ENDING
        val endingSuccess = MeetingRepository.updateStatusWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion,
            newStatus = MeetingStatus.ENDING
        )

        if (!endingSuccess) {
            return@withContext Result.failure(Exception("Concurrent modification detected. Please retry."))
        }

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_ENDING"
                it[details] = "Transitioned to ENDING while LiveKit room is being deleted"
                it[createdAt] = java.time.Instant.now()
            }
        }

        // 2. Delete LiveKit Room (does not wait for webhook)
        val lkResult = LiveKitRoomService.deleteRoom(meeting.livekitRoomName)
        if (lkResult.isFailure) {
            println("Warning: Failed to delete LiveKit room: ${lkResult.exceptionOrNull()}")
            org.jetbrains.exposed.sql.transactions.transaction {
                com.jbcoder.meeting.persistence.ModerationOutboxTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.meetingId] = meetingId
                    // Arbitrary participant id, usually the host triggering this
                    it[actionType] = "DELETE_ROOM"
                    it[status] = "PENDING"
                    it[createdAt] = java.time.Instant.now()
                }
            }
        }

        // 3. Transition to ENDED
        MeetingRepository.updateStatusWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion + 1,
            newStatus = MeetingStatus.ENDED
        )

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_ENDED"
                it[details] = "Meeting ended by host"
                it[createdAt] = java.time.Instant.now()
            }
        }

        Result.success(Unit)
    }

    suspend fun cancelMeeting(meetingId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        if (meeting.status == MeetingStatus.LIVE || meeting.status == MeetingStatus.ENDED || meeting.status == MeetingStatus.EXPIRED || meeting.status == MeetingStatus.CANCELLED) {
            return@withContext Result.failure(Exception("Meeting cannot be cancelled from its current state"))
        }

        val success = MeetingRepository.updateStatusWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion,
            newStatus = MeetingStatus.CANCELLED
        )

        if (!success) {
            return@withContext Result.failure(Exception("Concurrent modification detected. Please retry."))
        }

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_CANCELLED"
                it[details] = "Meeting cancelled by host"
                it[createdAt] = java.time.Instant.now()
            }
        }

        Result.success(Unit)
    }

    suspend fun lockMeeting(meetingId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        if (meeting.isLocked) return@withContext Result.success(Unit)

        val success = MeetingRepository.updateLockStateWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion,
            isLocked = true
        )

        if (!success) {
            return@withContext Result.failure(Exception("Concurrent modification detected. Please retry."))
        }

        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_LOCKED"
                it[details] = "Meeting locked by host"
                it[createdAt] = java.time.Instant.now()
            }
        }
        Result.success(Unit)
    }

    suspend fun unlockMeeting(meetingId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val meeting = MeetingRepository.findById(meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        if (!meeting.isLocked) return@withContext Result.success(Unit)

        val success = MeetingRepository.updateLockStateWithOptimisticLock(
            id = meetingId,
            currentVersion = meeting.optimisticLockVersion,
            isLocked = false
        )

        if (!success) {
            return@withContext Result.failure(Exception("Concurrent modification detected. Please retry."))
        }
        
        org.jetbrains.exposed.sql.transactions.transaction {
            com.jbcoder.meeting.persistence.AuditEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[this.meetingId] = meetingId
                it[eventType] = "MEETING_UNLOCKED"
                it[details] = "Meeting unlocked by host"
                it[createdAt] = java.time.Instant.now()
            }
        }
        Result.success(Unit)
    }
}
