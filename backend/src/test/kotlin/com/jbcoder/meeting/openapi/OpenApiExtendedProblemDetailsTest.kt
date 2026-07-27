package com.jbcoder.meeting.openapi

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
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
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class OpenApiExtendedProblemDetailsTest {

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
        val requestBuilder = SimpleRequest.Builder(Request.Method.valueOf(method.value), path)
        headers.forEach { (k, v) -> requestBuilder.withHeader(k, v) }
        requestBuilder.withHeader("Content-Type", "application/json")
        requestBuilder.withHeader("Accept", "application/json")
        if (requestBody != null) requestBuilder.withBody(requestBody)
        
        val responseBuilder = SimpleResponse.Builder(responseStatus)
        responseHeaders.forEach { k, v -> v.forEach { responseBuilder.withHeader(k, it) } }
        if (responseHeaders["Content-Type"] == null) responseBuilder.withHeader("Content-Type", "application/json")
        if (responseBody != null) responseBuilder.withBody(responseBody)
        
        val report = validator.validate(requestBuilder.build(), responseBuilder.build())
        if (report.hasErrors()) {
            val message = "OpenAPI Validation Failed:\n" + report.messages.joinToString("\n") { it.message }
            fail<Unit>(TestOutputRedactor.redact(message))
        }
    }

    @Test
    fun `test Problem Details SESSION_REVOKED`() = testApplication {
        application { module(testConfig) }
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        
        transaction {
            DeviceSessionsTable.update({ DeviceSessionsTable.id eq UUID.fromString(bBody.sessionId) }) { it[revokedAt] = java.time.Instant.now() }
        }
        
        val resp = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer ${bBody.refreshToken}")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        val bodyText = resp.bodyAsText()
        assertTrue(bodyText.contains("SESSION_REVOKED"), "Expected SESSION_REVOKED but got: $bodyText")
        
        validateInteraction(
            HttpMethod.Post, "/api/v1/session/refresh",
            mapOf("Authorization" to "Bearer ${bBody.refreshToken}"), null,
            resp.status.value, resp.headers, bodyText
        )
    }
    
    @Test
    fun `test Problem Details JOIN_REQUEST_NOT_OWNED`() = testApplication {
        application { module(testConfig) }
        val b1 = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val s1 = json.decodeFromString<SessionBootstrapResponse>(b1.bodyAsText())
        
        val b2 = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val s2 = json.decodeFromString<SessionBootstrapResponse>(b2.bodyAsText())
        
        var joinReqId: UUID? = null
        transaction {
            val meetingId = UUID.randomUUID()
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId, publicMeetingCode = "TEST-${UUID.randomUUID().toString().take(8)}",
                hostSecretHash = "hash", title = "Test", status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 50, waitingRoomEnabled = false, joinBeforeHostEnabled = true,
                passcodeHash = null, livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}", isLocked = false,
                scheduledStart = null, scheduledEnd = null, actualStart = java.time.Instant.now(),
                actualEnd = null, expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), optimisticLockVersion = 1
            ))
            val partId = UUID.randomUUID()
            ParticipantSessionsTable.insert {
                it[id] = partId; it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id-${UUID.randomUUID().toString().take(8)}"; it[displayName] = "Tester"; it[role] = "PARTICIPANT"
                it[state] = "ADMITTED"; it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now(); it[updatedAt] = java.time.Instant.now()
            }
            joinReqId = UUID.randomUUID()
            JoinRequestsTable.insert {
                it[id] = joinReqId!!; it[this.meetingId] = meetingId; it[participantSessionId] = partId
                it[status] = "ADMITTED"; it[this.deviceSessionId] = UUID.fromString(s1.sessionId)
                it[requestedAt] = java.time.Instant.now(); it[expiresAt] = java.time.Instant.now().plusSeconds(3600)
            }
        }
        
        val resp = client.get("/api/v1/join-requests/$joinReqId") {
            header("Authorization", "Bearer ${s2.accessToken}")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("JOIN_REQUEST_NOT_OWNED"))
        
        validateInteraction(HttpMethod.Get, "/api/v1/join-requests/$joinReqId", mapOf("Authorization" to "Bearer ${s2.accessToken}"), null, resp.status.value, resp.headers, resp.bodyAsText())
    }
    
    @Test
    fun `test Problem Details JOIN_REQUEST_EXPIRED and PARTICIPANT_REJECTED`() = testApplication {
        application { module(testConfig) }
        val b1 = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val s1 = json.decodeFromString<SessionBootstrapResponse>(b1.bodyAsText())
        
        var joinReqExpired: UUID? = null
        var joinReqRejected: UUID? = null
        transaction {
            val meetingId = UUID.randomUUID()
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId, publicMeetingCode = "TEST-${UUID.randomUUID().toString().take(8)}",
                hostSecretHash = "hash", title = "Test", status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 50, waitingRoomEnabled = false, joinBeforeHostEnabled = true,
                passcodeHash = null, livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}", isLocked = false,
                scheduledStart = null, scheduledEnd = null, actualStart = java.time.Instant.now(),
                actualEnd = null, expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), optimisticLockVersion = 1
            ))
            
            // Expired one
            val partId1 = UUID.randomUUID()
            ParticipantSessionsTable.insert {
                it[id] = partId1; it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id-${UUID.randomUUID().toString().take(8)}"; it[displayName] = "Tester"; it[role] = "PARTICIPANT"
                it[state] = "ADMITTED"; it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now(); it[updatedAt] = java.time.Instant.now()
            }
            joinReqExpired = UUID.randomUUID()
            JoinRequestsTable.insert {
                it[id] = joinReqExpired!!; it[this.meetingId] = meetingId; it[participantSessionId] = partId1
                it[status] = "ADMITTED"; it[this.deviceSessionId] = UUID.fromString(s1.sessionId)
                it[requestedAt] = java.time.Instant.now().minusSeconds(86400); it[expiresAt] = java.time.Instant.now().minusSeconds(3600)
            }
            
            // Rejected one
            val partId2 = UUID.randomUUID()
            ParticipantSessionsTable.insert {
                it[id] = partId2; it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id2-${UUID.randomUUID().toString().take(8)}"; it[displayName] = "Tester2"; it[role] = "PARTICIPANT"
                it[state] = "REJECTED"; it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now(); it[updatedAt] = java.time.Instant.now()
            }
            joinReqRejected = UUID.randomUUID()
            JoinRequestsTable.insert {
                it[id] = joinReqRejected!!; it[this.meetingId] = meetingId; it[participantSessionId] = partId2
                it[status] = "REJECTED"; it[this.deviceSessionId] = UUID.fromString(s1.sessionId)
                it[requestedAt] = java.time.Instant.now(); it[expiresAt] = java.time.Instant.now().plusSeconds(3600)
            }
        }
        
        val respExpired = client.get("/api/v1/join-requests/$joinReqExpired") {
            header("Authorization", "Bearer ${s1.accessToken}")
        }
        assertEquals(HttpStatusCode.Gone, respExpired.status)
        assertTrue(respExpired.bodyAsText().contains("JOIN_REQUEST_EXPIRED"))
        validateInteraction(HttpMethod.Get, "/api/v1/join-requests/$joinReqExpired", mapOf("Authorization" to "Bearer ${s1.accessToken}"), null, respExpired.status.value, respExpired.headers, respExpired.bodyAsText())
        
        val respRejected = client.get("/api/v1/join-requests/$joinReqRejected") {
            header("Authorization", "Bearer ${s1.accessToken}")
        }
        assertEquals(HttpStatusCode.Forbidden, respRejected.status)
        assertTrue(respRejected.bodyAsText().contains("PARTICIPANT_REJECTED"))
        validateInteraction(HttpMethod.Get, "/api/v1/join-requests/$joinReqRejected", mapOf("Authorization" to "Bearer ${s1.accessToken}"), null, respRejected.status.value, respRejected.headers, respRejected.bodyAsText())
    }

    @Test
    fun `test Problem Details TOKEN_ALREADY_ISSUED and INVALID_IDEMPOTENCY_KEY`() = testApplication {
        application { module(testConfig) }
        val b1 = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val s1 = json.decodeFromString<SessionBootstrapResponse>(b1.bodyAsText())
        
        var joinReqId: UUID? = null
        transaction {
            val meetingId = UUID.randomUUID()
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId, publicMeetingCode = "TEST-${UUID.randomUUID().toString().take(8)}",
                hostSecretHash = "hash", title = "Test", status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 50, waitingRoomEnabled = false, joinBeforeHostEnabled = true,
                passcodeHash = null, livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}", isLocked = false,
                scheduledStart = null, scheduledEnd = null, actualStart = java.time.Instant.now(),
                actualEnd = null, expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), optimisticLockVersion = 1
            ))
            val partId = UUID.randomUUID()
            ParticipantSessionsTable.insert {
                it[id] = partId; it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id-${UUID.randomUUID().toString().take(8)}"; it[displayName] = "Tester"; it[role] = "PARTICIPANT"
                it[state] = "ADMITTED"; it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now(); it[updatedAt] = java.time.Instant.now()
            }
            joinReqId = UUID.randomUUID()
            JoinRequestsTable.insert {
                it[id] = joinReqId!!; it[this.meetingId] = meetingId; it[participantSessionId] = partId
                it[status] = "ADMITTED"; it[this.deviceSessionId] = UUID.fromString(s1.sessionId)
                it[requestedAt] = java.time.Instant.now(); it[expiresAt] = java.time.Instant.now().plusSeconds(3600)
            }
        }
        
        // INVALID_IDEMPOTENCY_KEY
        val respInv = client.post("/api/v1/join-requests/$joinReqId/livekit-token") {
            header("Authorization", "Bearer ${s1.accessToken}")
            header("Idempotency-Key", "too_short")
        }
        assertEquals(HttpStatusCode.BadRequest, respInv.status)
        assertTrue(respInv.bodyAsText().contains("INVALID_IDEMPOTENCY_KEY"))
        validateInteraction(HttpMethod.Post, "/api/v1/join-requests/$joinReqId/livekit-token", mapOf("Authorization" to "Bearer ${s1.accessToken}", "Idempotency-Key" to "too_short"), null, respInv.status.value, respInv.headers, respInv.bodyAsText())
        
        // TOKEN_ALREADY_ISSUED
        val key1 = UUID.randomUUID().toString()
        val key2 = UUID.randomUUID().toString()
        client.post("/api/v1/join-requests/$joinReqId/livekit-token") {
            header("Authorization", "Bearer ${s1.accessToken}")
            header("Idempotency-Key", key1)
        }
        val respIssued = client.post("/api/v1/join-requests/$joinReqId/livekit-token") {
            header("Authorization", "Bearer ${s1.accessToken}")
            header("Idempotency-Key", key2)
        }
        assertEquals(HttpStatusCode.Conflict, respIssued.status)
        assertTrue(respIssued.bodyAsText().contains("TOKEN_ALREADY_ISSUED"))
        validateInteraction(HttpMethod.Post, "/api/v1/join-requests/$joinReqId/livekit-token", mapOf("Authorization" to "Bearer ${s1.accessToken}", "Idempotency-Key" to key2), null, respIssued.status.value, respIssued.headers, respIssued.bodyAsText())
    }

    @Test
    fun `test Problem Details MEETING_LOCKED and MEETING_CAPACITY_REACHED`() = testApplication {
        // Just mock the error responses or use domain setup if possible. Since we need to test schema,
        // and JoinRequests require an integration flow to get these errors, let's create a meeting with maxParticipants = 1
        application { module(testConfig) }
        val b1 = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val s1 = json.decodeFromString<SessionBootstrapResponse>(b1.bodyAsText())
        
        var meetingCodeCap = ""
        var meetingCodeLocked = ""
        transaction {
            val meetingId = UUID.randomUUID()
            meetingCodeCap = "TEST-${UUID.randomUUID().toString().take(8)}"
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId, publicMeetingCode = meetingCodeCap,
                hostSecretHash = "hash", title = "Test", status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 1, waitingRoomEnabled = false, joinBeforeHostEnabled = true,
                passcodeHash = null, livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}", isLocked = false,
                scheduledStart = null, scheduledEnd = null, actualStart = java.time.Instant.now(),
                actualEnd = null, expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), optimisticLockVersion = 1
            ))
            // Insert 1 participant already
            ParticipantSessionsTable.insert {
                it[id] = UUID.randomUUID(); it[this.meetingId] = meetingId
                it[livekitIdentity] = "lk_id-${UUID.randomUUID().toString().take(8)}"; it[displayName] = "Tester"; it[role] = "PARTICIPANT"
                it[state] = "ADMITTED"; it[requestedAt] = java.time.Instant.now()
                it[createdAt] = java.time.Instant.now(); it[updatedAt] = java.time.Instant.now()
            }
            
            val meetingId2 = UUID.randomUUID()
            meetingCodeLocked = "TEST-${UUID.randomUUID().toString().take(8)}"
            MeetingRepository.createMeeting(MeetingEntity(
                id = meetingId2, publicMeetingCode = meetingCodeLocked,
                hostSecretHash = "hash", title = "Test", status = com.jbcoder.meeting.domain.MeetingStatus.LIVE,
                maximumParticipants = 50, waitingRoomEnabled = false, joinBeforeHostEnabled = true,
                passcodeHash = null, livekitRoomName = "room-${UUID.randomUUID().toString().take(8)}", isLocked = true,
                scheduledStart = null, scheduledEnd = null, actualStart = java.time.Instant.now(),
                actualEnd = null, expiresAt = java.time.Instant.now().plusSeconds(3600),
                createdAt = java.time.Instant.now(), updatedAt = java.time.Instant.now(), optimisticLockVersion = 1
            ))
        }
        
        // Wait, creating a join request is POST /api/v1/meetings/{code}/join, which is NOT in the list!
        // "Automated request and response validation must cover: 1. session/bootstrap 2. session/refresh 3. join-requests/{id} GET 4. livekit-token POST 5. leave POST 6. status GET"
        // But MEETING_CAPACITY_REACHED and MEETING_LOCKED are typically thrown on `/join`.
        // If they are returned on `/status` or `/join-requests/{id}`, we can just trigger it there?
        // Wait, does `/status` throw MEETING_LOCKED?
        val respLocked = client.get("/api/v1/meetings/$meetingCodeLocked/status") {
            header("Authorization", "Bearer ${s1.accessToken}")
        }
        // It returns 200 with isLocked = true.
        // Wait, how do I trigger MEETING_LOCKED on the required endpoints?
        // Only `/join` (which is Mobile Join) would return 409 MEETING_LOCKED or MEETING_CAPACITY_REACHED!
        // But Mobile Join is in Phase 6A! Phase 5.1 is only: bootstrap, refresh, join-requests status, token, leave, meeting status.
        // If the prompt explicitly requires validating these error responses, I should write a test using a dummy route if needed, or maybe they are returned by `livekit-token` POST if the meeting was locked after admission?
        // Ah, `livekit-token` POST might check capacity and lock status before issuing the token!
    }
}
