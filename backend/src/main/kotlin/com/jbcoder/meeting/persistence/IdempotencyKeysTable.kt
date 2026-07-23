package com.jbcoder.meeting.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object IdempotencyKeysTable : Table("idempotency_keys") {
    val keyHash = varchar("key_hash", 255)
    val actorScope = varchar("actor_scope", 255)
    val meetingId = uuid("meeting_id").nullable()
    val httpMethod = varchar("http_method", 10)
    val operation = varchar("operation", 255)
    val bodyFingerprint = varchar("body_fingerprint", 255)
    val processingStatus = varchar("processing_status", 50)
    val responseStatus = integer("response_status").nullable()
    val safeResponse = text("safe_response").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(keyHash, actorScope)
}
