package com.jbcoder.meeting

import com.jbcoder.meeting.api.*
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import com.jbcoder.meeting.persistence.WebhookEventsTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class LifecycleAndWebhookTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            testConfig = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            if (!DatabaseConfig.isHealthy()) fail<Unit>("PostgreSQL unreachable")
            if (!RedisConfig.isHealthy()) fail<Unit>("Redis unreachable")
        }
    }

    private fun makeWebhookJwt(body: String): String {
        val sha256Bytes = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
        val hashBase64 = java.util.Base64.getEncoder().encodeToString(sha256Bytes)
        return JWT.create()
            .withIssuer(testConfig.livekitKey)
            .withClaim("sha256", hashBase64)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256(testConfig.livekitSecret))
    }

    @Test
    fun testWebhookIdempotency() = testApplication {
        application { module(testConfig) }
        val eventId = UUID.randomUUID().toString()
        val body = """{"event":"room_started","id":"$eventId","createdAt":${System.currentTimeMillis() / 1000},"room":{"sid":"test","name":"webhook-room","emptyTimeout":300,"maxParticipants":0,"creationTime":${System.currentTimeMillis() / 1000},"metadata":"","numParticipants":0}}"""
        val auth = makeWebhookJwt(body)
        val res1 = client.post("/api/v1/webhooks/livekit") {
            contentType(ContentType.parse("application/webhook+json"))
            header(HttpHeaders.Authorization, auth)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, res1.status, "First webhook delivery should succeed")
        val res2 = client.post("/api/v1/webhooks/livekit") {
            contentType(ContentType.parse("application/webhook+json"))
            header(HttpHeaders.Authorization, auth)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, res2.status, "Duplicate webhook should return 200 (idempotent)")
        val count = transaction { WebhookEventsTable.selectAll().where { WebhookEventsTable.eventId eq eventId }.count() }
        assertEquals(1L, count, "Should have exactly one DB record for duplicate webhooks")
    }

    @Test
    fun testWebhookInvalidSignatureRejected() = testApplication {
        application { module(testConfig) }
        val body = """{"event":"room_finished","id":"${UUID.randomUUID()}","createdAt":${System.currentTimeMillis() / 1000},"room":{"sid":"x","name":"y","emptyTimeout":300,"maxParticipants":0,"creationTime":1,"metadata":"","numParticipants":0}}"""
        val res = client.post("/api/v1/webhooks/livekit") {
            contentType(ContentType.parse("application/webhook+json"))
            header(HttpHeaders.Authorization, "Bearer invalid.jwt.token")
            setBody(body)
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status, "Invalid JWT should be rejected")
    }

    @Test
    fun testWebhookBodyModificationRejected() = testApplication {
        application { module(testConfig) }
        val originalBody = """{"event":"participant_joined","id":"${UUID.randomUUID()}","createdAt":${System.currentTimeMillis() / 1000},"participant":{"sid":"ps1","identity":"p1","name":"Test"},"room":{"sid":"r1","name":"room1","emptyTimeout":300,"maxParticipants":0,"creationTime":1,"metadata":"","numParticipants":1}}"""
        val auth = makeWebhookJwt(originalBody)
        val tamperedBody = originalBody.replace("participant_joined", "room_finished")
        val res = client.post("/api/v1/webhooks/livekit") {
            contentType(ContentType.parse("application/webhook+json"))
            header(HttpHeaders.Authorization, auth)
            setBody(tamperedBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status, "Tampered body must be rejected")
    }

    @Test
    fun testWebhookPayloadSizeLimit() = testApplication {
        application { module(testConfig) }
        val overSizeBody = "x".repeat(600 * 1024)
        val res = client.post("/api/v1/webhooks/livekit") {
            header("Content-Length", overSizeBody.length.toString())
            contentType(ContentType.parse("application/webhook+json"))
            header(HttpHeaders.Authorization, "Bearer dummy")
            setBody(overSizeBody)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, res.status, "Oversized payload should be rejected")
    }

    @Test
    fun testLifecycleStartAndEnd() = testApplication {
        application { module(testConfig) }
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Lifecycle Test","passcode":"abc123","waitingRoomEnabled":false,"joinBeforeHostEnabled":true,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val meetingJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val meetingCode = meetingJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = meetingJson["hostSecret"]!!.jsonPrimitive.content
        val hostToken = Json.parseToJsonElement(
            client.post("/api/v1/host-sessions/exchange") {
                contentType(ContentType.Application.Json)
                setBody("""{"publicMeetingCode":"$meetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
            }.bodyAsText()
        ).jsonObject["accessToken"]!!.jsonPrimitive.content
        val startRes = client.post("/api/v1/meetings/$meetingCode/start") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, startRes.status, "Start: ${startRes.bodyAsText()}")
        val endRes = client.post("/api/v1/meetings/$meetingCode/end") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, endRes.status, "End: ${endRes.bodyAsText()}")
    }

    @Test
    fun testLifecycleCancelFromReady() = testApplication {
        application { module(testConfig) }
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Cancel Test","passcode":"abc123","waitingRoomEnabled":false,"joinBeforeHostEnabled":true,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        val meetingJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val meetingCode = meetingJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = meetingJson["hostSecret"]!!.jsonPrimitive.content
        val hostToken = Json.parseToJsonElement(
            client.post("/api/v1/host-sessions/exchange") {
                contentType(ContentType.Application.Json)
                setBody("""{"publicMeetingCode":"$meetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
            }.bodyAsText()
        ).jsonObject["accessToken"]!!.jsonPrimitive.content
        val cancelRes = client.post("/api/v1/meetings/$meetingCode/cancel") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, cancelRes.status, "Cancel from READY: ${cancelRes.bodyAsText()}")
    }

    @Test
    fun testLifecycleLockUnlock() = testApplication {
        application { module(testConfig) }
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Lock Test","passcode":"abc123","waitingRoomEnabled":false,"joinBeforeHostEnabled":true,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        val meetingJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val meetingCode = meetingJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = meetingJson["hostSecret"]!!.jsonPrimitive.content
        val hostToken = Json.parseToJsonElement(
            client.post("/api/v1/host-sessions/exchange") {
                contentType(ContentType.Application.Json)
                setBody("""{"publicMeetingCode":"$meetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
            }.bodyAsText()
        ).jsonObject["accessToken"]!!.jsonPrimitive.content
        client.post("/api/v1/meetings/$meetingCode/start") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        val lockRes = client.post("/api/v1/meetings/$meetingCode/lock") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, lockRes.status)
        assertTrue(lockRes.bodyAsText().contains("true"))
        val unlockRes = client.post("/api/v1/meetings/$meetingCode/unlock") { header(HttpHeaders.Authorization, "Bearer $hostToken") }
        assertEquals(HttpStatusCode.OK, unlockRes.status)
        assertTrue(unlockRes.bodyAsText().contains("false"))
    }
}
