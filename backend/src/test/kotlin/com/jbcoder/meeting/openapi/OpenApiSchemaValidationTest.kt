package com.jbcoder.meeting.openapi

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.Response
import com.atlassian.oai.validator.model.SimpleRequest
import com.atlassian.oai.validator.model.SimpleResponse
import com.jbcoder.meeting.TestSecrets
import com.jbcoder.meeting.module
import com.jbcoder.meeting.api.SessionBootstrapRequest
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import com.jbcoder.meeting.domain.SessionBootstrapResponse
import com.jbcoder.meeting.persistence.DeviceSessionsTable
import com.jbcoder.meeting.persistence.JoinRequestsTable
import com.jbcoder.meeting.persistence.MeetingEntity
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import com.jbcoder.meeting.security.TestOutputRedactor
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class OpenApiSchemaValidationTest {

    companion object {
        private lateinit var testConfig: AppConfig
        private lateinit var validator: OpenApiInteractionValidator

        @JvmStatic
        @BeforeAll
        fun setup() {
            TestSecrets.setupTestProperties()
            testConfig = AppConfig.load()
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            
            TestOutputRedactor.setEncryptionKey(testConfig.tokenDeliveryEncryptionKeyB64)

            val openApiFile = File("../docs/openapi.yaml")
            assertTrue(openApiFile.exists(), "OpenAPI spec file must exist")
            
            validator = OpenApiInteractionValidator
                .createFor(openApiFile.absolutePath)
                .build()
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            DatabaseConfig.close()
            RedisConfig.close()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun validateInteraction(
        method: HttpMethod,
        path: String,
        headers: Map<String, String>,
        requestBody: String?,
        responseStatus: Int,
        responseHeaders: io.ktor.http.Headers,
        responseBody: String?
    ) {
        val requestBuilder = SimpleRequest.Builder(
            Request.Method.valueOf(method.value),
            path
        )
        
        headers.forEach { (k, v) -> requestBuilder.withHeader(k, v) }
        requestBuilder.withHeader("Content-Type", "application/json")
        requestBuilder.withHeader("Accept", "application/json")
        if (requestBody != null) {
            requestBuilder.withBody(requestBody)
        }
        val request = requestBuilder.build()

        val responseBuilder = SimpleResponse.Builder(responseStatus)
        responseHeaders.forEach { k, v ->
            v.forEach { responseBuilder.withHeader(k, it) }
        }
        if (responseHeaders["Content-Type"] == null) {
            responseBuilder.withHeader("Content-Type", "application/json")
        }
        if (responseBody != null) {
            responseBuilder.withBody(responseBody)
        }
        val response = responseBuilder.build()

        val report = validator.validate(request, response)
        if (report.hasErrors()) {
            val message = "OpenAPI Validation Failed:\n" + report.messages.joinToString("\n") { it.message }
            fail<Unit>(TestOutputRedactor.redact(message))
        }
    }
    
    private fun validateRequestOnly(
        method: HttpMethod,
        path: String,
        headers: Map<String, String>,
        requestBody: String?
    ): Boolean {
        val requestBuilder = SimpleRequest.Builder(
            Request.Method.valueOf(method.value),
            path
        )
        headers.forEach { (k, v) -> requestBuilder.withHeader(k, v) }
        requestBuilder.withHeader("Content-Type", "application/json")
        requestBuilder.withHeader("Accept", "application/json")
        if (requestBody != null) {
            requestBuilder.withBody(requestBody)
        }
        val request = requestBuilder.build()
        val report = validator.validateRequest(request)
        return !report.hasErrors()
    }

    private fun validateResponseOnly(
        method: HttpMethod,
        path: String,
        responseStatus: Int,
        responseHeaders: io.ktor.http.Headers,
        responseBody: String?
    ) {
        val requestBuilder = SimpleRequest.Builder(
            Request.Method.valueOf(method.value),
            path
        )
        val request = requestBuilder.build()

        val responseBuilder = SimpleResponse.Builder(responseStatus)
        responseHeaders.forEach { k, v ->
            v.forEach { responseBuilder.withHeader(k, it) }
        }
        if (responseHeaders["Content-Type"] == null) {
            responseBuilder.withHeader("Content-Type", "application/json")
        }
        if (responseBody != null) {
            responseBuilder.withBody(responseBody)
        }
        val response = responseBuilder.build()

        val report = validator.validateResponse(path, Request.Method.valueOf(method.value), response)
        if (report.hasErrors()) {
            val message = "OpenAPI Response Validation Failed:\n" + report.messages.joinToString("\n") { it.message }
            fail<Unit>(TestOutputRedactor.redact(message))
        }
    }

    @Test
    fun `test Session Bootstrap Schema`() = testApplication {
        application { module(testConfig) }
        
        val payload = SessionBootstrapRequest(
            installationId = UUID.randomUUID().toString(),
            platform = "ANDROID",
            appVersion = "1.0.0"
        )
        val bodyStr = json.encodeToString(payload)
        
        val response = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(bodyStr)
        }
        
        val respBody = response.bodyAsText()
        validateInteraction(
            HttpMethod.Post,
            "/api/v1/session/bootstrap",
            mapOf(),
            bodyStr,
            response.status.value,
            response.headers,
            respBody
        )
        
        // Ensure credential fields don't leak raw encryption keys or DB stuff
        assertFalse(respBody.contains("iv\""))
        assertFalse(respBody.contains("encryptedToken\""))
        
        // Invalid Request Test (missing platform)
        val invalidPayload = "{\"installationId\":\"${UUID.randomUUID()}\"}"
        assertFalse(validateRequestOnly(HttpMethod.Post, "/api/v1/session/bootstrap", mapOf(), invalidPayload), "Validator should reject missing required field")
        
        val badResp = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(invalidPayload)
        }
        assertEquals(HttpStatusCode.BadRequest, badResp.status)
        validateResponseOnly(
            HttpMethod.Post,
            "/api/v1/session/bootstrap",
            badResp.status.value,
            badResp.headers,
            badResp.bodyAsText()
        )
    }

    @Test
    fun `test Session Refresh Schema`() = testApplication {
        application { module(testConfig) }
        
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        
        val response = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer ${bBody.refreshToken}")
        }
        
        validateInteraction(
            HttpMethod.Post,
            "/api/v1/session/refresh",
            mapOf("Authorization" to "Bearer ${bBody.refreshToken}"),
            null,
            response.status.value,
            response.headers,
            response.bodyAsText()
        )
        
        // Invalid refresh (revoked or bad token)
        val badResp = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer rtk_badtoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, badResp.status)
        assertTrue(badResp.bodyAsText().contains("INVALID_REFRESH_TOKEN"), "Expected INVALID_AUTHORIZATION_FORMAT but got: ${badResp.bodyAsText()}")
        
        validateResponseOnly(
            HttpMethod.Post,
            "/api/v1/session/refresh",
            badResp.status.value,
            badResp.headers,
            badResp.bodyAsText()
        )
    }

    @Test
    fun `test Join Request Status and LiveKit Token Delivery Schemas`() = testApplication {
        application { module(testConfig) }
        
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        
        var joinReqId: UUID? = null
        var meetingCode = ""
        transaction {
            val meetingId = UUID.randomUUID()
            meetingCode = "TEST-${UUID.randomUUID().toString().take(8)}"
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId,
                publicMeetingCode = meetingCode,
                hostSecretHash = "hash",
                title = "Test",
                status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 50,
                waitingRoomEnabled = false,
                joinBeforeHostEnabled = true,
                passcodeHash = null,
                livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}",
                isLocked = false,
                scheduledStart = null,
                scheduledEnd = null,
                actualStart = java.time.Instant.now(),
                actualEnd = null,
                expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                optimisticLockVersion = 1
            ))
            
            val partId = UUID.randomUUID()
            ParticipantSessionsTable.insert {
                it[id] = partId
                it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id-${UUID.randomUUID().toString().take(8)}"
                it[displayName] = "Tester"
                it[role] = "PARTICIPANT"
                it[state] = "ADMITTED"
                it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now()
                it[updatedAt] = java.time.Instant.now()
            }
            
            joinReqId = UUID.randomUUID()
            JoinRequestsTable.insert {
                it[id] = joinReqId!!
                it[this.meetingId] = meetingId
                it[participantSessionId] = partId
                it[status] = "ADMITTED"
                it[this.deviceSessionId] = UUID.fromString(bBody.sessionId)
                it[requestedAt] = java.time.Instant.now()
                it[expiresAt] = java.time.Instant.now().plusSeconds(3600)
            }
        }
        
        // 1. Join Request Status GET
        val statusResp = client.get("/api/v1/join-requests/$joinReqId") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, statusResp.status, "Join request status GET failed with ${statusResp.status}: ${statusResp.bodyAsText()}")
        validateInteraction(
            HttpMethod.Get,
            "/api/v1/join-requests/$joinReqId",
            mapOf("Authorization" to "Bearer ${bBody.accessToken}"),
            null,
            statusResp.status.value,
            statusResp.headers,
            statusResp.bodyAsText()
        )
        
        // 2. Token Delivery POST
        val idempotencyKey = UUID.randomUUID().toString()
        val tokenResp = client.post("/api/v1/join-requests/$joinReqId/livekit-token") {
            header("Authorization", "Bearer ${bBody.accessToken}")
            header("Idempotency-Key", idempotencyKey)
        }
        assertEquals(HttpStatusCode.OK, tokenResp.status, "Token Delivery POST failed with ${tokenResp.status}: ${tokenResp.bodyAsText()}")
        validateInteraction(
            HttpMethod.Post,
            "/api/v1/join-requests/$joinReqId/livekit-token",
            mapOf("Authorization" to "Bearer ${bBody.accessToken}", "Idempotency-Key" to idempotencyKey),
            null,
            tokenResp.status.value,
            tokenResp.headers,
            tokenResp.bodyAsText()
        )
        val tokenRespBody = tokenResp.bodyAsText()
        assertTrue(tokenRespBody.contains("token"))
        
        
        // 3. Meeting Leave POST
        val leaveResp = client.post("/api/v1/meetings/$meetingCode/leave") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        // Could be 204 No Content
        assertEquals(HttpStatusCode.NoContent, leaveResp.status, "Meeting Leave POST failed with ${leaveResp.status}: ${leaveResp.bodyAsText()}")
        validateInteraction(
            HttpMethod.Post,
            "/api/v1/meetings/$meetingCode/leave",
            mapOf("Authorization" to "Bearer ${bBody.accessToken}"),
            null,
            leaveResp.status.value,
            leaveResp.headers,
            leaveResp.bodyAsText().takeIf { it.isNotEmpty() }
        )
        
        // 4. Meeting Status GET
        val mStatusResp = client.get("/api/v1/meetings/$meetingCode/status") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, mStatusResp.status, "Meeting Status GET failed with ${mStatusResp.status}: ${mStatusResp.bodyAsText()}")
        validateInteraction(
            HttpMethod.Get,
            "/api/v1/meetings/$meetingCode/status",
            mapOf("Authorization" to "Bearer ${bBody.accessToken}"),
            null,
            mStatusResp.status.value,
            mStatusResp.headers,
            mStatusResp.bodyAsText()
        )
    }
}
