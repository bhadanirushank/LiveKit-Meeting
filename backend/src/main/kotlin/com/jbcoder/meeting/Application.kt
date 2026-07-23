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

    // 4. Register Shutdown hook for graceful degradation
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down meeting backend...")
        RedisConfig.close()
        DatabaseConfig.close()
    })

    // 5. Start Ktor Netty Engine
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = { module(config) })
        .start(wait = true)
}

fun Application.module(config: AppConfig) {
    configureSecurity(config)
    configureMonitoring()
    configureSerialization()
    configureErrorHandling()
    configureRouting(config)

    launch {
        com.jbcoder.meeting.domain.WebhookReconciliationJob.startLoop()
    }
    launch {
        com.jbcoder.meeting.domain.ScheduledCleanupJob.startLoop()
    }
    launch {
        com.jbcoder.meeting.domain.ModerationOutboxWorker.start()
    }
}
