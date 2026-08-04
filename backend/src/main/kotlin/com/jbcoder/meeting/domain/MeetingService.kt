package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.CryptoService
import com.jbcoder.meeting.infrastructure.RedisService
import com.jbcoder.meeting.persistence.MeetingEntity
import com.jbcoder.meeting.persistence.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

object MeetingService {
    
    data class CreateMeetingCommand(
        val title: String,
        val passcode: String?,
        val waitingRoomEnabled: Boolean,
        val joinBeforeHostEnabled: Boolean,
        val maximumParticipants: Int,
        val scheduledStart: Instant?,
        val scheduledEnd: Instant?,
        val idempotencyKey: String
    )
    
    data class MeetingCreatedResult(
        val publicCode: String,
        val hostSecret: String,
        val meeting: MeetingEntity
    )

    /**
     * Creates a meeting securely, applying idempotency and generating collision-safe codes.
     */
    suspend fun createMeeting(command: CreateMeetingCommand): Result<MeetingCreatedResult> = withContext(Dispatchers.IO) {
        // Idempotency Check (TTL 1 hour)
        val idempotencyRedisKey = "idempotency:meeting:create:${command.idempotencyKey}"
        if (!RedisService.setIfAbsent(idempotencyRedisKey, "IN_PROGRESS", 3600)) {
            // Ideally we'd return the previously created meeting response, 
            // but returning an error is an acceptable minimal idempotency mechanism to prevent duplicates.
            return@withContext Result.failure(Exception("Duplicate request or request in progress"))
        }
        
        try {
            // 1. Generate Public Code
            var publicCode: String
            var retries = 0
            do {
                publicCode = CryptoService.generateSecureNumericString(10)
                val exists = MeetingRepository.findByPublicCode(publicCode) != null
                retries++
                if (retries > 5) throw Exception("Failed to generate unique meeting code")
            } while (exists)

            // 2. Generate Host Secret & Room Name
            val hostSecret = CryptoService.generateSecureRandomString(32)
            val livekitRoomName = "room_$publicCode"

            // 3. Hash Passcode and Host Secret
            val passcodeHash = command.passcode?.let { CryptoService.hash(it) }
            val hostSecretHash = CryptoService.hash(hostSecret)

            // 4. Expiry
            val expiresAt = command.scheduledEnd?.plus(1, ChronoUnit.HOURS) 
                ?: Instant.now().plus(24, ChronoUnit.HOURS)

            val status = if (command.scheduledStart != null && command.scheduledStart.isAfter(Instant.now())) {
                MeetingStatus.SCHEDULED
            } else {
                MeetingStatus.READY
            }

            // 5. Create Entity
            val entity = MeetingEntity(
                publicMeetingCode = publicCode,
                livekitRoomName = livekitRoomName,
                title = command.title,
                passcodeHash = passcodeHash,
                hostSecretHash = hostSecretHash,
                status = status,
                waitingRoomEnabled = command.waitingRoomEnabled,
                joinBeforeHostEnabled = command.joinBeforeHostEnabled,
                isLocked = false,
                maximumParticipants = command.maximumParticipants,
                scheduledStart = command.scheduledStart,
                scheduledEnd = command.scheduledEnd,
                actualStart = null,
                actualEnd = null,
                expiresAt = expiresAt
            )

            // 6. Persist to DB
            val savedEntity = MeetingRepository.createMeeting(entity)

            // Optional: Pre-create room in LiveKit if special constraints are needed
            // If it fails, we shouldn't necessarily fail the whole transaction since LiveKit auto-creates on first join,
            // but we requested strict validation.
            val lkResult = LiveKitRoomService.createRoom(
                livekitRoomName, 
                maxParticipants = command.maximumParticipants
            )
            
            if (lkResult.isFailure) {
                // To safely handle rollback, we could delete from the database. 
                // However, since it's an initial creation, we can just return the failure.
                // Or leave it in DB as DRAFT/FAILED. For now, we will mark as cancelled if LK fails.
                MeetingRepository.updateStatus(savedEntity.id, MeetingStatus.CANCELLED)
                return@withContext Result.failure(lkResult.exceptionOrNull() ?: Exception("LiveKit Room Creation Failed"))
            }

            // Successfully finished idempotency, store success
            RedisService.set(idempotencyRedisKey, "SUCCESS", 3600)
            
            Result.success(MeetingCreatedResult(publicCode, hostSecret, savedEntity))
            
        } catch (e: Exception) {
            RedisService.delete(idempotencyRedisKey)
            Result.failure(e)
        }
    }
}
