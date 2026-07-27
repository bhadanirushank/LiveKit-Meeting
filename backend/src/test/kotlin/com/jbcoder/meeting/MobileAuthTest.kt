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
import org.jetbrains.exposed.sql.transactions.transaction
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
        println("Bootstrap response body: $bodyText")

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
}
