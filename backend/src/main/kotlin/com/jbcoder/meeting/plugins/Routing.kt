package com.jbcoder.meeting.plugins

import com.jbcoder.meeting.api.healthRoutes
import com.jbcoder.meeting.api.hostSessionRoutes
import com.jbcoder.meeting.api.joinRequestRoutes
import com.jbcoder.meeting.api.meetingRoutes
import com.jbcoder.meeting.api.waitingRoomRoutes
import com.jbcoder.meeting.api.mobileSessionRoutes
import com.jbcoder.meeting.api.moderationRoutes
import com.jbcoder.meeting.api.webhookRoutes
import com.jbcoder.meeting.api.pollRoutes
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.plugins.RedisRateLimit
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: AppConfig) {
    routing {
        route("") {
            install(RedisRateLimit) {
                limit = System.getProperty("RATE_LIMIT")?.toIntOrNull() ?: 200
                windowSeconds = 60
            }
            healthRoutes(config)
            mobileSessionRoutes()
            joinRequestRoutes()
            meetingRoutes()
            hostSessionRoutes()
            webhookRoutes(config)
            pollRoutes()
        }
        
        authenticate("auth-jwt") {
            waitingRoomRoutes()
            moderationRoutes()
        }
    }
}
