package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.MeetingAuthorizationSessionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import java.util.UUID

@Serializable
data class ExchangeSecretRequest(
    val publicMeetingCode: String,
    val hostSecret: String
)

@Serializable
data class HostSessionResponse(
    val accessToken: String,
    val refreshToken: String
)

fun Route.hostSessionRoutes() {
    route("/api/v1/host-sessions") {
        post("/exchange") {
            val req = call.receive<ExchangeSecretRequest>()
            
            if (req.publicMeetingCode.isBlank() || req.hostSecret.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid input"))
                return@post
            }
            
            val idempotencyKey = call.request.headers["Idempotency-Key"] ?: UUID.randomUUID().toString()
            val installationId = call.request.headers["X-Installation-Id"] ?: "anonymous"
            
            val result = MeetingAuthorizationSessionService.exchangeSecret(req.publicMeetingCode, req.hostSecret, idempotencyKey, installationId)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                call.respond(HttpStatusCode.OK, HostSessionResponse(data.accessToken, data.refreshToken))
            } else {
                val exception = result.exceptionOrNull()
                if (exception is com.jbcoder.meeting.domain.IdempotencyException) {
                    call.respond(exception.status, mapOf("error" to exception.errorCode))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid meeting code or host secret"))
                }
            }
        }
        
        authenticate("auth-jwt") {
            post("/livekit-token") {
                val principal = call.principal<JWTPrincipal>()
                val sessionId = principal?.payload?.getClaim("sessionId")?.asString() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                // Fetch the authorization session
                val sessionRow = org.jetbrains.exposed.sql.transactions.transaction {
                    com.jbcoder.meeting.persistence.MeetingAuthorizationSessionsTable
                        .selectAll()
                        .where { com.jbcoder.meeting.persistence.MeetingAuthorizationSessionsTable.sessionIdentifier eq sessionId }
                        .singleOrNull()
                } ?: return@post call.respond(HttpStatusCode.Unauthorized)
                
                val meetingId = sessionRow[com.jbcoder.meeting.persistence.MeetingAuthorizationSessionsTable.meetingId]
                
                val meeting = org.jetbrains.exposed.sql.transactions.transaction {
                    com.jbcoder.meeting.persistence.MeetingRepository.findByIdForUpdate(meetingId)
                } ?: return@post call.respond(HttpStatusCode.NotFound)
                
                // Get or create participant session for host
                val participantId = org.jetbrains.exposed.sql.transactions.transaction {
                    val existing = com.jbcoder.meeting.persistence.ParticipantSessionsTable
                        .selectAll()
                        .where { 
                            (com.jbcoder.meeting.persistence.ParticipantSessionsTable.meetingId eq meetingId) and
                            (com.jbcoder.meeting.persistence.ParticipantSessionsTable.role eq "HOST")
                        }
                        .singleOrNull()
                        
                    if (existing != null) {
                        existing[com.jbcoder.meeting.persistence.ParticipantSessionsTable.id]
                    } else {
                        val newId = java.util.UUID.randomUUID()
                        com.jbcoder.meeting.persistence.ParticipantSessionsTable.insert {
                            it[id] = newId
                            it[com.jbcoder.meeting.persistence.ParticipantSessionsTable.meetingId] = meetingId
                            it[displayName] = "Host"
                            it[livekitIdentity] = "host-$newId"
                            // Host does not have an anonymous device session, so leave deviceSessionId null
                            it[state] = com.jbcoder.meeting.domain.ParticipantState.JOINED.name
                            it[role] = "HOST"
                            
                            val now = java.time.Instant.now()
                            it[createdAt] = now
                            it[updatedAt] = now
                            it[joinedAt] = now
                        }
                        newId
                    }
                }
                
                val participantRow = org.jetbrains.exposed.sql.transactions.transaction {
                    com.jbcoder.meeting.persistence.ParticipantSessionsTable
                        .selectAll()
                        .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.id eq participantId }
                        .single()
                }
                
                val token = com.jbcoder.meeting.domain.LiveKitTokenService.createToken(
                    roomName = meeting.livekitRoomName,
                    participantIdentity = participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.livekitIdentity],
                    participantName = participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.displayName],
                    metadata = participantId.toString(),
                    role = participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.role],
                    publishRestricted = participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.publishRestricted],
                    screenShareAllowed = participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.screenShareAllowed]
                )
                
                call.respond(HttpStatusCode.OK, mapOf("token" to token))
            }
        }
    }
}
