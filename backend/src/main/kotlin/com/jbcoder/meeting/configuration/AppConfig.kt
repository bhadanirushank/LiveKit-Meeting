package com.jbcoder.meeting.configuration

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

data class AppConfig(
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val redisHost: String,
    val redisPort: Int,
    val redisPass: String,
    val livekitUrl: String,
    val livekitApiUrl: String,
    val livekitKey: String,
    val livekitSecret: String,
    val jwtSecret: String,
    val flywayMigrateOnStart: Boolean
) {
    companion object {
        fun load(): AppConfig {
            val env = try {
                dotenv {
                    directory = "../" // Look in the root LiveKit-Meeting folder for local dev
                    ignoreIfMissing = true
                }
            } catch (e: Exception) {
                null
            }

            fun getEnv(name: String): String {
                val value = System.getProperty(name) ?: System.getenv(name) ?: env?.get(name)
                if (value.isNullOrBlank()) {
                    System.err.println("CRITICAL ERROR: Missing required environment variable: $name")
                    System.exit(1)
                }
                return value!!
            }

            val dbHost = System.getProperty("POSTGRES_HOST") ?: System.getenv("POSTGRES_HOST") ?: env?.get("POSTGRES_HOST") ?: "localhost"
            val dbPort = System.getProperty("POSTGRES_PORT") ?: System.getenv("POSTGRES_PORT") ?: env?.get("POSTGRES_PORT") ?: "5432"
            val dbName = getEnv("POSTGRES_DB")

            val lkUrl = System.getProperty("LIVEKIT_URL") ?: System.getenv("LIVEKIT_URL") ?: env?.get("LIVEKIT_URL") ?: "ws://localhost:7880"
            
            return AppConfig(
                dbUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName",
                dbUser = getEnv("POSTGRES_USER"),
                dbPass = getEnv("POSTGRES_PASSWORD"),
                redisHost = System.getProperty("REDIS_HOST") ?: System.getenv("REDIS_HOST") ?: env?.get("REDIS_HOST") ?: "localhost",
                redisPort = (System.getProperty("REDIS_PORT") ?: System.getenv("REDIS_PORT") ?: env?.get("REDIS_PORT") ?: "6379").toInt(),
                redisPass = getEnv("REDIS_PASSWORD"),
                livekitUrl = lkUrl,
                livekitApiUrl = lkUrl.replace("ws://", "http://").replace("wss://", "https://"),
                livekitKey = getEnv("LIVEKIT_API_KEY"),
                livekitSecret = getEnv("LIVEKIT_API_SECRET"),
                jwtSecret = getEnv("JWT_SECRET"),
                flywayMigrateOnStart = (System.getProperty("FLYWAY_MIGRATE_ON_START") ?: System.getenv("FLYWAY_MIGRATE_ON_START") ?: env?.get("FLYWAY_MIGRATE_ON_START") ?: "true").toBoolean()
            )
        }
    }
}
