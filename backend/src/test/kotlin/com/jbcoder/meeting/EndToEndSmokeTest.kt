package com.jbcoder.meeting

import com.jbcoder.meeting.api.*
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class EndToEndSmokeTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            TestSecrets.setupTestProperties()
            testConfig = AppConfig.load()
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            
            if (!DatabaseConfig.isHealthy()) fail<Unit>("Integration Test failed: PostgreSQL is not reachable")
            if (!RedisConfig.isHealthy()) fail<Unit>("Integration Test failed: Redis is not reachable")
        }
    }

    @Test
    fun testCompleteEndToEndFlow() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"E2E Smoke Test","passcode":"123456","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createBody = createRes.bodyAsText()
        val json = Json.parseToJsonElement(createBody).jsonObject
        val publicMeetingCode = json["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = json["hostSecret"]!!.jsonPrimitive.content
        val roomName = json["livekitRoomName"]!!.jsonPrimitive.content

        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"publicMeetingCode":"$publicMeetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.OK, exchangeRes.status)
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        val startRes = client.post("/api/v1/meetings/$publicMeetingCode/start") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, startRes.status)

        val hostLkRes = client.post("/api/v1/host-sessions/livekit-token") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, hostLkRes.status)
        val hostLkToken = Json.parseToJsonElement(hostLkRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val bootstrapRes = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceModel":"SmokeTest", "osVersion":"1.0", "appVersion":"1.0.0"}""")
        }
        val bootstrapBody = Json.parseToJsonElement(bootstrapRes.bodyAsText()).jsonObject
        val deviceId = bootstrapBody["deviceSessionId"]!!.jsonPrimitive.content
        val pAccessToken = bootstrapBody["accessToken"]!!.jsonPrimitive.content

        val joinReqRes = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            header(HttpHeaders.Authorization, "Bearer $pAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"passcode":"123456","displayName":"Participant","deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.Accepted, joinReqRes.status)
        val requestId = Json.parseToJsonElement(joinReqRes.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        val admitRes = client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, admitRes.status)

        val partLkRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            header(HttpHeaders.Authorization, "Bearer $pAccessToken")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, partLkRes.status)
        val partLkToken = Json.parseToJsonElement(partLkRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val nodeConfigJson = """
            {
                "url": "${testConfig.livekitUrl}",
                "tokenA": "$hostLkToken",
                "tokenB": "$partLkToken",
                "apiKey": "${testConfig.livekitKey}",
                "apiSecret": "${testConfig.livekitSecret}",
                "roomName": "$roomName"
            }
        """.trimIndent()
        
        val processBuilder = ProcessBuilder("node", "src/test/resources/livekit-client-test/client-test.js")
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        process.outputStream.writer().use { it.write(nodeConfigJson) }
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        assertEquals(0, exitCode, "LiveKit node test failed:\n$output")
        assertTrue(output.contains("SUCCESS: Both clients connected"))

        // 11. End Meeting
        val endRes = client.post("/api/v1/meetings/$publicMeetingCode/end") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, endRes.status)
        
        // 12. Lock/Unlock
        val lockRes = client.post("/api/v1/meetings/$publicMeetingCode/lock") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, lockRes.status)

        val unlockRes = client.post("/api/v1/meetings/$publicMeetingCode/unlock") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, unlockRes.status)

        // 13. Create/vote poll
        val createPollRes = client.post("/api/v1/meetings/$publicMeetingCode/polls") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Is this an E2E test?","allowMultipleAnswers":false,"options":["Yes","No"]}""")
        }
        assertEquals(HttpStatusCode.Created, createPollRes.status)
        val pollId = Json.parseToJsonElement(createPollRes.bodyAsText()).jsonObject["pollId"]!!.jsonPrimitive.content

        // 14. Participant removal
        var participantId = ""
        org.jetbrains.exposed.sql.transactions.transaction {
            participantId = com.jbcoder.meeting.persistence.JoinRequestsTable
                .selectAll()
                .where { com.jbcoder.meeting.persistence.JoinRequestsTable.id eq UUID.fromString(requestId) }
                .single()[com.jbcoder.meeting.persistence.JoinRequestsTable.participantSessionId]
                .toString()
        }

        val removeRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$participantId/remove") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Violation"}""")
        }
        assertEquals(HttpStatusCode.OK, removeRes.status)
        
        // Removed participant denied token
        val deniedTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            header(HttpHeaders.Authorization, "Bearer $pAccessToken")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, deniedTokenRes.status)
    }
}
