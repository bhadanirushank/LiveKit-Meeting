package com.jbcoder.meeting.api

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.persistence.WebhookEventsTable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.livekit.server.WebhookReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant

private val logger = LoggerFactory.getLogger("WebhookRoutes")

fun Route.webhookRoutes(config: AppConfig) {
    route("/api/v1/webhooks/livekit") {
        post {
            val authHeader = call.request.header("Authorization")
            if (authHeader == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val contentLength = call.request.header("Content-Length")?.toIntOrNull() ?: 0
            if (contentLength > 512 * 1024) { // 512 KB limit
                call.respond(HttpStatusCode.PayloadTooLarge, "Webhook payload too large")
                return@post
            }

            // Raw payload is required for WebhookReceiver validation
            val rawBody = call.receiveText()
            
            if (rawBody.length > 512 * 1024) {
                call.respond(HttpStatusCode.PayloadTooLarge, "Webhook payload too large")
                return@post
            }
            
            try {
                val token = authHeader.removePrefix("Bearer ").removePrefix("bearer ").trim()
                // Use official LiveKit Java/Kotlin WebhookReceiver
                val receiver = WebhookReceiver(config.livekitKey, config.livekitSecret)
                val event = receiver.receive(rawBody, token)
                
                // Livekit event.event maps to the name. e.g. "room_started"
                val eventName = event.event
                val eventId = event.id
                
                // Idempotent insertion into inbox
                withContext(Dispatchers.IO) {
                    transaction {
                        WebhookEventsTable.insertIgnore {
                            it[this.eventId] = eventId
                            it[this.eventType] = eventName
                            it[this.rawPayload] = rawBody
                            it[this.receivedAt] = Instant.now()
                            it[this.processingStatus] = "PENDING"
                        }
                    }
                }
                
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.error("Failed to process webhook", e)
                call.respond(HttpStatusCode.Unauthorized, "Invalid webhook signature")
            }
        }
    }
}
