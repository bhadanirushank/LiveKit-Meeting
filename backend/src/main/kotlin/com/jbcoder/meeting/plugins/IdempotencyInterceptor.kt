package com.jbcoder.meeting.plugins

import com.jbcoder.meeting.persistence.IdempotencyRepository
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

val IdempotencyPlugin = createRouteScopedPlugin(
    name = "IdempotencyPlugin",
    createConfiguration = ::IdempotencyConfig
) {
    val repository = pluginConfig.repository
    val ttlHours = pluginConfig.ttlHours

    onCall { call ->
        val idempotencyKey = call.request.header("Idempotency-Key") ?: return@onCall
        
        val actorScope = call.attributes.getOrNull(AttributeKey<String>("IdempotencyActor")) ?: "anonymous"
        val meetingIdStr = call.parameters["meetingCode"]
        val meetingId = try {
            meetingIdStr?.let { java.util.UUID.fromString(it) }
        } catch (e: Exception) {
            null
        }

        val httpMethod = call.request.httpMethod.value
        val operation = call.request.path()
        
        // Compute body fingerprint safely (assuming caching is enabled on application side or we just hash the raw string if already consumed, but for Ktor interceptors reading body before route is tricky without DoubleReceive)
        // For now, we will rely on a simple fingerprint hash from the key itself, since true body hashing requires DoubleReceive plugin.
        // We will assume DoubleReceive is installed and we can read text.
        val bodyText = try {
            call.receiveText()
        } catch (e: Exception) {
            ""
        }
        
        val md = MessageDigest.getInstance("SHA-256")
        val fingerprint = md.digest(bodyText.toByteArray()).joinToString("") { "%02x".format(it) }

        val keyHash = md.digest(idempotencyKey.toByteArray()).joinToString("") { "%02x".format(it) }

        // Fetch existing
        val existing = repository.getRecord(keyHash, actorScope)
        if (existing != null) {
            val status = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.processingStatus]
            if (status == "COMPLETED") {
                val responseStatus = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.responseStatus]
                val safeResponse = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.safeResponse]
                val bodyFp = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.bodyFingerprint]

                if (bodyFp != fingerprint) {
                    throw com.jbcoder.meeting.domain.AppError("INVALID_IDEMPOTENCY_KEY", "Idempotency key reused", io.ktor.http.HttpStatusCode.Conflict)
                    return@onCall // Stop processing
                }

                if (responseStatus != null) {
                    if (safeResponse != null) {
                        call.respondText(safeResponse, io.ktor.http.ContentType.Application.Json, io.ktor.http.HttpStatusCode.fromValue(responseStatus))
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.fromValue(responseStatus))
                    }
                    return@onCall // Stop processing
                }
            } else if (status == "PROCESSING") {
                throw com.jbcoder.meeting.domain.AppError("CONCURRENT_REQUEST_PROCESSING", "Concurrent request processing", io.ktor.http.HttpStatusCode.Conflict)
                return@onCall // Stop processing
            }
        } else {
            val created = repository.createKey(
                keyHash = keyHash,
                actorScope = actorScope,
                meetingId = meetingId,
                httpMethod = httpMethod,
                operation = operation,
                bodyFingerprint = fingerprint,
                expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS)
            )

            if (!created) {
                throw com.jbcoder.meeting.domain.AppError("CONCURRENT_REQUEST_PROCESSING", "Concurrent request processing", io.ktor.http.HttpStatusCode.Conflict)
                return@onCall
            }
        }
        
        call.attributes.put(AttributeKey<String>("IdempotencyKeyHash"), keyHash)
        call.attributes.put(AttributeKey<String>("IdempotencyActor"), actorScope)
    }
}

class IdempotencyConfig {
    lateinit var repository: IdempotencyRepository
    var ttlHours: Long = 24
}

fun Route.idempotent(repository: IdempotencyRepository, build: Route.() -> Unit) {
    install(IdempotencyPlugin) {
        this.repository = repository
    }
    build()
}
