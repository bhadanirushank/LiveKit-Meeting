package com.jbcoder.meeting

import com.auth0.jwt.JWT
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.domain.ParticipantState
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class ParticipantPublishingIntegrationTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            TestSecrets.setupTestProperties()
            testConfig = AppConfig.load()
            com.jbcoder.meeting.configuration.DatabaseConfig.init(testConfig)
            com.jbcoder.meeting.configuration.RedisConfig.init(testConfig)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            com.jbcoder.meeting.configuration.DatabaseConfig.close()
            com.jbcoder.meeting.configuration.RedisConfig.close()
        }
    }

    private fun decodeVideoGrant(token: String): Map<String, Any> {
        val decoded = JWT.decode(token)
        val videoClaim = decoded.getClaim("video")
        return videoClaim.asMap()
    }

    @Test
    fun testPublishingPermissionModerationFlows() = testApplication {
        application { module(testConfig) }

        // 1. Create meeting and get host secret
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Moderation Flow Test","passcode":"123456","waitingRoomEnabled":false,"joinBeforeHostEnabled":true,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status, "Failed to create meeting: ${kotlinx.coroutines.runBlocking { createRes.bodyAsText() }}")
        val json = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val publicMeetingCode = json["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = json["hostSecret"]!!.jsonPrimitive.content

        // 2. Exchange host secret for host token
        val exchangeRes = client.post("/api/v1/host-sessions/exchange") {
            contentType(ContentType.Application.Json)
            setBody("""{"publicMeetingCode":"$publicMeetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.OK, exchangeRes.status, "Host exchange failed")
        val hostToken = Json.parseToJsonElement(exchangeRes.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content

        // 3. Participant joins
        val pDeviceId = UUID.randomUUID().toString()
        val joinReq = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "Participant 1", "deviceSessionId": "$pDeviceId"}""")
        }
        assertEquals(HttpStatusCode.Accepted, joinReq.status, "Participant join failed")
        val requestId = Json.parseToJsonElement(joinReq.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        // 4. Get LiveKit Token for participant (Initial State)
        var pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, pTokenRes.status, "Failed to get LiveKit token")
        var pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        var grant = decodeVideoGrant(pTokenStr)
        
        // Initial token should allow publishing
        assertEquals(true, grant["canPublish"])
        
        // Find participantId for moderation APIs
        // The sub of the token is the livekitIdentity, metadata is the participantSessionId.
        val decoded = JWT.decode(pTokenStr)
        val pSessionIdStr = decoded.getClaim("metadata").asString()

        // ---------------------------------------------------------
        // A. Normal audio mute
        // ---------------------------------------------------------
        val muteRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/mute") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        // Since the participant hasn't connected via WebRTC, LiveKit server returns 404, which translates to 400 Bad Request
        assertEquals(HttpStatusCode.BadRequest, muteRes.status)
        assertTrue(kotlinx.coroutines.runBlocking { muteRes.bodyAsText() }.contains("Participant not in LiveKit room"))
        // Muting sends a LiveKit data packet but DOES NOT disable publishing permission
        
        pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        grant = decodeVideoGrant(pTokenStr)
        assertEquals(true, grant["canPublish"], "Muting must not remove canPublish permission")

        // ---------------------------------------------------------
        // B. Ask to unmute
        // ---------------------------------------------------------
        val askRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/ask-to-unmute") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        // askToUnmute calls LiveKit sendData, which can silently succeed (HTTP 200) even if the participant isn't connected yet.
        assertEquals(HttpStatusCode.OK, askRes.status)
        
        pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        grant = decodeVideoGrant(pTokenStr)
        assertEquals(true, grant["canPublish"], "Asking to unmute does not affect canPublish")

        // ---------------------------------------------------------
        // C. Disable publishing
        // ---------------------------------------------------------
        val disableRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/disable-publishing") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, disableRes.status, "Disable publishing failed: ${kotlinx.coroutines.runBlocking { disableRes.bodyAsText() }}")
        
        pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        grant = decodeVideoGrant(pTokenStr)
        val canPublish = grant["canPublish"] as? Boolean ?: false
        assertEquals(false, canPublish, "Disable publishing MUST remove canPublish permission")

        // ---------------------------------------------------------
        // D. Restore publishing
        // ---------------------------------------------------------
        val restoreRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/restore-publishing") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.OK, restoreRes.status, "Restore publishing failed: ${kotlinx.coroutines.runBlocking { restoreRes.bodyAsText() }}")
        
        pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        grant = decodeVideoGrant(pTokenStr)
        assertEquals(true, grant["canPublish"], "Restore publishing MUST restore canPublish permission")

        // ---------------------------------------------------------
        // E. Screen-share restriction (Not explicitly added to API yet, but tested in domain)
        // ---------------------------------------------------------
        // For now, testing that by default screen_share is not in participant sources
        val sources = grant["canPublishSources"] as List<*>
        assertFalse(sources.contains("screen_share"), "Screen share must be disabled by default for participants")
        
        // ---------------------------------------------------------
        // F. Reconnection preserves state
        // ---------------------------------------------------------
        // Disable publishing again to test reconnection
        client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/disable-publishing") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        
        // Simulate a reconnection by requesting token again
        pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        grant = decodeVideoGrant(pTokenStr)
        val canPub = grant["canPublish"] as? Boolean ?: false
        assertEquals(false, canPub, "Replacement token reflects the current persisted permission on reconnection")
    }
}
