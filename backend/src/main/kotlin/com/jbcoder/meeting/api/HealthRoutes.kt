package com.jbcoder.meeting.api

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.healthRoutes(config: AppConfig) {
    val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 3000
        }
    }

    route("/health") {
        get {
            val dbHealthy = DatabaseConfig.isHealthy()
            val redisHealthy = RedisConfig.isHealthy()
            val livekitHealthy = checkLivekit(httpClient, config.livekitUrl)
            
            val allHealthy = dbHealthy && redisHealthy && livekitHealthy
            val status = if (allHealthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            
            call.respond(
                status, 
                mapOf(
                    "status" to if (allHealthy) "UP" else "DOWN",
                    "database" to if (dbHealthy) "UP" else "DOWN",
                    "redis" to if (redisHealthy) "UP" else "DOWN",
                    "livekit" to if (livekitHealthy) "UP" else "DOWN"
                )
            )
        }

        get("/live") {
            // Simply confirms the Ktor process is accepting requests.
            call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
        }

        get("/ready") {
            // Verifies all dependencies are ready to accept traffic.
            val dbHealthy = DatabaseConfig.isHealthy()
            val redisHealthy = RedisConfig.isHealthy()
            val livekitHealthy = checkLivekit(httpClient, config.livekitUrl)
            
            if (dbHealthy && redisHealthy && livekitHealthy) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DOWN"))
            }
        }

        get("/database") {
            if (DatabaseConfig.isHealthy()) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DOWN"))
            }
        }

        get("/redis") {
            if (RedisConfig.isHealthy()) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DOWN"))
            }
        }

        get("/livekit") {
            if (checkLivekit(httpClient, config.livekitUrl)) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "UP"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "DOWN"))
            }
        }
    }
}

private suspend fun checkLivekit(client: HttpClient, url: String): Boolean {
    // Livekit healthcheck is simply checking if the root path returns an HTTP 200 OK
    // Replace ws:// or wss:// with http:// or https:// for the reachability check
    val httpUrl = url.replace("ws://", "http://").replace("wss://", "https://")
    return try {
        withContext(Dispatchers.IO) {
            val response: HttpResponse = client.get(httpUrl)
            response.status.value in 200..299
        }
    } catch (e: Exception) {
        false
    }
}
