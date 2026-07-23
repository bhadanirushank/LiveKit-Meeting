package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.ModerationService
import com.jbcoder.meeting.persistence.MeetingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID
import org.jetbrains.exposed.sql.*

@Serializable
data class RemoveParticipantRequest(val reason: String? = null)

fun Route.moderationRoutes() {
    route("/api/v1/meetings/{meetingCode}/participants/{participantId}") {
        authenticate("auth-jwt") {
            post("/remove") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<RemoveParticipantRequest>()
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val removedBy = org.jetbrains.exposed.sql.transactions.transaction {
                    com.jbcoder.meeting.persistence.ParticipantSessionsTable
                        .selectAll()
                        .where { 
                            (com.jbcoder.meeting.persistence.ParticipantSessionsTable.meetingId eq meeting.id) and 
                            (com.jbcoder.meeting.persistence.ParticipantSessionsTable.role eq "HOST") 
                        }
                        .singleOrNull()?.get(com.jbcoder.meeting.persistence.ParticipantSessionsTable.id)
                } ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Host session not found"))
                
                val result = ModerationService.removeParticipant(meeting.id, participantId, removedBy, req.reason)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "REMOVED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/mute") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.muteParticipant(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "MUTED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/ask-to-unmute") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.askToUnmute(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ASKED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/disable-publishing") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.disablePublishing(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "PUBLISHING_DISABLED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/restore-publishing") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.restorePublishing(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "PUBLISHING_RESTORED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/promote-cohost") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.promoteToCoHost(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "PROMOTED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }

            post("/demote-cohost") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val participantIdStr = call.parameters["participantId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                val participantId = try { UUID.fromString(participantIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
                
                val result = ModerationService.demoteToParticipant(meeting.id, participantId)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "DEMOTED"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }
        }
    }
}
