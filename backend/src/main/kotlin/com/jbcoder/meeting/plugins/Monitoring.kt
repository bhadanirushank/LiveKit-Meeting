package com.jbcoder.meeting.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level
import java.util.UUID

fun Application.configureMonitoring() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
        
        filter { call -> call.request.path().startsWith("/") }
        
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.userAgent()
            val uri = call.request.uri
            "Status: $status, HTTP method: $httpMethod, User agent: $userAgent, URI: $uri"
        }
    }
}
