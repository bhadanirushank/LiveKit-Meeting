package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.CryptoService
import com.jbcoder.meeting.persistence.DeviceSessionsTable
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.and
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class SessionBootstrapResponse(
    val sessionId: String,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String
)

@Serializable
data class SessionRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String
)

object SessionService {
    // Expiry values are hardcoded for now, but in a real scenario should be loaded from AppConfig
    private const val ACCESS_TOKEN_TTL_MINUTES = 30L
    private const val REFRESH_TOKEN_TTL_DAYS = 30L

    fun bootstrapSession(
        installationId: UUID,
        platform: String,
        appVersion: String? = null
    ): SessionBootstrapResponse {
        try {
            val sessionId = UUID.randomUUID()
            
            // Generate opaque random tokens (128-bit / 16 bytes for uniqueness, maybe more. Let's use 32 bytes)
            val accessTokenRaw = "atk_" + CryptoService.generateUrlSafeToken(32)
            val refreshTokenRaw = "rtk_" + CryptoService.generateUrlSafeToken(32)
            
            val accessHash = CryptoService.sha256(accessTokenRaw)
            val refreshHash = CryptoService.sha256(refreshTokenRaw)
            
            val now = Instant.now()
            val accessExpiresAt = now.plus(1, ChronoUnit.HOURS)
            val refreshExpiresAt = now.plus(30, ChronoUnit.DAYS)
            
            transaction {
                DeviceSessionsTable.insert {
                    it[id] = sessionId
                    it[this.installationId] = installationId
                    it[accessTokenHash] = accessHash
                    it[refreshTokenHash] = refreshHash
                    it[this.accessExpiresAt] = accessExpiresAt
                    it[this.refreshExpiresAt] = refreshExpiresAt
                    it[createdAt] = now
                    it[updatedAt] = now
                    it[lastUsedAt] = now
                    it[this.platform] = platform
                    it[this.appVersion] = appVersion
                }
            }
            
            return SessionBootstrapResponse(
                sessionId = sessionId.toString(),
                accessToken = accessTokenRaw,
                refreshToken = refreshTokenRaw,
                accessTokenExpiresAt = accessExpiresAt.toString(),
                refreshTokenExpiresAt = refreshExpiresAt.toString()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    fun refreshSession(refreshToken: String): SessionRefreshResponse {
        if (!refreshToken.startsWith("rtk_")) {
            throw AppError("INVALID_REFRESH_TOKEN", "Invalid refresh token format", HttpStatusCode.Unauthorized)
        }

        val refreshHash = CryptoService.sha256(refreshToken)
        val now = Instant.now()
        
        return transaction {
            val sessionRow = DeviceSessionsTable.select { DeviceSessionsTable.refreshTokenHash eq refreshHash }.singleOrNull()
                ?: throw AppError("INVALID_REFRESH_TOKEN", "Refresh token not found", HttpStatusCode.Unauthorized)
                
            val revokedAt = sessionRow[DeviceSessionsTable.revokedAt]
            if (revokedAt != null) {
                throw AppError("SESSION_REVOKED", "Session has been revoked", HttpStatusCode.Unauthorized)
            }
            
            val refreshExpiresAt = sessionRow[DeviceSessionsTable.refreshExpiresAt]
            if (now.isAfter(refreshExpiresAt)) {
                throw AppError("SESSION_EXPIRED", "Refresh token has expired", HttpStatusCode.Unauthorized)
            }

            val sessionId = sessionRow[DeviceSessionsTable.id]

            val newAccessToken = "atk_${CryptoService.generateUrlSafeToken(32)}"
            val newRefreshToken = "rtk_${CryptoService.generateUrlSafeToken(32)}"
            val newAccessHash = CryptoService.sha256(newAccessToken)
            val newRefreshHash = CryptoService.sha256(newRefreshToken)
            
            val newAccessExpiresAt = now.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES)
            val newRefreshExpiresAt = now.plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS)

            val updatedRows = DeviceSessionsTable.update({ 
                (DeviceSessionsTable.id eq sessionId) and (DeviceSessionsTable.refreshTokenHash eq refreshHash) 
            }) {
                it[accessTokenHash] = newAccessHash
                it[refreshTokenHash] = newRefreshHash
                it[accessExpiresAt] = newAccessExpiresAt
                it[this.refreshExpiresAt] = newRefreshExpiresAt
                it[updatedAt] = now
                it[lastUsedAt] = now
            }
            
            if (updatedRows == 0) {
                throw AppError("INVALID_REFRESH_TOKEN", "Concurrent refresh detected or token invalidated", HttpStatusCode.Unauthorized)
            }

            SessionRefreshResponse(
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                accessTokenExpiresAt = newAccessExpiresAt.toString(),
                refreshTokenExpiresAt = newRefreshExpiresAt.toString()
            )
        }
    }
}
