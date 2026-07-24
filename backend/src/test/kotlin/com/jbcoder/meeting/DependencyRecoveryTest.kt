package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import eu.rekawek.toxiproxy.Proxy
import eu.rekawek.toxiproxy.ToxiproxyClient
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail
import java.util.UUID
import io.ktor.server.testing.testApplication
import com.jbcoder.meeting.module
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertTrue

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DependencyRecoveryTest {

    companion object {
        private lateinit var config: AppConfig
        private lateinit var toxiproxyClient: ToxiproxyClient
        
        private lateinit var postgresProxy: Proxy
        private lateinit var redisProxy: Proxy
        private lateinit var livekitProxy: Proxy

        @JvmStatic
        @BeforeAll
        fun setupSuite() {
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
                livekitUrl = "ws://127.0.0.1:17880",
                livekitApiUrl = "http://127.0.0.1:17880",
                livekitKey = TestSecrets.liveKitApiKey,
                livekitSecret = TestSecrets.liveKitApiSecret,
                jwtSecret = TestSecrets.jwtSecret,
                flywayMigrateOnStart = true
            )
            
            DatabaseConfig.init(config)
            RedisConfig.init(config)
        }
        
        @JvmStatic
        @AfterAll
        fun teardownSuite() {
            // Ensure all toxics are cleared and proxies enabled
            runBlocking { restoreProxiesAndVerify() }
            
            val proxyList = toxiproxyClient.proxies
            for (p in proxyList) {
                if (p is Proxy) {
                    p.delete()
                }
            }
            toxiproxyClient.createProxy("meeting_test_postgres", "0.0.0.0:15432", "postgres:5432")
            toxiproxyClient.createProxy("meeting_test_redis", "0.0.0.0:16379", "redis:6379")
            toxiproxyClient.createProxy("meeting_test_livekit_api", "0.0.0.0:17880", "livekit:7880")
            
            DatabaseConfig.close()
            RedisConfig.close()
        }

        private suspend fun restoreProxiesAndVerify() {
            try {
                postgresProxy.toxics().all.forEach { it.remove() }
                redisProxy.toxics().all.forEach { it.remove() }
                livekitProxy.toxics().all.forEach { it.remove() }
                postgresProxy.enable()
                redisProxy.enable()
                livekitProxy.enable()
            } catch (e: Exception) {
                println("Failed to clear toxics: \${e.message}")
            }

            // Stop workers
            com.jbcoder.meeting.domain.WebhookReconciliationJob.stop()
            com.jbcoder.meeting.domain.ScheduledCleanupJob.stop()
            com.jbcoder.meeting.domain.ModerationOutboxWorker.stop()

            // Poll for health (max 15s)
            var healthy = false
            for (i in 1..15) {
                if (DatabaseConfig.isHealthy() && RedisConfig.isHealthy()) {
                    healthy = true
                    break
                }
                delay(1000)
            }
            assertTrue(healthy, "Upstream services did not recover within 15 seconds")
        }
    }

    @BeforeEach
    fun beforeEachTest() {
        runBlocking { restoreProxiesAndVerify() }
    }

    @AfterEach
    fun afterEachTest() {
        runBlocking { restoreProxiesAndVerify() }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createClientWithTimeout() = createClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 20000
            connectTimeoutMillis = 5000
        }
    }

    @Test
    fun `test PostgreSQL unavailable before request returns 503 Service Unavailable`() = runBlocking {
        postgresProxy.disable()
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            // Handled by @AfterEach
        }
    }

    @Test
    fun `test PostgreSQL connection timeout`() = runBlocking {
        postgresProxy.toxics().timeout("timeout", ToxicDirection.UPSTREAM, 1)
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            // Handled by @AfterEach
        }
    }

    @Test
    fun `test PostgreSQL database lost during read`() = runBlocking {
        postgresProxy.toxics().limitData("limit", ToxicDirection.DOWNSTREAM, 100)
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertTrue(createRes.status == HttpStatusCode.ServiceUnavailable || createRes.status == HttpStatusCode.InternalServerError)
            }
        } finally {
            // Handled by @AfterEach
        }
    }

    @Test
    fun `test PostgreSQL database lost before transaction commit`() = runBlocking {
        postgresProxy.toxics().limitData("limit", ToxicDirection.UPSTREAM, 100)
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertTrue(createRes.status == HttpStatusCode.ServiceUnavailable || createRes.status == HttpStatusCode.InternalServerError)
            }
        } finally {
            // Handled by @AfterEach
        }
    }

    @Test
    fun `test Redis unavailable complete disconnect`() = runBlocking {
        redisProxy.disable()
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            // Handled by @AfterEach
        }
    }

    @Test
    fun `test LiveKit unavailable during participant moderation`() = runBlocking {
        livekitProxy.disable()
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.Created, createRes.status)
            }
        } finally {
            // Handled by @AfterEach
        }
    }
    
    @Test
    fun `test Redis hang regression`() = runBlocking {
        // Starts with Redis healthy
        assertTrue(RedisConfig.isHealthy())
        
        // Disconnects Redis using a latency timeout to simulate a silently dropped connection 
        // that takes too long, rather than a hard reset
        redisProxy.toxics().timeout("hang_timeout", ToxicDirection.UPSTREAM, 1)
        
        try {
            testApplication {
                application { module(config) }
                val client = createClientWithTimeout()
                
                // Proves the request finishes within the configured deadline (HttpTimeout)
                // Receives HTTP 503 with expected stable error
                val createRes = client.post("/api/v1/meetings") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Redis Hang","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
                }
                assertEquals(HttpStatusCode.ServiceUnavailable, createRes.status)
            }
        } finally {
            redisProxy.toxics().get("hang_timeout")?.remove()
        }
        
        // Restores Redis and confirms readiness recovers
        restoreProxiesAndVerify()
        assertTrue(RedisConfig.isHealthy())
        
        // Confirms a subsequent request succeeds
        testApplication {
            application { module(config) }
            val client = createClientWithTimeout()
            val createRes = client.post("/api/v1/meetings") {
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Redis Recovered","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
            }
            assertEquals(HttpStatusCode.Created, createRes.status)
        }
    }
}
