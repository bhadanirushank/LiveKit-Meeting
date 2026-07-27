package com.jbcoder.meeting.domain

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

open class AppError(
    val code: String,
    override val message: String,
    val status: io.ktor.http.HttpStatusCode = io.ktor.http.HttpStatusCode.InternalServerError
) : RuntimeException(message)

class DependencyUnavailableException(message: String) : AppError(
    code = "DEPENDENCY_UNAVAILABLE",
    message = message,
    status = io.ktor.http.HttpStatusCode.ServiceUnavailable
)
