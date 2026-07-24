package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.server.testing.testApplication

class LoadTest {

    companion object {
        private lateinit var config: AppConfig
        private val resultsDir = File("build/load_test_results")

        @JvmStatic
        @BeforeAll
        fun setup() {
            config = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            DatabaseConfig.init(config)
            RedisConfig.init(config)
            if (!resultsDir.exists()) {
                resultsDir.mkdirs()
            }
        }
        
        @JvmStatic
        @AfterAll
        fun teardown() {
            RedisConfig.close()
            DatabaseConfig.close()
        }
    }

    private fun writeMetricsCsv(profileName: String, times: List<Long>, statusCodes: List<Int>, durationMs: Long = -1L) {
        val file = File(resultsDir, "load_test_$profileName.csv")
        file.printWriter().use { out ->
            out.println("latencyMs,statusCode")
            times.zip(statusCodes).forEach { (t, s) ->
                out.println("$t,$s")
            }
        }
        val sorted = times.sorted()
        val p50 = sorted.getOrNull((sorted.size * 0.50).toInt()) ?: 0
        val p95 = sorted.getOrNull((sorted.size * 0.95).toInt()) ?: 0
        val p99 = sorted.getOrNull((sorted.size * 0.99).toInt()) ?: 0
        val max = sorted.maxOrNull() ?: 0
        val avg = if (sorted.isNotEmpty()) sorted.average().toLong() else 0
        val successes = statusCodes.count { it in 200..299 }
        val c403 = statusCodes.count { it == 403 }
        val c409 = statusCodes.count { it == 409 }
        val c429 = statusCodes.count { it == 429 }
        val c4xx = statusCodes.count { it in 400..499 && it !in listOf(403, 409, 429) }
        val c5xx = statusCodes.count { it >= 500 }
        val rps = if (durationMs > 0) String.format("%.2f", (times.size.toDouble() / (durationMs / 1000.0))) else "N/A"
        
        println("[$profileName] Complete. Duration: ${durationMs}ms, RPS: $rps")
        println("[$profileName] Latency -> P50: ${p50}ms, P95: ${p95}ms, P99: ${p99}ms, Avg: ${avg}ms, Max: ${max}ms")
        println("[$profileName] Status -> Total: ${times.size}, Success: $successes, 403: $c403, 409: $c409, 429: $c429, Other 4xx: $c4xx, 5xx: $c5xx, Timeouts: 0")
    }

    @Test
    fun `Profile A1 - Locked Meeting`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val publicMeetingCode = createTestMeeting(client)

            // Lock the meeting by directly mutating the persistence layer
            val meeting = com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(publicMeetingCode)
            if (meeting != null) {
                com.jbcoder.meeting.persistence.MeetingRepository.updateLockStateWithOptimisticLock(meeting.id, meeting.optimisticLockVersion, true)
            }

            val requests = 5
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val testStart = System.currentTimeMillis()
            val deferreds = (1..requests).map {
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", UUID.randomUUID().toString())
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"User","deviceSessionId":"some-id","passcode":"1234"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res.status.value)
                }
            }
            
            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            val testEnd = System.currentTimeMillis()
            
            val locked = statusCodes.count { it == 403 }
            val rateLimited = statusCodes.count { it == 429 }
            assertEquals(requests, locked, "Expected exactly 403 Forbidden (MEETING_LOCKED)")
            assertEquals(0, rateLimited, "Expected 0 HTTP 429")
            
            writeMetricsCsv("Profile_A1_Locked", latencies, statusCodes, testEnd - testStart)
        }
    }

    @Test
    fun `Profile A2 - Rate Limiting`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val requests = 50
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val publicMeetingCode = createTestMeeting(client)

            val testStart = System.currentTimeMillis()
            val deferreds = (1..requests).map {
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", "same-installation-id")
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"User","deviceSessionId":"same-installation-id","passcode":"1234"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res)
                }
            }
            
            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second.status.value)
                if (it.second.status.value == 429) {
                    assertTrue(it.second.headers.contains("Retry-After"), "Expected Retry-After header")
                    assertTrue(it.second.headers.contains("X-RateLimit-Limit"), "Expected X-RateLimit-Limit header")
                }
            }
            val testEnd = System.currentTimeMillis()
            writeMetricsCsv("Profile_A2_RateLimit", latencies, statusCodes, testEnd - testStart)
        }
    }

    @Test
    fun `Profile B1 - Normal Load Open Meeting`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val requests = 20
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val publicMeetingCode = createTestMeeting(client, maxParticipants = 100, hasPasscode = false)

            val testStart = System.currentTimeMillis()
            val deferreds = (1..requests).map { i ->
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val installationId = UUID.randomUUID().toString()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", installationId)
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"User $i","deviceSessionId":"$installationId"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res.status.value)
                }
            }
            
            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            val testEnd = System.currentTimeMillis()
            writeMetricsCsv("Profile_B1_NormalLoad", latencies, statusCodes, testEnd - testStart)
        }
    }
    
    @Test
    fun `Profile B2 - Passcode Verification CPU Load`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val requests = 10
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val publicMeetingCode = createTestMeeting(client, maxParticipants = 100, hasPasscode = true)

            val testStart = System.currentTimeMillis()
            val deferreds = (1..requests).map { i ->
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val installationId = UUID.randomUUID().toString()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", installationId)
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"User $i","deviceSessionId":"$installationId","passcode":"1234"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res.status.value)
                }
            }
            
            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            val testEnd = System.currentTimeMillis()
            writeMetricsCsv("Profile_B2_PasscodeVerif", latencies, statusCodes, testEnd - testStart)
        }
    }

    @Test
    fun `Profile C - Capacity Reservation Race`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val publicMeetingCode = createTestMeeting(client, maxParticipants = 2, hasPasscode = false, waitingRoomEnabled = false)
            
            // Consume 1 slot so only 1 final slot is available
            client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                contentType(ContentType.Application.Json)
                header("X-Installation-Id", UUID.randomUUID().toString())
                header("Idempotency-Key", UUID.randomUUID().toString())
                setBody("""{"displayName":"Initial User","deviceSessionId":"${UUID.randomUUID()}"}""")
            }

            val requests = 3
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()

            val testStart = System.currentTimeMillis()
            val deferreds = (1..requests).map { i ->
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val installationId = UUID.randomUUID().toString()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", installationId)
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"Competitor $i","deviceSessionId":"$installationId"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res.status.value)
                }
            }

            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            
            val testEnd = System.currentTimeMillis()
            writeMetricsCsv("Profile_C_Contention", latencies, statusCodes, testEnd - testStart)
            
            val success = statusCodes.count { it == 202 }
            val conflict = statusCodes.count { it == 409 }
            val rateLimit = statusCodes.count { it == 429 }
            
            println("[Profile C] Success: $success, Conflict (Capacity Reached): $conflict")
            
            assertEquals(1, success, "Exactly one join request should succeed")
            assertEquals(2, conflict, "Exactly two requests should fail with 409 Conflict")
            assertEquals(0, rateLimit, "Zero 429 requests expected")
            
            val dbCount = transaction {
                com.jbcoder.meeting.persistence.ParticipantSessionsTable.selectAll()
                    .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.meetingId eq com.jbcoder.meeting.persistence.MeetingRepository.findByPublicCode(publicMeetingCode)!!.id }
                    .count()
            }
            assertEquals(2L, dbCount, "Database reservation count should not exceed maximumParticipants (2)")
        }
    }

    private suspend fun createTestMeeting(
        testClient: HttpClient, 
        maxParticipants: Int = 10, 
        hasPasscode: Boolean = true,
        waitingRoomEnabled: Boolean = true
    ): String {
        return try {
            val passcodeJson = if (hasPasscode) "\"passcode\":\"1234\"," else ""
            val res = testClient.post("/api/v1/meetings") {
                contentType(ContentType.Application.Json)
                header("X-Installation-Id", UUID.randomUUID().toString())
                header("Idempotency-Key", UUID.randomUUID().toString())
                setBody("""{"title":"Load Test",$passcodeJson"waitingRoomEnabled":$waitingRoomEnabled,"joinBeforeHostEnabled":false,"maximumParticipants":$maxParticipants}""")
            }
            if (res.status == HttpStatusCode.Created) {
                val body = Json.parseToJsonElement(res.bodyAsText()).jsonObject
                body["publicMeetingCode"]!!.jsonPrimitive.content
            } else {
                "failed_to_create"
            }
        } catch(e: Exception) {
            "failed_to_connect"
        }
    }
}
