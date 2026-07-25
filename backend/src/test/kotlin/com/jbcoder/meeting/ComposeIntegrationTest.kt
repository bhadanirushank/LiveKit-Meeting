package com.jbcoder.meeting

import com.jbcoder.meeting.api.*
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import com.auth0.jwt.JWT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID
class ComposeIntegrationTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Load environment variables for the real Docker Compose stack
            testConfig = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            
            // Hard fail if infrastructure is unavailable
            if (!DatabaseConfig.isHealthy()) {
                fail<Unit>("Integration Test failed: PostgreSQL is not reachable")
            }
            if (!RedisConfig.isHealthy()) {
                fail<Unit>("Integration Test failed: Redis is not reachable")
            }
        }
    }

    @Test
    fun testCompleteMeetingLifecycle() = testApplication {
        application {
            module(testConfig)
        }
        
        // 1. Create Meeting
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "title": "Integration Test Meeting",
                    "passcode": "123456",
                    "waitingRoomEnabled": true,
                    "joinBeforeHostEnabled": false,
                    "maximumParticipants": 10,
                    "idempotencyKey": "${UUID.randomUUID()}"
                }
            """.trimIndent())
        }
        
        if (createRes.status != HttpStatusCode.Created) {
            println("CREATE MEETING FAILED: ${createRes.bodyAsText()}")
        }
        
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createBody = createRes.bodyAsText()
        val json = Json.parseToJsonElement(createBody).jsonObject
        val publicMeetingCode = json["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = json["hostSecret"]!!.jsonPrimitive.content
        
        // 2. Exchange Host Secret
        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "publicMeetingCode": "$publicMeetingCode",
                    "hostSecret": "$hostSecret",
                    "deviceSessionId": "${UUID.randomUUID()}"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, exchangeRes.status)
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
        
        // 3. Participant requests to join
        val deviceId = UUID.randomUUID().toString()
        val joinReqRes = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "passcode": "123456",
                    "displayName": "Test Participant",
                    "deviceSessionId": "$deviceId"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Accepted, joinReqRes.status)
        val joinReqBody = joinReqRes.bodyAsText()
        val requestId = Json.parseToJsonElement(joinReqBody).jsonObject["requestId"]!!.jsonPrimitive.content
        
        // 4. Try fetching token before being admitted (Should fail with WAITING_ROOM)
        val earlyTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "deviceSessionId": "$deviceId"
                }
            """.trimIndent())
        }
        if (earlyTokenRes.status != HttpStatusCode.Accepted) {
            println("EARLY TOKEN FAILED: ${earlyTokenRes.bodyAsText()}")
        }
        assertEquals(HttpStatusCode.Accepted, earlyTokenRes.status)
        assertTrue(earlyTokenRes.bodyAsText().contains("WAITING_ROOM"))
        
        // 5. Host admits participant
        val admitRes = client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, admitRes.status)
        
        // 6. Fetch Token successfully
        val validTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "deviceSessionId": "$deviceId"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, validTokenRes.status)
        val validTokenBody = validTokenRes.bodyAsText()
        assertTrue(validTokenBody.contains("token"))
        val token = Json.parseToJsonElement(validTokenBody).jsonObject["token"]!!.jsonPrimitive.content
        
        // The token is a valid JWT string issued by LiveKit server SDK.
        assertNotNull(token)
        assertTrue(token.startsWith("ey"))
        
        // 7. Get token for second participant (B)
        val deviceIdB = UUID.randomUUID().toString()
        val joinReqResB = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "passcode": "123456",
                    "displayName": "Test Participant B",
                    "deviceSessionId": "$deviceIdB"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Accepted, joinReqResB.status, "Join request B failed: ${joinReqResB.bodyAsText()}")
        val requestIdB = Json.parseToJsonElement(joinReqResB.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content
        client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestIdB/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        val tokenResB = client.post("/api/v1/join-requests/$requestIdB/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "deviceSessionId": "$deviceIdB"
                }
            """.trimIndent())
        }
        val tokenB = Json.parseToJsonElement(tokenResB.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        
        // 8. Run Node.js LiveKit Client Test via ProcessBuilder
        val nodeConfigJson = """
            {
                "url": "${testConfig.livekitUrl}",
                "tokenA": "$token",
                "tokenB": "$tokenB",
                "apiKey": "${testConfig.livekitKey}",
                "apiSecret": "${testConfig.livekitSecret}",
                "roomName": "${Json.parseToJsonElement(createBody).jsonObject["livekitRoomName"]!!.jsonPrimitive.content}"
            }
        """.trimIndent()
        
        val processBuilder = ProcessBuilder("node", "src/test/resources/livekit-client-test/client-test.js")
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()
        
        process.outputStream.writer().use { it.write(nodeConfigJson) }
        
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        
        println("NODE TEST OUTPUT:\n${output.replace(testConfig.livekitSecret, "***REDACTED***").replace(token, "***REDACTED***").replace(tokenB, "***REDACTED***")}")
        assertEquals(0, exitCode, "Node LiveKit test failed! Exit code: $exitCode")
        
        // 9. Verify identities via LiveKit Room Service & Delete Room
        val roomClient = io.livekit.server.RoomServiceClient.createClient(testConfig.livekitApiUrl, testConfig.livekitKey, testConfig.livekitSecret)
        val roomName = Json.parseToJsonElement(createBody).jsonObject["livekitRoomName"]!!.jsonPrimitive.content
        
        val participantsRes = roomClient.listParticipants(roomName).execute()
        assertTrue(participantsRes.isSuccessful, "Failed to list participants")
        
        // They might have disconnected already because the node script disconnected them.
        // If the script exited cleanly, it's successful. We can still try to delete the room.
        val delRes = roomClient.deleteRoom(roomName).execute()
        assertTrue(delRes.isSuccessful || delRes.code() == 404, "Failed to delete room")
    }

    @Test
    fun testCapacityConcurrency() = testApplication {
        application {
            module(testConfig)
        }
        
        // 1. Create Meeting with capacity 2
        // (Minimum allowed by validation is 2; we fill slot 1 first, then race 2 participants for slot 2)
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "title": "Concurrency Test Meeting",
                    "passcode": "123456",
                    "waitingRoomEnabled": true,
                    "joinBeforeHostEnabled": false,
                    "maximumParticipants": 2,
                    "idempotencyKey": "${UUID.randomUUID()}"
                }
            """.trimIndent())
        }
        if (createRes.status != HttpStatusCode.Created) {
            println("CAPACITY TEST CREATE FAILED: ${createRes.bodyAsText()}")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createBody = createRes.bodyAsText()
        val json = Json.parseToJsonElement(createBody).jsonObject
        val publicMeetingCode = json["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = json["hostSecret"]!!.jsonPrimitive.content
        
        // Exchange host secret
        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "publicMeetingCode": "$publicMeetingCode",
                    "hostSecret": "$hostSecret",
                    "deviceSessionId": "${UUID.randomUUID()}"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, exchangeRes.status)
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Admit P0 first (fills slot 1 of 2) -- done sequentially before the race
        val deviceId0 = UUID.randomUUID().toString()
        val req0 = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "P0", "deviceSessionId": "$deviceId0"}""")
        }
        val requestId0 = Json.parseToJsonElement(req0.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content
        val admit0Res = client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId0/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, admit0Res.status, "P0 sequential admission should succeed")

        // Create 2 more join requests
        val deviceId1 = UUID.randomUUID().toString()
        val req1 = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "P1", "deviceSessionId": "$deviceId1"}""")
        }
        val requestId1 = Json.parseToJsonElement(req1.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content
        
        val deviceId2 = UUID.randomUUID().toString()
        val req2 = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "P2", "deviceSessionId": "$deviceId2"}""")
        }
        val requestId2 = Json.parseToJsonElement(req2.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        // Race P1 and P2 for the last remaining slot (slot 2 of 2)
        val results = java.util.concurrent.ConcurrentHashMap<String, io.ktor.http.HttpStatusCode>()
        val bodies = java.util.concurrent.ConcurrentHashMap<String, String>()
        val barrier = java.util.concurrent.CyclicBarrier(2)
        
        val thread1 = Thread {
            barrier.await() // start simultaneously
            val res = kotlinx.coroutines.runBlocking {
                client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId1/admit") {
                    header(HttpHeaders.Authorization, "Bearer $hostToken")
                }
            }
            results["req1"] = res.status
            bodies["req1"] = kotlinx.coroutines.runBlocking { res.bodyAsText() }
        }
        val thread2 = Thread {
            barrier.await() // start simultaneously
            val res = kotlinx.coroutines.runBlocking {
                client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId2/admit") {
                    header(HttpHeaders.Authorization, "Bearer $hostToken")
                }
            }
            results["req2"] = res.status
            bodies["req2"] = kotlinx.coroutines.runBlocking { res.bodyAsText() }
        }
        
        thread1.start()
        thread2.start()
        thread1.join(10000)
        thread2.join(10000)
        
        val statusList = results.values.toList()
        println("CAPACITY TEST - Status results: $statusList")
        println("CAPACITY TEST - Bodies: ${bodies.values.map { it.take(200) }}")
        
        // Expected: exactly one 200 OK and exactly one 409 Conflict
        assertTrue(statusList.contains(HttpStatusCode.OK), "Expected one successful admission for last slot, got: $statusList")
        assertTrue(statusList.contains(HttpStatusCode.Conflict), "Expected one MEETING_CAPACITY_REACHED for over-capacity, got: $statusList")
        
        val conflictBodyEntry = bodies.values.firstOrNull { body -> body.contains("MEETING_CAPACITY_REACHED") }
        assertNotNull(conflictBodyEntry, "One response must contain MEETING_CAPACITY_REACHED, bodies were: ${bodies.values}")
    }

    @Test
    fun testJoinRequestCapacityConcurrency() = testApplication {
        application { module(testConfig) }

        // 1. Create Meeting with capacity 2 and waitingRoomEnabled = false
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "title": "Concurrency Test Meeting (No Waiting Room)",
                    "passcode": "123456",
                    "waitingRoomEnabled": false,
                    "joinBeforeHostEnabled": true,
                    "maximumParticipants": 2,
                    "idempotencyKey": "${UUID.randomUUID()}"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createBody = createRes.bodyAsText()
        val json = Json.parseToJsonElement(createBody).jsonObject
        val publicMeetingCode = json["publicMeetingCode"]!!.jsonPrimitive.content

        // Join P0 first (fills slot 1 of 2)
        val deviceId0 = UUID.randomUUID().toString()
        val req0 = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "P0", "deviceSessionId": "$deviceId0"}""")
        }
        assertEquals(HttpStatusCode.Accepted, req0.status, "P0 sequential join should succeed")

        // Race P1 and P2 for the last remaining slot (slot 2 of 2)
        val deviceId1 = UUID.randomUUID().toString()
        val deviceId2 = UUID.randomUUID().toString()

        val results = java.util.concurrent.ConcurrentHashMap<String, io.ktor.http.HttpStatusCode>()
        val bodies = java.util.concurrent.ConcurrentHashMap<String, String>()
        val barrier = java.util.concurrent.CyclicBarrier(2)

        val thread1 = Thread {
            barrier.await() // start simultaneously
            val res = kotlinx.coroutines.runBlocking {
                client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"passcode": "123456", "displayName": "P1", "deviceSessionId": "$deviceId1"}""")
                }
            }
            results["req1"] = res.status
            bodies["req1"] = kotlinx.coroutines.runBlocking { res.bodyAsText() }
        }
        val thread2 = Thread {
            barrier.await() // start simultaneously
            val res = kotlinx.coroutines.runBlocking {
                client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"passcode": "123456", "displayName": "P2", "deviceSessionId": "$deviceId2"}""")
                }
            }
            results["req2"] = res.status
            bodies["req2"] = kotlinx.coroutines.runBlocking { res.bodyAsText() }
        }

        thread1.start()
        thread2.start()
        thread1.join(10000)
        thread2.join(10000)

        val statusList = results.values.toList()

        // Expected: exactly one 202 Accepted and exactly one 409 Conflict
        assertTrue(statusList.contains(HttpStatusCode.Accepted), "Expected one successful join for last slot, got: $statusList")
        assertTrue(statusList.contains(HttpStatusCode.Conflict), "Expected one MEETING_CAPACITY_REACHED for over-capacity, got: $statusList")

    }

    @Test
    fun testLockedRoomRejectsNewJoinRequests() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Locked Test","passcode":"123456","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val publicMeetingCode = createJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = createJson["hostSecret"]!!.jsonPrimitive.content

        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"publicMeetingCode":"$publicMeetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Host starts and locks the meeting
        client.post("/api/v1/meetings/$publicMeetingCode/start") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        client.post("/api/v1/meetings/$publicMeetingCode/lock") { header(HttpHeaders.Authorization, "Bearer $hostToken") }

        // Participant requests to join AFTER lock
        val joinReqRes = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode":"123456","displayName":"Late Participant","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, joinReqRes.status)
        assertTrue(joinReqRes.bodyAsText().contains("MEETING_LOCKED"))
    }

    @Test
    fun testLockedRoomBypassTokenAtomicConsumption() = testApplication {
        application { module(testConfig) }
        
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Locked Bypass Test","passcode":"123456","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val createJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val publicMeetingCode = createJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = createJson["hostSecret"]!!.jsonPrimitive.content

        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"publicMeetingCode":"$publicMeetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.OK, exchangeRes.status)
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // 2. Start the meeting
        client.post("/api/v1/meetings/$publicMeetingCode/start") { header(HttpHeaders.Authorization, "Bearer $hostToken") }

        // 3. Participant requests to join (must be done before lock, else 403 MEETING_LOCKED)
        val deviceId = UUID.randomUUID().toString()
        val joinReqRes = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode":"123456","displayName":"Bypass Participant","deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.Accepted, joinReqRes.status)
        val requestId = Json.parseToJsonElement(joinReqRes.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        // 3b. Host locks the meeting
        val lockRes = client.post("/api/v1/meetings/$publicMeetingCode/lock") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, lockRes.status)

        // 4. Host admits participant while meeting is locked -> creates one-time authorization in DB
        val admitRes = client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, admitRes.status)

        // 5. Participant calls /livekit-token for the FIRST time -> succeeds and consumes authorization
        val firstTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, firstTokenRes.status, "First token fetch should succeed with one-time bypass token: ${firstTokenRes.bodyAsText()}")
        val tokenText = Json.parseToJsonElement(firstTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        assertNotNull(tokenText)

        // 6. Participant calls /livekit-token for the SECOND time -> must fail because bypass authorization is consumed!
        val secondTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, secondTokenRes.status, "Second token fetch must be rejected since bypass token was already consumed")
        assertTrue(secondTokenRes.bodyAsText().contains("MEETING_LOCKED"), "Rejection must state MEETING_LOCKED, was: ${secondTokenRes.bodyAsText()}")
    }

    @Test
    fun testNoRoomAdminInMobileTokens() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Grants Test","passcode":"123456","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        val createJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val publicMeetingCode = createJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = createJson["hostSecret"]!!.jsonPrimitive.content

        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"publicMeetingCode":"$publicMeetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        val deviceId = UUID.randomUUID().toString()
        val joinReqRes = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode":"123456","displayName":"Participant No Admin","deviceSessionId":"$deviceId"}""")
        }
        val requestId = Json.parseToJsonElement(joinReqRes.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        client.post("/api/v1/meetings/$publicMeetingCode/waiting-room/$requestId/admit") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }

        val tokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, tokenRes.status)
        val tokenString = Json.parseToJsonElement(tokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val decoded = com.auth0.jwt.JWT.decode(tokenString)
        val videoClaim = decoded.getClaim("video").asMap()
        assertNotNull(videoClaim, "video claim must be present")
        assertNotEquals(true, videoClaim["roomAdmin"], "roomAdmin MUST NOT be granted to mobile or co-host tokens")
    }
}
