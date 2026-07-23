package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.WaitingRoomService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RejectParticipantRequest(
    val reason: String? = null
)

fun Route.waitingRoomRoutes() {
    route("/api/v1/meetings/{meetingCode}/waiting-room") {
        get {
            val meetingCode = call.parameters["meetingCode"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            
            val result = WaitingRoomService.getPendingRequests(meetingCode)
            if (result.isSuccess) {
                val requests = result.getOrThrow().map {
                    mapOf(
                        "id" to it.id.toString(),
                        "displayName" to it.displayName,
                        "requestedAt" to it.requestedAt.toString()
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf("requests" to requests))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to (result.exceptionOrNull()?.message ?: "Unknown error")))
            }
        }
        
        post("/{requestId}/admit") {
            val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val requestIdStr = call.parameters["requestId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            
            // Note: Route is protected by authenticate("auth-jwt") in Routing.kt

            val idempotencyKey = call.request.header("Idempotency-Key") ?: java.util.UUID.randomUUID().toString()
            val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
            
            try {
                val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, String>>(
                    key = idempotencyKey,
                    actorScope = installationId,
                    meetingId = null,
                    httpMethod = call.request.httpMethod.value,
                    operation = call.request.path(),
                    bodyContent = ""
                ) {
                    val result = WaitingRoomService.admitParticipant(meetingCode, java.util.UUID.fromString(requestIdStr))
                    if (result.isSuccess) {
                        Pair(200, mapOf("status" to "ADMITTED"))
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                        if (errorMsg.contains("capacity") || errorMsg.contains("MEETING_CAPACITY_REACHED")) {
                            throw com.jbcoder.meeting.domain.AppError("CAPACITY_REACHED", "MEETING_CAPACITY_REACHED", HttpStatusCode.Conflict)
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("ADMIT_FAILED", errorMsg, HttpStatusCode.NotFound)
                        }
                    }
                }
                call.respond(HttpStatusCode.fromValue(status), responseData)
            } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                call.respond(e.status, mapOf("error" to e.errorCode))
            }
        }
        
        post("/{requestId}/reject") {
            val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val requestIdStr = call.parameters["requestId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            
            val req = call.receive<RejectParticipantRequest>()
            
            val result = WaitingRoomService.rejectParticipant(meetingCode, java.util.UUID.fromString(requestIdStr), req.reason)
            if (result.isSuccess) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "REJECTED"))
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to (result.exceptionOrNull()?.message ?: "Unknown error")))
            }
        }
    }
}
