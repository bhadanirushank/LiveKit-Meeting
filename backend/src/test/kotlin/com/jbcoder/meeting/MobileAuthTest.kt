package com.jbcoder.meeting

import com.jbcoder.meeting.api.SessionBootstrapRequest
import com.jbcoder.meeting.domain.SessionBootstrapResponse
import com.jbcoder.meeting.domain.SessionRefreshResponse
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.persistence.DeviceSessionsTable
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID

class MobileAuthTest {
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
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `test anonymous bootstrap generates valid opaque credentials`() = testApplication {
        application { module(testConfig) }
        
        val requestPayload = SessionBootstrapRequest(
            installationId = UUID.randomUUID().toString(),
            platform = "ANDROID",
            appVersion = "1.0.0"
        )

        val response = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestPayload))
        }

        val bodyText = response.bodyAsText()
        println("Bootstrap response status: ${response.status}")
        println("Bootstrap response body: ${com.jbcoder.meeting.security.TestOutputRedactor.redact(bodyText)}")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<SessionBootstrapResponse>(bodyText)
        
        assertNotNull(body.sessionId)
        assertTrue(body.accessToken.startsWith("atk_"))
        assertTrue(body.refreshToken.startsWith("rtk_"))
        assertEquals(47, body.accessToken.length) // atk_ (4) + 43 base64 chars for 32 bytes
        assertEquals(47, body.refreshToken.length)

        transaction {
            val sessions = DeviceSessionsTable.selectAll().toList()
            assertTrue(sessions.isNotEmpty())
        }
    }

    @Test
    fun `test session refresh atomically rotates credentials and prevents replay`() = testApplication {
        application { module(AppConfig.load()) }
        
        val requestPayload = SessionBootstrapRequest(
            installationId = UUID.randomUUID().toString(),
            platform = "ANDROID"
        )

        val bootstrapResponse = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestPayload))
        }
        
        val bootstrapBodyText = bootstrapResponse.bodyAsText()
        val bootstrapBody = json.decodeFromString<SessionBootstrapResponse>(bootstrapBodyText)

        val oldAccessToken = bootstrapBody.accessToken
        val oldRefreshToken = bootstrapBody.refreshToken

        // Refresh request
        val refreshResponse = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer $oldRefreshToken")
        }

        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val refreshedBodyText = refreshResponse.bodyAsText()
        val refreshedBody = json.decodeFromString<SessionRefreshResponse>(refreshedBodyText)
        
        assertNotEquals(oldAccessToken, refreshedBody.accessToken)
        assertNotEquals(oldRefreshToken, refreshedBody.refreshToken)
        assertTrue(refreshedBody.accessToken.startsWith("atk_"))
        assertTrue(refreshedBody.refreshToken.startsWith("rtk_"))

        // Replay attempt should fail
        val replayResponse = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer $oldRefreshToken")
        }

        assertEquals(HttpStatusCode.Unauthorized, replayResponse.status)
    }

    @Test
    fun `test token delivery race conditions`() = testApplication {
        application { module(testConfig) }
        
        val requestPayload = SessionBootstrapRequest(
            installationId = UUID.randomUUID().toString(),
            platform = "ANDROID"
        )
        val bootstrapResponse = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestPayload))
        }
        val bootstrapBody = json.decodeFromString<SessionBootstrapResponse>(bootstrapResponse.bodyAsText())
        val accessToken = bootstrapBody.accessToken

        var joinReqId: UUID? = null
        var deviceSessionId = bootstrapBody.sessionId
        
        transaction {
            val meetingId = UUID.randomUUID()
            com.jbcoder.meeting.persistence.MeetingRepository.createMeeting(com.jbcoder.meeting.persistence.MeetingEntity(
                id = meetingId,
                publicMeetingCode = "TESTCODE-${UUID.randomUUID().toString().take(8)}",
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
            com.jbcoder.meeting.persistence.ParticipantSessionsTable.insert {
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
            
            val reqId = UUID.randomUUID()
            joinReqId = reqId
            com.jbcoder.meeting.persistence.JoinRequestsTable.insert {
                it[id] = reqId
                it[this.meetingId] = meetingId
                it[participantSessionId] = partId
                it[status] = "ADMITTED"
                it[this.deviceSessionId] = UUID.fromString(deviceSessionId)
                it[requestedAt] = java.time.Instant.now()
                it[expiresAt] = java.time.Instant.now().plusSeconds(3600)
            }
        }

        val idempotencyKey = UUID.randomUUID().toString()

        // Dispatch multiple concurrent requests
        val concurrency = 5
        val results = runBlocking {
            val deferreds = (1..concurrency).map {
                async(Dispatchers.Default) {
                    client.post("/api/v1/join-requests/$joinReqId/livekit-token") {
                        header("Authorization", "Bearer $accessToken")
                        header("Idempotency-Key", idempotencyKey)
                    }
                }
            }
            deferreds.map { it.await() }
        }
        
        // Assert exactly one OK (200) or all OK with same token? 
        // With IdempotencyKey, the first creates it, the subsequent read it and return OK!
        // The token should be identical in all 200 responses.
        var successCount = 0
        var tokens = mutableSetOf<String>()
        results.forEach { res ->
            if (res.status == HttpStatusCode.OK) {
                successCount++
                val bodyText = res.bodyAsText()
                if (bodyText.contains("token")) {
                    tokens.add(bodyText)
                }
            }
        }
        
        // Due to concurrency, some might hit 409 Conflict if idempotency lock isn't fully robust, 
        // but with our postgres row locking, they should either block and then return the same token, 
        // or return 200.
        assertTrue(successCount > 0, "At least one request must succeed")
        assertTrue(tokens.size == 1, "All successful requests must return the exact same token payload")
    }
}
