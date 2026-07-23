package com.jbcoder.meeting.plugins

import com.jbcoder.meeting.domain.AppError
import com.jbcoder.meeting.domain.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import java.time.Instant

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<AppError> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(
                    code = cause.code,
                    message = cause.message,
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
        exception<java.sql.SQLException> { call, cause ->
            call.application.environment.log.error("Database connection error", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    code = "SERVICE_UNAVAILABLE",
                    message = "Service is temporarily unavailable due to database connectivity issues.",
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
        
        exception<org.jetbrains.exposed.exceptions.ExposedSQLException> { call, cause ->
            call.application.environment.log.error("Database error", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    code = "SERVICE_UNAVAILABLE",
                    message = "Service is temporarily unavailable due to database connectivity issues.",
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
        
        exception<io.lettuce.core.RedisException> { call, cause ->
            call.application.environment.log.error("Redis connection error", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    code = "SERVICE_UNAVAILABLE",
                    message = "Service is temporarily unavailable due to cache connectivity issues.",
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
        
        exception<java.net.ConnectException> { call, cause ->
            call.application.environment.log.error("Upstream connection error", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(
                    code = "SERVICE_UNAVAILABLE",
                    message = "Service is temporarily unavailable due to upstream connectivity issues.",
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    code = "INTERNAL_SERVER_ERROR",
                    message = "An unexpected error occurred.",
                    requestId = call.callId ?: "unknown",
                    timestamp = Instant.now().toString()
                )
            )
        }
    }
}
