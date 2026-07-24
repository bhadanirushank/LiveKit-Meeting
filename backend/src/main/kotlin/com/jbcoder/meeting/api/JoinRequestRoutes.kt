package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.JoinRequestService
import com.jbcoder.meeting.domain.RateLimitService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class JoinRequestDto(
    val passcode: String? = null,
    val displayName: String,
    val deviceSessionId: String
)

fun Route.joinRequestRoutes() {
    route("/api/v1/meetings/{meetingCode}/join-request") {
        post {
            val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val req = try {
                call.receive<JoinRequestDto>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body"))
                return@post
            }
            
            // Basic rate limit check based on deviceSessionId
            val ip = call.request.local.remoteHost
            if (!RateLimitService.isJoinRequestAllowed(ip, req.deviceSessionId, meetingCode)) {
                call.response.headers.append("Retry-After", "60")
                call.response.headers.append("X-RateLimit-Limit", "10")
                throw com.jbcoder.meeting.domain.AppError("RATE_LIMIT_EXCEEDED", "RATE_LIMIT_EXCEEDED", HttpStatusCode.TooManyRequests)
            }
            
            if (RateLimitService.isLocked(req.deviceSessionId)) {
                call.response.headers.append("Retry-After", "300")
                call.response.headers.append("X-RateLimit-Limit", "5")
                throw com.jbcoder.meeting.domain.AppError("RATE_LIMIT_EXCEEDED", "RATE_LIMIT_EXCEEDED", HttpStatusCode.TooManyRequests)
            }

            val command = JoinRequestService.JoinCommand(
                publicMeetingCode = meetingCode,
                passcode = req.passcode,
                displayName = req.displayName,
                deviceSessionId = req.deviceSessionId
            )
            
            val result = JoinRequestService.requestJoin(command)
            
            if (result.isSuccess) {
                val data = result.getOrThrow()
                call.respond(HttpStatusCode.Accepted, mapOf("requestId" to data.id.toString(), "status" to data.status.name))
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                if (errorMsg == "MEETING_CAPACITY_REACHED") {
                    throw com.jbcoder.meeting.domain.AppError("CAPACITY_REACHED", "MEETING_CAPACITY_REACHED", HttpStatusCode.Conflict)
                }
                if (errorMsg == "MEETING_LOCKED") {
                    throw com.jbcoder.meeting.domain.AppError("MEETING_LOCKED", "MEETING_LOCKED", HttpStatusCode.Forbidden)
                }
                RateLimitService.recordFailedAttempt(req.deviceSessionId)
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid meeting code or passcode"))
            }
        }
    }
    
    route("/api/v1/join-requests/{requestId}") {
        post("/livekit-token") {
            val requestIdStr = call.parameters["requestId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            // Just parse simple deviceSessionId from body for now
            @Serializable data class TokenReq(val deviceSessionId: String)
            val req = call.receive<TokenReq>()
            
            val requestId = java.util.UUID.fromString(requestIdStr)
            val result = com.jbcoder.meeting.domain.TokenIssuanceService.issueToken(requestId, req.deviceSessionId)
            
            if (result.isSuccess) {
                call.respond(HttpStatusCode.OK, mapOf("token" to result.getOrThrow()))
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                if (errorMsg == "WAITING_ROOM") {
                    call.respond(HttpStatusCode.Accepted, mapOf("status" to "WAITING_ROOM"))
                } else {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to errorMsg))
                }
            }
        }
    }
}
