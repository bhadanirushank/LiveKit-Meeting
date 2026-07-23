package com.jbcoder.meeting.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object MeetingsTable : Table("meetings") {
    val id = uuid("id")
    val publicMeetingCode = varchar("public_meeting_code", 50).uniqueIndex()
    val livekitRoomName = varchar("livekit_room_name", 100).uniqueIndex()
    val title = varchar("title", 200)
    val passcodeHash = varchar("passcode_hash", 255).nullable()
    val hostSecretHash = varchar("host_secret_hash", 255)
    val status = varchar("status", 20)
    val waitingRoomEnabled = bool("waiting_room_enabled").default(true)
    val joinBeforeHostEnabled = bool("join_before_host_enabled").default(false)
    val isLocked = bool("is_locked").default(false)
    val maximumParticipants = integer("maximum_participants")
    val scheduledStart = timestamp("scheduled_start").nullable()
    val scheduledEnd = timestamp("scheduled_end").nullable()
    val actualStart = timestamp("actual_start").nullable()
    val actualEnd = timestamp("actual_end").nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val firstObservedEmptyAt = timestamp("first_observed_empty_at").nullable()
    val optimisticLockVersion = integer("optimistic_lock_version").default(1)

    override val primaryKey = PrimaryKey(id)
}

object MeetingAuthorizationSessionsTable : Table("meeting_authorization_sessions") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val tokenHash = varchar("token_hash", 255).nullable()
    val sessionIdentifier = varchar("session_identifier", 100).uniqueIndex()
    val refreshTokenHash = varchar("refresh_token_hash", 255)
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
    val refreshExpiresAt = timestamp("refresh_expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val lastUsedAt = timestamp("last_used_at")
    val deviceSessionId = varchar("device_session_id", 100).nullable()
    val rotationVersion = integer("rotation_version").default(1)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id).nullable()
    val role = varchar("role", 50).default("HOST")
    val allowedCapabilities = text("allowed_capabilities").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AnonymousDeviceSessionsTable : Table("anonymous_device_sessions") {
    val id = uuid("id")
    val installationId = varchar("installation_id", 100).uniqueIndex()
    val createdAt = timestamp("created_at")
    val lastSeenAt = timestamp("last_seen_at")
    val blockedAt = timestamp("blocked_at").nullable()
    val blockReason = varchar("block_reason", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ParticipantSessionsTable : Table("participant_sessions") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val livekitIdentity = varchar("livekit_identity", 100).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val role = varchar("role", 20)
    val deviceSessionId = reference("device_session_id", AnonymousDeviceSessionsTable.id).nullable()
    val state = varchar("state", 20)
    val publishRestricted = bool("publish_restricted").default(false)
    val screenShareAllowed = bool("screen_share_allowed").default(false)
    val requestedAt = timestamp("requested_at").nullable()
    val admittedAt = timestamp("admitted_at").nullable()
    val joinedAt = timestamp("joined_at").nullable()
    val leftAt = timestamp("left_at").nullable()
    val removedAt = timestamp("removed_at").nullable()
    val tokenIssuedAt = timestamp("token_issued_at").nullable()
    val rejoinCount = integer("rejoin_count").default(0)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object JoinRequestsTable : Table("join_requests") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id)
    val status = varchar("status", 20)
    val requestedAt = timestamp("requested_at")
    val reviewedAt = timestamp("reviewed_at").nullable()
    val reviewedByParticipantId = uuid("reviewed_by_participant_id").nullable()
    val rejectionReason = varchar("rejection_reason", 255).nullable()
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}

object AuditEventsTable : Table("audit_events") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val eventType = varchar("event_type", 50)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id).nullable()
    val details = text("details").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ParticipantDenyListTable : Table("participant_deny_list") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id).nullable()
    val livekitIdentity = varchar("livekit_identity", 100).nullable()
    val hashedInstallationId = varchar("hashed_installation_id", 255).nullable()
    val reason = varchar("reason", 255).nullable()
    val removedBy = reference("removed_by", ParticipantSessionsTable.id).nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}

object WebhookEventsTable : Table("webhook_events") {
    val eventId = varchar("event_id", 100)
    val eventType = varchar("event_type", 100)
    val rawPayload = text("raw_payload")
    val receivedAt = timestamp("received_at")
    val processedAt = timestamp("processed_at").nullable()
    val processingStatus = varchar("processing_status", 20)
    val retryCount = integer("retry_count").default(0)
    val failureReason = text("failure_reason").nullable()

    override val primaryKey = PrimaryKey(eventId)
}

object PollsTable : Table("polls") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val question = varchar("question", 255)
    val allowMultipleAnswers = bool("allow_multiple_answers").default(false)
    val status = varchar("status", 20)
    val createdBy = reference("created_by", ParticipantSessionsTable.id).nullable()
    val createdAt = timestamp("created_at")
    val openedAt = timestamp("opened_at").nullable()
    val closedAt = timestamp("closed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object PollOptionsTable : Table("poll_options") {
    val id = uuid("id")
    val pollId = reference("poll_id", PollsTable.id)
    val optionText = varchar("option_text", 255)
    val sortOrder = integer("sort_order")

    override val primaryKey = PrimaryKey(id)
}

object PollVotesTable : Table("poll_votes") {
    val id = uuid("id")
    val pollId = reference("poll_id", PollsTable.id)
    val pollOptionId = reference("poll_option_id", PollOptionsTable.id)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id)
    val createdAt = timestamp("created_at")
    val idempotencyKey = varchar("idempotency_key", 100).nullable().uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object LockedMeetingAuthorizationsTable : Table("locked_meeting_authorizations") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id)
    val issuedAt = timestamp("issued_at")
    val expiresAt = timestamp("expires_at")
    val consumedAt = timestamp("consumed_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
}

object ModerationOutboxTable : Table("moderation_outbox") {
    val id = uuid("id")
    val meetingId = reference("meeting_id", MeetingsTable.id)
    val participantSessionId = reference("participant_session_id", ParticipantSessionsTable.id)
    val actionType = varchar("action_type", 50)
    val status = varchar("status", 20)
    val createdAt = timestamp("created_at")
    val completedAt = timestamp("completed_at").nullable()
    val workerId = varchar("worker_id", 100).nullable()
    val claimedAt = timestamp("claimed_at").nullable()
    val nextRetryAt = timestamp("next_retry_at").nullable()
    val retryCount = integer("retry_count").default(0)
    val failureReason = text("failure_reason").nullable()
    
    override val primaryKey = PrimaryKey(id)
}
