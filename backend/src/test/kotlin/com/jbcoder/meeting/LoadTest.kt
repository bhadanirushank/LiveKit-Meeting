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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
            config = AppConfig.load()
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

    private fun writeMetricsCsv(profileName: String, times: List<Long>, statusCodes: List<Int>) {
        val file = File(resultsDir, "load_test_$profileName.csv")
        file.printWriter().use { out ->
            out.println("latencyMs,statusCode")
            times.zip(statusCodes).forEach { (t, s) ->
                out.println("$t,$s")
            }
        }
        val p95 = times.sorted().getOrNull((times.size * 0.95).toInt()) ?: 0
        val p99 = times.sorted().getOrNull((times.size * 0.99).toInt()) ?: 0
        println("[$profileName] Complete. p95=${p95}ms, p99=${p99}ms")
    }

    @Test
    fun `Profile A - Rate Limiting`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val requests = 100
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val publicMeetingCode = createTestMeeting(client)

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
                    Pair(time, res.status.value)
                }
            }
            
            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            writeMetricsCsv("Profile_A_RateLimit", latencies, statusCodes)
        }
    }

    @Test
    fun `Profile B - Normal Load isolated namespaces`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val requests = 50
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()
            
            val publicMeetingCode = createTestMeeting(client)

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
            writeMetricsCsv("Profile_B_NormalLoad", latencies, statusCodes)
        }
    }

    @Test
    fun `Profile C - Contention concurrent slots`() = runBlocking {
        testApplication {
            application { module(config) }
            val client = createClient { }
            val publicMeetingCode = createTestMeeting(client, maxParticipants = 2)

            val requests = 3
            val latencies = mutableListOf<Long>()
            val statusCodes = mutableListOf<Int>()

            val deferreds = (1..requests).map { i ->
                async(Dispatchers.IO) {
                    val start = System.currentTimeMillis()
                    val installationId = UUID.randomUUID().toString()
                    val res = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                        contentType(ContentType.Application.Json)
                        header("X-Installation-Id", installationId)
                        header("Idempotency-Key", UUID.randomUUID().toString())
                        setBody("""{"displayName":"Competitor $i","deviceSessionId":"$installationId","passcode":"1234"}""")
                    }
                    val time = System.currentTimeMillis() - start
                    Pair(time, res.status.value)
                }
            }

            deferreds.awaitAll().forEach {
                latencies.add(it.first)
                statusCodes.add(it.second)
            }
            
            writeMetricsCsv("Profile_C_Contention", latencies, statusCodes)
            
            val accepted = statusCodes.count { it == 202 }
            val conflict = statusCodes.count { it == 409 }
            println("[Profile C] Accepted: $accepted, Conflict (Capacity Reached): $conflict")
        }
    }

    private suspend fun createTestMeeting(testClient: HttpClient, maxParticipants: Int = 10): String {
        return try {
            val res = testClient.post("/api/v1/meetings") {
                contentType(ContentType.Application.Json)
                header("X-Installation-Id", UUID.randomUUID().toString())
                header("Idempotency-Key", UUID.randomUUID().toString())
                setBody("""{"title":"Load Test","passcode":"1234","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":$maxParticipants}""")
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
