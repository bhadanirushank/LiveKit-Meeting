package com.jbcoder.meeting.api

import com.jbcoder.meeting.domain.SessionService
import com.jbcoder.meeting.domain.AppError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SessionBootstrapRequest(
    val installationId: String,
    val platform: String,
    val appVersion: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null
)

fun Route.mobileSessionRoutes() {
    route("/api/v1/session") {
        post("/bootstrap") {
            val req = try {
                call.receive<SessionBootstrapRequest>()
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload"))
            }

            val installationIdUuid = try {
                UUID.fromString(req.installationId)
            } catch (e: Exception) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid installationId format"))
            }

            val response = SessionService.bootstrapSession(
                installationId = installationIdUuid,
                platform = req.platform,
                appVersion = req.appVersion
            )

            call.respond(HttpStatusCode.OK, response)
        }

        post("/refresh") {
            // Read Authorization header manually since we don't want to use standard mobile-bearer
            // which only accepts atk_ access tokens.
            val authHeader = call.request.header("Authorization") ?: return@post call.respond(
                HttpStatusCode.Unauthorized, 
                mapOf("error" to "Missing Authorization header")
            )

            if (!authHeader.startsWith("Bearer ")) {
                return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid Authorization format"))
            }

            val refreshToken = authHeader.removePrefix("Bearer ").trim()
            
            try {
                val response = SessionService.refreshSession(refreshToken)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: AppError) {
                call.respond(e.status, mapOf("error" to e.code, "message" to e.message))
            }
        }
    }
}
