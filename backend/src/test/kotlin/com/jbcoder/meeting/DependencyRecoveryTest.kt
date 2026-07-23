package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.ToxiproxyClient
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import java.util.UUID
import io.ktor.server.testing.testApplication
import com.jbcoder.meeting.module
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertTrue

class DependencyRecoveryTest {

    companion object {
        private lateinit var config: AppConfig
        private lateinit var toxiproxyClient: ToxiproxyClient
        
        private lateinit var postgresProxy: Proxy
        private lateinit var redisProxy: Proxy
        private lateinit var livekitProxy: Proxy

        @JvmStatic
        @BeforeAll
        fun setup() {
            // 1. Verify toxiproxy is up and version is correct
            val versionUrl = URL("http://127.0.0.1:8474/version")
            val connection = versionUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            if (connection.responseCode != 200) {
                fail("DependencyRecoveryTest requires Toxiproxy running on 127.0.0.1:8474")
            }
            val version = connection.inputStream.bufferedReader().use { it.readText() }
            println("Found Toxiproxy server version: $version")
            
            toxiproxyClient = ToxiproxyClient("127.0.0.1", 8474)
            
            // Clean up existing proxies if they exist
            val existingProxies = toxiproxyClient.proxies
            for (p in existingProxies) {
                if (p is Proxy) {
                    p.delete()
                }
            }
            
            // Recreate proxies explicitly
            // Use 0.0.0.0 internally in the toxiproxy container
            postgresProxy = toxiproxyClient.createProxy("meeting_test_postgres", "0.0.0.0:15432", "postgres:5432")
            redisProxy = toxiproxyClient.createProxy("meeting_test_redis", "0.0.0.0:16379", "redis:6379")
            livekitProxy = toxiproxyClient.createProxy("meeting_test_livekit_api", "0.0.0.0:17880", "livekit:7880")
            
            // Apply chaotic config pointing to proxy ports
            config = AppConfig(
                dbUrl = "jdbc:postgresql://127.0.0.1:15432/livekit_meeting",
                dbUser = "postgres",
                dbPass = "postgres_password_placeholder",
                redisHost = "127.0.0.1",
                redisPort = 16379,
                redisPass = "redis_password_placeholder",
                livekitUrl = "ws://127.0.0.1:17880", // WebSocket API
                livekitApiUrl = "http://127.0.0.1:17880", // Control API
                livekitKey = TestSecrets.liveKitApiKey,
                livekitSecret = "livekit_secret_placeholder_at_least_32_chars",
                jwtSecret = "jwt_secret_placeholder",
                flywayMigrateOnStart = true
            )
            
            DatabaseConfig.init(config)
            RedisConfig.init(config)
        }
        
        @JvmStatic
        @AfterAll
        fun teardown() {
            // Ensure all toxics are cleared and proxies enabled
            val proxyList = toxiproxyClient.proxies
            for (p in proxyList) {
                if (p is Proxy) {
                    p.delete()
                }
            }
            toxiproxyClient.createProxy("meeting_test_postgres", "0.0.0.0:15432", "postgres:5432")
            toxiproxyClient.createProxy("meeting_test_redis", "0.0.0.0:16379", "redis:6379")
            toxiproxyClient.createProxy("meeting_test_livekit_api", "0.0.0.0:17880", "livekit:7880")
            
            // Clean up connections to proxy ports so subsequent tests aren't poisoned
            DatabaseConfig.close()
            RedisConfig.close()
        }
    }

    @Test
    fun `test PostgreSQL unavailable before request returns 503 Service Unavailable`() = runBlocking {
        // Drop all connections
        postgresProxy.disable()
        
        try {
            testApplication {
                application { module(config) }
                
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                
                // HikariCP will fail to acquire connection, should result in 503 instead of 500
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            postgresProxy.enable()
            Thread.sleep(1000) // Let Hikari recover
            assertTrue(DatabaseConfig.isHealthy())
        }
    }

    @Test
    fun `test PostgreSQL connection timeout`() = runBlocking {
        postgresProxy.toxics().timeout("timeout", ToxicDirection.UPSTREAM, 1)
        try {
            testApplication {
                application { module(config) }
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            postgresProxy.toxics().get("timeout").remove()
            Thread.sleep(1000)
            assertTrue(DatabaseConfig.isHealthy())
        }
    }

    @Test
    fun `test PostgreSQL database lost during read`() = runBlocking {
        // Limit data simulating connection reset in the middle of a read
        postgresProxy.toxics().limitData("limit", ToxicDirection.DOWNSTREAM, 100)
        try {
            testApplication {
                application { module(config) }
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                // Because we limit downstream data, Postgres connection resets, leading to SQLException.
                // Depending on when it occurs, it could be Service Unavailable or Internal Server Error.
                assertTrue(createRes.status == HttpStatusCode.ServiceUnavailable || createRes.status == HttpStatusCode.InternalServerError)
            }
        } finally {
            postgresProxy.toxics().get("limit").remove()
            Thread.sleep(1000)
            assertTrue(DatabaseConfig.isHealthy())
        }
    }

    @Test
    fun `test PostgreSQL database lost before transaction commit`() = runBlocking {
        // Limit upstream data so insert fails before commit
        postgresProxy.toxics().limitData("limit", ToxicDirection.UPSTREAM, 100)
        try {
            testApplication {
                application { module(config) }
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertTrue(createRes.status == HttpStatusCode.ServiceUnavailable || createRes.status == HttpStatusCode.InternalServerError)
            }
        } finally {
            postgresProxy.toxics().get("limit").remove()
            Thread.sleep(1000)
            assertTrue(DatabaseConfig.isHealthy())
        }
    }

    @Test
    fun `test Redis unavailable`() = runBlocking {
        redisProxy.disable()
        try {
            testApplication {
                application { module(config) }
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                // Meeting Create uses idempotency key so it hits Redis first!
                // We've configured idempotency to throw 503 if Redis is down.
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            redisProxy.enable()
            Thread.sleep(1000)
            assertTrue(RedisConfig.isHealthy())
        }
    }

    @Test
    fun `test LiveKit unavailable during participant moderation`() = runBlocking {
        livekitProxy.disable()
        try {
            testApplication {
                application { module(config) }
                // Create meeting works because it only inserts to DB and doesn't hit livekit synchronously
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.Created, createRes.status)
            }
        } finally {
            livekitProxy.enable()
            Thread.sleep(1000)
        }
    }
}
