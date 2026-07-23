package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.IdempotencyRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class IdempotencyException(val status: HttpStatusCode, val errorCode: String) : Exception(errorCode)

object IdempotencyService {
    @PublishedApi internal val repository = IdempotencyRepository()

    @PublishedApi internal fun hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend inline fun <reified T> executeIdempotent(
        key: String,
        actorScope: String,
        meetingId: UUID?,
        httpMethod: String,
        operation: String,
        bodyContent: String,
        ttlHours: Long = 24,
        crossinline block: suspend () -> Pair<Int, T>
    ): Pair<Int, T> {
        val keyHash = hash(key)
        val bodyFingerprint = hash(bodyContent)
        
        val existing = repository.getRecord(keyHash, actorScope)
        if (existing != null) {
            val status = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.processingStatus]
            if (status == "COMPLETED") {
                val dbFingerprint = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.bodyFingerprint]
                if (dbFingerprint != bodyFingerprint) {
                    throw IdempotencyException(HttpStatusCode.Conflict, "IDEMPOTENCY_KEY_REUSED")
                }
                
                val responseStatus = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.responseStatus] ?: 200
                val safeResponse = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.safeResponse]
                
                if (safeResponse != null) {
                    val decoded = Json.decodeFromString<T>(safeResponse)
                    return Pair(responseStatus, decoded)
                }
                
                // If safeResponse is null but completed, it might be a response without body
                // But we requested a typed T. For simplicity we will throw internal error if type T is required but null.
                throw IdempotencyException(HttpStatusCode.InternalServerError, "MISSING_SAFE_RESPONSE")
            } else if (status == "PROCESSING") {
                throw IdempotencyException(HttpStatusCode.Conflict, "CONCURRENT_REQUEST_PROCESSING")
            }
        }
        
        val created = repository.createKey(
            keyHash = keyHash,
            actorScope = actorScope,
            meetingId = meetingId,
            httpMethod = httpMethod,
            operation = operation,
            bodyFingerprint = bodyFingerprint,
            expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS)
        )
        
        if (!created) {
            throw IdempotencyException(HttpStatusCode.Conflict, "CONCURRENT_REQUEST_PROCESSING")
        }
        
        return try {
            val (status, result) = block()
            val serializedResponse = Json.encodeToString(result)
            repository.completeProcessing(keyHash, actorScope, status, serializedResponse)
            Pair(status, result)
        } catch (e: Exception) {
            repository.markFailed(keyHash, actorScope)
            throw e
        }
    }
}
