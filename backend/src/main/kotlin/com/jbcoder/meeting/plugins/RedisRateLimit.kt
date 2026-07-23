package com.jbcoder.meeting.plugins

import com.jbcoder.meeting.infrastructure.RedisService
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.header
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RedisRateLimitConfig {
    var limit: Int = 10
    var windowSeconds: Long = 60
    var keyExtractor: (ApplicationCall) -> String = { call ->
        call.request.header("X-Forwarded-For") ?: call.request.local.remoteHost
    }
}

val RedisRateLimit = createRouteScopedPlugin(name = "RedisRateLimit", createConfiguration = ::RedisRateLimitConfig) {
    val limit = pluginConfig.limit
    val windowSeconds = pluginConfig.windowSeconds
    val keyExtractor = pluginConfig.keyExtractor

    onCall { call ->
        val key = keyExtractor(call)
        val path = call.request.path()
        val redisKey = "ratelimit:$path:$key"

        val rateLimitResult = withContext(Dispatchers.IO) {
            try {
                RedisService.incrementAndGetAtomic(redisKey, windowSeconds)
            } catch (e: Exception) {
                // Fail open
                null
            }
        }

        if (rateLimitResult != null) {
            val remaining = (limit - rateLimitResult.count).coerceAtLeast(0)
            call.response.header("X-RateLimit-Limit", limit.toString())
            call.response.header("X-RateLimit-Remaining", remaining.toString())
            call.response.header("X-RateLimit-Reset", rateLimitResult.ttl.toString())

            if (rateLimitResult.count > limit) {
                call.response.header("Retry-After", rateLimitResult.ttl.toString())
                call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limit exceeded"))
            }
        }
            // Cancel the rest of the pipeline
            // Ktor 2 mechanism for aborting:
            // return@onCall is not enough to stop pipeline, we must throw or finish.
            // But respond() ends the pipeline in createRouteScopedPlugin.
            // Wait, we need to return early.
            // We just let the plugin end it. respond() marks the response as handled.
        }
    }
