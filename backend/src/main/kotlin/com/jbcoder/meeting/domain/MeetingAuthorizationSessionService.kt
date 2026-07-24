package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.CryptoService
import com.jbcoder.meeting.infrastructure.JwtService
import com.jbcoder.meeting.persistence.MeetingAuthorizationSessionEntity
import com.jbcoder.meeting.persistence.MeetingAuthorizationSessionsTable
import com.jbcoder.meeting.persistence.MeetingRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object MeetingAuthorizationSessionService {
    
    data class AuthorizationSessionResult(
        val accessToken: String,
        val refreshToken: String
    )

    suspend fun exchangeSecret(publicCode: String, hostSecret: String, idempotencyKey: String, installationId: String): Result<AuthorizationSessionResult> {
        val meeting = MeetingRepository.findByPublicCode(publicCode)
            ?: return Result.failure(Exception("Meeting not found"))
            
        if (!CryptoService.verify(hostSecret, meeting.hostSecretHash)) {
            return Result.failure(Exception("Invalid host secret"))
        }
        
        val idempotencyRepo = com.jbcoder.meeting.persistence.IdempotencyRepository()
        val keyHash = IdempotencyService.hash(idempotencyKey)
        val bodyFingerprint = IdempotencyService.hash("$publicCode:$hostSecret")
        
        val existing = idempotencyRepo.getRecord(keyHash, installationId)
        if (existing != null) {
            val status = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.processingStatus]
            if (status == "COMPLETED") {
                val dbFp = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.bodyFingerprint]
                if (dbFp != bodyFingerprint) {
                    return Result.failure(com.jbcoder.meeting.domain.IdempotencyException(io.ktor.http.HttpStatusCode.Conflict, "IDEMPOTENCY_KEY_REUSED"))
                }
                
                val sessionId = existing[com.jbcoder.meeting.persistence.IdempotencyKeysTable.safeResponse]
                if (sessionId != null) {
                    val sessionRow = transaction {
                        MeetingAuthorizationSessionsTable.selectAll().where { MeetingAuthorizationSessionsTable.sessionIdentifier eq sessionId }.singleOrNull()
                    }
                    if (sessionRow != null) {
                        // Generate new short-lived token and refresh token
                        val newRefreshToken = CryptoService.generateUrlSafeToken(64)
                        val newRefreshTokenHash = CryptoService.hash(newRefreshToken)
                        val accessToken = JwtService.generateHostSessionToken(
                            meetingId = meeting.id.toString(),
                            sessionIdentifier = sessionId,
                            expiryMs = 3600 * 1000 // 1 hour
                        )
                        
                        transaction {
                            MeetingAuthorizationSessionsTable.update({ MeetingAuthorizationSessionsTable.sessionIdentifier eq sessionId }) {
                                it[refreshTokenHash] = newRefreshTokenHash
                                it[lastUsedAt] = Instant.now()
                            }
                        }
                        
                        return Result.success(AuthorizationSessionResult(accessToken, newRefreshToken))
                    }
                }
            } else if (status == "PROCESSING") {
                return Result.failure(com.jbcoder.meeting.domain.IdempotencyException(io.ktor.http.HttpStatusCode.Conflict, "CONCURRENT_REQUEST_PROCESSING"))
            }
        }
        
        val created = idempotencyRepo.createKey(
            keyHash = keyHash,
            actorScope = installationId,
            meetingId = meeting.id,
            httpMethod = "POST",
            operation = "/api/v1/host-sessions/exchange",
            bodyFingerprint = bodyFingerprint,
            expiresAt = Instant.now().plusSeconds(86400)
        )
        
        if (!created) {
            return Result.failure(com.jbcoder.meeting.domain.IdempotencyException(io.ktor.http.HttpStatusCode.Conflict, "CONCURRENT_REQUEST_PROCESSING"))
        }
        
        try {
            // Generate tokens
            val sessionIdentifier = CryptoService.generateUrlSafeToken(32)
            val refreshToken = CryptoService.generateUrlSafeToken(64)
            val refreshTokenHash = CryptoService.hash(refreshToken)
            
            // Access token expires in 1 hour
            val accessToken = JwtService.generateHostSessionToken(
                meetingId = meeting.id.toString(),
                sessionIdentifier = sessionIdentifier,
                expiryMs = 3600 * 1000 // 1 hour
            )
            
            val expiresAt = Instant.now().plusSeconds(3600)
            val refreshExpiresAt = Instant.now().plusSeconds(86400 * 7) // 7 days

            // Persist session
            transaction {
                MeetingAuthorizationSessionsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[meetingId] = meeting.id
                    it[this.sessionIdentifier] = sessionIdentifier
                    it[this.refreshTokenHash] = refreshTokenHash
                    it[createdAt] = Instant.now()
                    it[this.expiresAt] = expiresAt
                    it[this.refreshExpiresAt] = refreshExpiresAt
                    it[lastUsedAt] = Instant.now()
                    it[role] = "HOST"
                    if (installationId != "anonymous") {
                        it[deviceSessionId] = installationId
                    }
                }
            }
            
            // Safe response is just the session identifier (no raw tokens)
            idempotencyRepo.completeProcessing(keyHash, installationId, 200, sessionIdentifier)
            return Result.success(AuthorizationSessionResult(accessToken, refreshToken))
        } catch (e: Exception) {
            idempotencyRepo.markFailed(keyHash, installationId)
            return Result.failure(e)
        }
    }
}
