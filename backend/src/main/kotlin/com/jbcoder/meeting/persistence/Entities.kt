package com.jbcoder.meeting.persistence

import com.jbcoder.meeting.domain.JoinRequestStatus
import com.jbcoder.meeting.domain.MeetingStatus
import com.jbcoder.meeting.domain.ParticipantRole
import com.jbcoder.meeting.domain.ParticipantState
import java.time.Instant
import java.util.UUID

data class MeetingEntity(
    val id: UUID = UUID.randomUUID(),
    val publicMeetingCode: String,
    val livekitRoomName: String,
    val title: String,
    val passcodeHash: String?,
    val hostSecretHash: String,
    val status: MeetingStatus,
    val waitingRoomEnabled: Boolean,
    val joinBeforeHostEnabled: Boolean,
    val isLocked: Boolean,
    val maximumParticipants: Int,
    val scheduledStart: Instant?,
    val scheduledEnd: Instant?,
    val actualStart: Instant?,
    val actualEnd: Instant?,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val firstObservedEmptyAt: Instant? = null,
    val optimisticLockVersion: Int = 1
)

data class MeetingAuthorizationSessionEntity(
    val id: UUID = UUID.randomUUID(),
    val meetingId: UUID,
    val tokenHash: String?,
    val sessionIdentifier: String,
    val refreshTokenHash: String,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant,
    val refreshExpiresAt: Instant,
    val revokedAt: Instant?,
    val lastUsedAt: Instant,
    val deviceSessionId: String?,
    val rotationVersion: Int = 1,
    val participantSessionId: UUID? = null,
    val role: String = "HOST",
    val allowedCapabilities: String? = null
)

data class AnonymousDeviceSessionEntity(
    val id: UUID = UUID.randomUUID(),
    val installationId: String,
    val createdAt: Instant = Instant.now(),
    val lastSeenAt: Instant = Instant.now(),
    val blockedAt: Instant? = null,
    val blockReason: String? = null
)

data class ParticipantSessionEntity(
    val id: UUID = UUID.randomUUID(),
    val meetingId: UUID,
    val livekitIdentity: String,
    val displayName: String,
    val role: ParticipantRole,
    val deviceSessionId: UUID?,
    val state: ParticipantState,
    val requestedAt: Instant? = null,
    val admittedAt: Instant? = null,
    val joinedAt: Instant? = null,
    val leftAt: Instant? = null,
    val removedAt: Instant? = null,
    val tokenIssuedAt: Instant? = null,
    val rejoinCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class JoinRequestEntity(
    val id: UUID = UUID.randomUUID(),
    val meetingId: UUID,
    val participantSessionId: UUID,
    val status: JoinRequestStatus,
    val requestedAt: Instant = Instant.now(),
    val reviewedAt: Instant? = null,
    val reviewedByParticipantId: UUID? = null,
    val rejectionReason: String? = null,
    val expiresAt: Instant,
    val deviceSessionId: UUID? = null
)

data class AuditEventEntity(
    val id: UUID = UUID.randomUUID(),
    val meetingId: UUID,
    val eventType: String,
    val participantSessionId: UUID?,
    val details: String?,
    val createdAt: Instant = Instant.now()
)
