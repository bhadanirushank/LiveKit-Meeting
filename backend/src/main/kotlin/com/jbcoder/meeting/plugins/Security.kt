package com.jbcoder.meeting.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jbcoder.meeting.configuration.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import org.jetbrains.exposed.sql.*

fun Application.configureSecurity(config: AppConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        
        // Use anyHost() only for development, production should specify exact hosts.
        // For Phase 2, we are in local development mode.
        anyHost() 
    }
    
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "LiveKit Meeting Host"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer("jbcoder-meeting")
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("role").asString() == "HOST") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
        
        bearer("mobile-bearer") {
            realm = "LiveKit Meeting Mobile"
            authenticate { credential ->
                val token = credential.token
                if (!token.startsWith("atk_")) {
                    return@authenticate null
                }
                val hash = com.jbcoder.meeting.infrastructure.CryptoService.sha256(token)
                val now = java.time.Instant.now()
                
                org.jetbrains.exposed.sql.transactions.transaction {
                    val session = com.jbcoder.meeting.persistence.DeviceSessionsTable.selectAll().where {
                        com.jbcoder.meeting.persistence.DeviceSessionsTable.accessTokenHash eq hash
                    }.singleOrNull() ?: return@transaction null
                    
                    if (session[com.jbcoder.meeting.persistence.DeviceSessionsTable.revokedAt] != null) {
                        return@transaction null
                    }
                    if (now.isAfter(session[com.jbcoder.meeting.persistence.DeviceSessionsTable.accessExpiresAt])) {
                        return@transaction null
                    }
                    
                    // Update last used at
                    com.jbcoder.meeting.persistence.DeviceSessionsTable.update({ com.jbcoder.meeting.persistence.DeviceSessionsTable.id eq session[com.jbcoder.meeting.persistence.DeviceSessionsTable.id] }) {
                        it[com.jbcoder.meeting.persistence.DeviceSessionsTable.lastUsedAt] = now
                    }
                    
                    MobileSessionPrincipal(session[com.jbcoder.meeting.persistence.DeviceSessionsTable.id].toString())
                }
            }
        }
    }
}

data class MobileSessionPrincipal(val sessionId: String) : Principal
