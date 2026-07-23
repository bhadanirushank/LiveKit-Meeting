package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.PollService
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

@Serializable
data class CreatePollRequest(
    val question: String,
    val allowMultipleAnswers: Boolean = false,
    val options: List<String>
)

@Serializable
data class VoteRequest(
    val optionId: String,
    val participantId: String
)

fun Route.pollRoutes() {
    route("/api/v1/meetings/{meetingCode}/polls") {
        
        // Host creates poll
        authenticate("auth-jwt") {
            post {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val req = call.receive<CreatePollRequest>()
                
                val meeting = MeetingRepository.findByPublicCode(meetingCode) ?: return@post call.respond(HttpStatusCode.NotFound)
                
                // Fetch host from JWT (placeholder createdBy)
                val createdBy = UUID.randomUUID()
                
                val command = PollService.CreatePollCommand(
                    meetingId = meeting.id,
                    question = req.question,
                    allowMultipleAnswers = req.allowMultipleAnswers,
                    options = req.options,
                    createdBy = null  // Host session - no participant row required
                )
                
                val result = PollService.createPoll(command)
                if (result.isSuccess) {
                    call.respond(HttpStatusCode.Created, mapOf("pollId" to result.getOrThrow().toString()))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
                }
            }
        }
        
        // Participant votes (No auth-jwt since they might just have a session token or LiveKit token)
        // In reality, this should be authenticated by a participant token, but for Phase 4 we just stub it.
        post("/{pollId}/vote") {
            val pollIdStr = call.parameters["pollId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val req = call.receive<VoteRequest>()
            
            val pollId = try { UUID.fromString(pollIdStr) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
            val optionId = try { UUID.fromString(req.optionId) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
            val participantId = try { UUID.fromString(req.participantId) } catch(e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
            
            val result = PollService.vote(pollId, optionId, participantId)
            if (result.isSuccess) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "VOTED"))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.exceptionOrNull()?.message))
            }
        }
    }
}
