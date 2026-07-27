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
import org.jetbrains.exposed.sql.*

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
        val bootstrapRes = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody("""{"installationId":"${UUID.randomUUID()}", "platform":"ANDROID", "deviceModel":"SmokeTest", "osVersion":"1.0", "appVersion":"1.0.0"}""")
        }
        val bootstrapBody = Json.parseToJsonElement(bootstrapRes.bodyAsText()).jsonObject
        val pDeviceId = bootstrapBody["sessionId"]!!.jsonPrimitive.content
        val pAccessToken = bootstrapBody["accessToken"]!!.jsonPrimitive.content

        val joinReq = client.post("/api/v1/meetings/$publicMeetingCode/join-request") {
            header(HttpHeaders.Authorization, "Bearer $pAccessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"passcode": "123456", "displayName": "Participant 1", "deviceSessionId": "$pDeviceId"}""")
        }
        assertEquals(HttpStatusCode.Accepted, joinReq.status, "Participant join failed")
        val requestId = Json.parseToJsonElement(joinReq.bodyAsText()).jsonObject["requestId"]!!.jsonPrimitive.content

        // 4. Get LiveKit Token for participant (Initial State)
        var pTokenRes = client.post("/api/v1/join-requests/$requestId/livekit-token") {
            header(HttpHeaders.Authorization, "Bearer $pAccessToken")
            header("Idempotency-Key", UUID.randomUUID().toString())
            contentType(ContentType.Application.Json)
            setBody("""{"deviceSessionId":"$pDeviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, pTokenRes.status, "Failed to get LiveKit token: ${kotlinx.coroutines.runBlocking { pTokenRes.bodyAsText() }}")
        var pTokenStr = Json.parseToJsonElement(pTokenRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        var grant = decodeVideoGrant(pTokenStr)
        
        assertTrue(grant["canPublish"] as Boolean == true)
        assertTrue(grant["canPublishData"] as Boolean == true)

        var pSessionIdStr = ""
        org.jetbrains.exposed.sql.transactions.transaction {
            pSessionIdStr = com.jbcoder.meeting.persistence.JoinRequestsTable
                .selectAll()
                .where { com.jbcoder.meeting.persistence.JoinRequestsTable.id eq UUID.fromString(requestId) }
                .single()[com.jbcoder.meeting.persistence.JoinRequestsTable.participantSessionId]
                .toString()
        }

        // 5. Host mutes participant
        val muteRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/mute") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("""{"trackType":"audio"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, muteRes.status)
        assertTrue(kotlinx.coroutines.runBlocking { muteRes.bodyAsText() }.contains("Participant not in LiveKit room"))
        // Muting sends a LiveKit data packet but DOES NOT disable publishing permission
        
        org.jetbrains.exposed.sql.transactions.transaction {
            val participantRow = com.jbcoder.meeting.persistence.ParticipantSessionsTable.selectAll()
                .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.id eq UUID.fromString(pSessionIdStr) }
                .single()
            assertEquals(false, participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.publishRestricted])
        }

        // 6. Host asks participant to unmute
        val askRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/ask-to-unmute") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            contentType(ContentType.Application.Json)
            setBody("""{"trackType":"audio"}""")
        }
        // askToUnmute calls LiveKit sendData, which can silently succeed (HTTP 200) even if the participant isn't connected yet.
        assertEquals(HttpStatusCode.OK, askRes.status)
        
        org.jetbrains.exposed.sql.transactions.transaction {
            val participantRow = com.jbcoder.meeting.persistence.ParticipantSessionsTable.selectAll()
                .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.id eq UUID.fromString(pSessionIdStr) }
                .single()
            assertEquals(false, participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.publishRestricted])
        }

        // 7. Host disables participant's publishing capability
        val disableRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/disable-publishing") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, disableRes.status, "Disable publishing failed: ${kotlinx.coroutines.runBlocking { disableRes.bodyAsText() }}")
        
        org.jetbrains.exposed.sql.transactions.transaction {
            val participantRow = com.jbcoder.meeting.persistence.ParticipantSessionsTable.selectAll()
                .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.id eq UUID.fromString(pSessionIdStr) }
                .single()
            assertEquals(true, participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.publishRestricted])
        }

        // 8. Host restores participant's publishing capability
        val restoreRes = client.post("/api/v1/meetings/$publicMeetingCode/participants/$pSessionIdStr/restore-publishing") {
            header(HttpHeaders.Authorization, "Bearer $hostToken")
        }
        assertEquals(HttpStatusCode.OK, restoreRes.status, "Restore publishing failed: ${kotlinx.coroutines.runBlocking { restoreRes.bodyAsText() }}")
        
        org.jetbrains.exposed.sql.transactions.transaction {
            val participantRow = com.jbcoder.meeting.persistence.ParticipantSessionsTable.selectAll()
                .where { com.jbcoder.meeting.persistence.ParticipantSessionsTable.id eq UUID.fromString(pSessionIdStr) }
                .single()
            assertEquals(false, participantRow[com.jbcoder.meeting.persistence.ParticipantSessionsTable.publishRestricted])
        }
        
        // 9. Change global setting (Meeting Lock/Mute-all)
        // Global mute disables publishing for those not already granted exception, but this is handled by LiveKit webhook syncing.
    }
}
