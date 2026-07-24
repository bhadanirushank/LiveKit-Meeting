package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import com.jbcoder.meeting.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch

fun main() {
    // 1. Load configuration (Dotenv or Environment Variables)
    val config = AppConfig.load()

    // 2. Initialize Database (HikariCP, Flyway, Exposed)
    DatabaseConfig.init(config)

    // 3. Initialize Redis (Lettuce)
    RedisConfig.init(config)

    // 4. Start Ktor Netty Engine
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = { module(config) })
        .start(wait = true)
}

fun Application.module(config: AppConfig) {
    configureSecurity(config)
    configureMonitoring()
    configureSerialization()
    configureErrorHandling()
    configureRouting(config)

    // Start background workers (they manage their own scope now)
    com.jbcoder.meeting.domain.WebhookReconciliationJob.start()
    com.jbcoder.meeting.domain.ScheduledCleanupJob.start()
    com.jbcoder.meeting.domain.ModerationOutboxWorker.start()

    // Graceful shutdown
    environment.monitor.subscribe(io.ktor.server.application.ApplicationStopping) {
        log.info("Application stopping. Shutting down background workers...")
        kotlinx.coroutines.runBlocking {
            com.jbcoder.meeting.domain.WebhookReconciliationJob.stop()
            com.jbcoder.meeting.domain.ScheduledCleanupJob.stop()
            com.jbcoder.meeting.domain.ModerationOutboxWorker.stop()
        }
        
        log.info("Background workers shut down. (Infrastructure connections remain open for tests/JVM shutdown)")
    }
}
