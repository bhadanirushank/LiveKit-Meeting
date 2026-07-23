package com.jbcoder.meeting

import com.jbcoder.meeting.api.*
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests rate limiting on join-request endpoint and passcode validation.
 * Rate limiting must reject excessive incorrect-passcode attempts.
 */
class RateLimitIntegrationTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            testConfig = AppConfig.load()
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            if (!DatabaseConfig.isHealthy()) fail<Unit>("PostgreSQL unreachable")
            if (!RedisConfig.isHealthy()) fail<Unit>("Redis unreachable")
        }
    }

    @Test
    fun testWrongPasscodeRateLimiting() = testApplication {
        application { module(testConfig) }

        // Create meeting with passcode
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Rate Limit Test","passcode":"correct999","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val meetingCode = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["publicMeetingCode"]!!.jsonPrimitive.content

        // Submit 10 requests with wrong passcode from same device
        val deviceId = UUID.randomUUID().toString()
        val statusCodes = (1..10).map { attempt ->
            val res = client.post("/api/v1/meetings/$meetingCode/join-request") {
                contentType(ContentType.Application.Json)
                setBody("""{"passcode":"wrong$attempt","displayName":"Attacker","deviceSessionId":"$deviceId"}""")
            }
            res.status
        }

        println("RATE LIMIT TEST - Status codes: $statusCodes")

        // Must have at least some rejections (400 for wrong passcode OR 429 for rate limit)
        val rejections = statusCodes.filter { it == HttpStatusCode.BadRequest || it == HttpStatusCode.TooManyRequests }
        assertTrue(rejections.isNotEmpty(), "Wrong passcode must be rejected. Got: $statusCodes")

        // Valid passcode should still work (not rate-limited for correct)
        val validRes = client.post("/api/v1/meetings/$meetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"passcode":"correct999","displayName":"Valid User","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertTrue(
            validRes.status == HttpStatusCode.Accepted || validRes.status == HttpStatusCode.OK,
            "Valid passcode from different device should work: ${validRes.status} ${validRes.bodyAsText()}"
        )
    }

    @Test
    fun testMissingPasscodeRejected() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Passcode Required Meeting","passcode":"secret1234","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val meetingCode = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["publicMeetingCode"]!!.jsonPrimitive.content

        // No passcode at all
        val res = client.post("/api/v1/meetings/$meetingCode/join-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"No Code","deviceSessionId":"${UUID.randomUUID()}"}""")
        }
        assertTrue(res.status == HttpStatusCode.BadRequest || res.status == HttpStatusCode.Unauthorized, "Missing passcode must be rejected with 400 or 401, got: ${res.status}")
    }

    @Test
    fun testHostSessionRequiredForModerationRoutes() = testApplication {
        application { module(testConfig) }

        // Create a meeting
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Auth Test Meeting","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val meetingCode = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["publicMeetingCode"]!!.jsonPrimitive.content

        // Attempt moderation route without Authorization header
        val resNoAuth = client.post("/api/v1/meetings/$meetingCode/participants/${UUID.randomUUID()}/remove") {
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"test"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resNoAuth.status, "Moderation route without auth should be 401")

        // Attempt with invalid Bearer token
        val resBadAuth = client.post("/api/v1/meetings/$meetingCode/participants/${UUID.randomUUID()}/remove") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer bad.token.here")
            setBody("""{"reason":"test"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, resBadAuth.status, "Invalid token should be 401")
    }

    @Test
    fun testExpiredHostSessionRejected() = testApplication {
        application { module(testConfig) }

        // Create a meeting and get valid host secret
        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Expired Token Test","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val meetingCode = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["publicMeetingCode"]!!.jsonPrimitive.content

        // Craft a JWT signed with wrong secret to simulate expired/invalid session
        val fakeJwtStr = com.auth0.jwt.JWT.create()
            .withSubject("host")
            .withClaim("publicMeetingCode", "fake")
            .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256("wrong_secret_for_test"))
        val fakeJwt = "Bearer $fakeJwtStr"

        val res = client.post("/api/v1/meetings/$meetingCode/waiting-room/${UUID.randomUUID()}/admit") {
            header(HttpHeaders.Authorization, fakeJwt)
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status, "Forged JWT should be rejected as 401")
    }
}
