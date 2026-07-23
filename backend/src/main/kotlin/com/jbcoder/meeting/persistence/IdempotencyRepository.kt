package com.jbcoder.meeting.persistence

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

class IdempotencyRepository {

    fun createKey(
        keyHash: String,
        actorScope: String,
        meetingId: java.util.UUID?,
        httpMethod: String,
        operation: String,
        bodyFingerprint: String,
        expiresAt: Instant
    ): Boolean {
        return transaction {
            val existing = IdempotencyKeysTable.selectAll().where {
                (IdempotencyKeysTable.keyHash eq keyHash) and
                (IdempotencyKeysTable.actorScope eq actorScope)
            }.firstOrNull()

            if (existing != null) {
                return@transaction false
            }

            IdempotencyKeysTable.insert {
                it[this.keyHash] = keyHash
                it[this.actorScope] = actorScope
                it[this.meetingId] = meetingId
                it[this.httpMethod] = httpMethod
                it[this.operation] = operation
                it[this.bodyFingerprint] = bodyFingerprint
                it[this.processingStatus] = "PROCESSING"
                it[this.createdAt] = Instant.now()
                it[this.expiresAt] = expiresAt
            }
            true
        }
    }

    fun getRecord(keyHash: String, actorScope: String): ResultRow? {
        return transaction {
            IdempotencyKeysTable.selectAll().where {
                (IdempotencyKeysTable.keyHash eq keyHash) and
                (IdempotencyKeysTable.actorScope eq actorScope)
            }.firstOrNull()
        }
    }

    fun completeProcessing(
        keyHash: String,
        actorScope: String,
        responseStatus: Int,
        safeResponse: String?
    ) {
        transaction {
            IdempotencyKeysTable.update({
                (IdempotencyKeysTable.keyHash eq keyHash) and
                (IdempotencyKeysTable.actorScope eq actorScope)
            }) {
                it[this.processingStatus] = "COMPLETED"
                it[this.responseStatus] = responseStatus
                it[this.safeResponse] = safeResponse
            }
        }
    }

    fun markFailed(keyHash: String, actorScope: String) {
        transaction {
            IdempotencyKeysTable.deleteWhere {
                (IdempotencyKeysTable.keyHash eq keyHash) and
                (IdempotencyKeysTable.actorScope eq actorScope) and
                (IdempotencyKeysTable.processingStatus eq "PROCESSING")
            }
        }
    }
}
