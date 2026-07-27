package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.MeetingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.get

@Serializable
data class CreateMeetingRequest(
    val title: String,
    val passcode: String? = null,
    val waitingRoomEnabled: Boolean = true,
    val joinBeforeHostEnabled: Boolean = false,
    val maximumParticipants: Int = 100,
    val idempotencyKey: String = UUID.randomUUID().toString()
)

@Serializable
data class CreateMeetingResponse(
    val publicMeetingCode: String,
    val hostSecret: String,
    val livekitRoomName: String,
    val title: String,
    val waitingRoomEnabled: Boolean,
    val joinBeforeHostEnabled: Boolean,
    val maximumParticipants: Int
)

fun Route.meetingRoutes() {
    route("/api/v1/meetings") {
        post {
            val reqBody = call.receiveText()
            val req = try {
                kotlinx.serialization.json.Json.decodeFromString<CreateMeetingRequest>(reqBody)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
                return@post
            }
            
            // Validation
            if (req.title.isBlank() || req.title.length > 200) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid title"))
                return@post
            }
            if (req.maximumParticipants !in 2..1000) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid maximumParticipants"))
                return@post
            }
            if (req.passcode != null && (req.passcode.length !in 4..20)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid passcode format"))
                return@post
            }

            val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
            
            try {
                val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<CreateMeetingResponse>(
                    key = req.idempotencyKey,
                    actorScope = installationId,
                    meetingId = null,
                    httpMethod = call.request.httpMethod.value,
                    operation = call.request.path(),
                    bodyContent = reqBody
                ) {
                    val command = MeetingService.CreateMeetingCommand(
                        title = req.title,
                        passcode = req.passcode,
                        waitingRoomEnabled = req.waitingRoomEnabled,
                        joinBeforeHostEnabled = req.joinBeforeHostEnabled,
                        maximumParticipants = req.maximumParticipants,
                        scheduledStart = null,
                        scheduledEnd = null,
                        idempotencyKey = req.idempotencyKey
                    )
                    
                    val result = MeetingService.createMeeting(command)
                    if (result.isSuccess) {
                        val data = result.getOrThrow()
                        val res = CreateMeetingResponse(
                            publicMeetingCode = data.publicCode,
                            hostSecret = data.hostSecret,
                            livekitRoomName = data.meeting.livekitRoomName,
                            title = data.meeting.title,
                            waitingRoomEnabled = data.meeting.waitingRoomEnabled,
                            joinBeforeHostEnabled = data.meeting.joinBeforeHostEnabled,
                            maximumParticipants = data.meeting.maximumParticipants
                        )
                        Pair(201, res)
                    } else {
                        throw com.jbcoder.meeting.domain.AppError("CREATE_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.InternalServerError)
                    }
                }
                
                call.respond(HttpStatusCode.fromValue(status), responseData)
            } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
            }
        }

        authenticate("auth-jwt") {
            post("/{meetingCode}/start") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, String>>(
                        key = idempotencyKey,
                        actorScope = installationId,
                        meetingId = meeting.id,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.MeetingLifecycleService.startMeeting(meeting.id)
                        if (result.isSuccess) {
                            Pair(200, mapOf("status" to "STARTING/LIVE"))
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("START_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    call.respond(HttpStatusCode.fromValue(status), responseData)
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }

            post("/{meetingCode}/end") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, String>>(
                        key = idempotencyKey,
                        actorScope = installationId,
                        meetingId = meeting.id,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.MeetingLifecycleService.endMeeting(meeting.id)
                        if (result.isSuccess) {
                            Pair(200, mapOf("status" to "ENDING/ENDED"))
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("END_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    call.respond(HttpStatusCode.fromValue(status), responseData)
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }

            post("/{meetingCode}/cancel") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, String>>(
                        key = idempotencyKey,
                        actorScope = installationId,
                        meetingId = meeting.id,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.MeetingLifecycleService.cancelMeeting(meeting.id)
                        if (result.isSuccess) {
                            Pair(200, mapOf("status" to "CANCELLED"))
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("CANCEL_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    call.respond(HttpStatusCode.fromValue(status), responseData)
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }

            post("/{meetingCode}/lock") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, Boolean>>(
                        key = idempotencyKey,
                        actorScope = installationId,
                        meetingId = meeting.id,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.MeetingLifecycleService.lockMeeting(meeting.id)
                        if (result.isSuccess) {
                            Pair(200, mapOf("locked" to true))
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("LOCK_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    call.respond(HttpStatusCode.fromValue(status), responseData)
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }

            post("/{meetingCode}/unlock") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                    ?: return@post call.respond(HttpStatusCode.NotFound)

                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                val installationId = call.request.header("X-Installation-Id") ?: "anonymous"
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<Map<String, Boolean>>(
                        key = idempotencyKey,
                        actorScope = installationId,
                        meetingId = meeting.id,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.MeetingLifecycleService.unlockMeeting(meeting.id)
                        if (result.isSuccess) {
                            Pair(200, mapOf("locked" to false))
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("UNLOCK_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    call.respond(HttpStatusCode.fromValue(status), responseData)
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }
        }
        
        // Mobile & General Meeting endpoints
        authenticate("mobile-bearer") {
            post("/{meetingCode}/leave") {
                val meetingCode = call.parameters["meetingCode"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val idempotencyKey = call.request.header("Idempotency-Key") ?: UUID.randomUUID().toString()
                
                val mobilePrincipal = call.principal<com.jbcoder.meeting.plugins.MobileSessionPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val deviceSessionIdStr = mobilePrincipal.sessionId
                
                try {
                    val (status, responseData) = com.jbcoder.meeting.domain.IdempotencyService.executeIdempotent<String>(
                        key = idempotencyKey,
                        actorScope = deviceSessionIdStr,
                        meetingId = null,
                        httpMethod = call.request.httpMethod.value,
                        operation = call.request.path(),
                        bodyContent = ""
                    ) {
                        val result = com.jbcoder.meeting.domain.ParticipantService.leaveMeeting(meetingCode, deviceSessionIdStr)
                        if (result.isSuccess) {
                            Pair(204, "")
                        } else {
                            throw com.jbcoder.meeting.domain.AppError("LEAVE_FAILED", result.exceptionOrNull()?.message ?: "Internal Error", HttpStatusCode.BadRequest)
                        }
                    }
                    if (status == 204) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respondText((responseData as? String) ?: "", io.ktor.http.ContentType.Text.Plain, HttpStatusCode.fromValue(status))
                    }
                } catch (e: com.jbcoder.meeting.domain.IdempotencyException) {
                    throw com.jbcoder.meeting.domain.AppError(e.errorCode, e.message ?: e.errorCode, e.status)
                }
            }
        }
        
        authenticate("mobile-bearer", "auth-jwt") {
            get("/{meetingCode}/status") {
                val meetingCode = call.parameters["meetingCode"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                
                val meeting = org.jetbrains.exposed.sql.transactions.transaction {
                    com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(meetingCode)
                } ?: return@get call.respond(HttpStatusCode.NotFound)
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to meeting.status.name,
                    "title" to meeting.title
                ))
            }
        }
    }
}
