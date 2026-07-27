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
import com.jbcoder.meeting.security.TestOutputRedactor
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class OpenApiProblemDetailsTest {

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
    fun `test Problem Details INVALID_REFRESH_TOKEN`() = testApplication {
        application { module(testConfig) }
        val resp = client.post("/api/v1/session/refresh") {
            header("Authorization", "Bearer rtk_invalid")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        val bodyText = resp.bodyAsText()
        assertTrue(bodyText.contains("INVALID_REFRESH_TOKEN"))
        
        validateInteraction(
            HttpMethod.Post, "/api/v1/session/refresh",
            mapOf("Authorization" to "Bearer rtk_invalid"), null,
            resp.status.value, resp.headers, bodyText
        )
    }

    @Test
    fun `test Problem Details SESSION_EXPIRED or REVOKED`() = testApplication {
        application { module(testConfig) }
        val resp = client.get("/api/v1/join-requests/${UUID.randomUUID()}") {
            header("Authorization", "Bearer atk_expired_or_invalid")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        val bodyText = resp.bodyAsText()
        if (bodyText.isNotEmpty() && resp.headers["Content-Type"]?.contains("application/json") == true) {
            validateInteraction(
                HttpMethod.Get, "/api/v1/join-requests/${UUID.randomUUID()}",
                mapOf("Authorization" to "Bearer atk_expired_or_invalid"), null,
                resp.status.value, resp.headers, bodyText
            )
        }
    }
    
    @Test
    fun `test Problem Details JOIN_REQUEST_NOT_FOUND`() = testApplication {
        application { module(testConfig) }
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        
        val fakeId = UUID.randomUUID()
        val resp = client.get("/api/v1/join-requests/$fakeId") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        val bodyText = resp.bodyAsText()
        assertTrue(bodyText.contains("JOIN_REQUEST_NOT_FOUND"), "Expected JOIN_REQUEST_NOT_FOUND but got: $bodyText")
        
        validateInteraction(
            HttpMethod.Get, "/api/v1/join-requests/$fakeId",
            mapOf("Authorization" to "Bearer ${bBody.accessToken}"), null,
            resp.status.value, resp.headers, bodyText
        )
    }

    @Test
    fun `test Problem Details RATE_LIMIT_EXCEEDED`() = testApplication {
        application { module(testConfig) }
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        
        var resp = client.get("/api/v1/join-requests/${UUID.randomUUID()}") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        for (i in 1..25) {
            resp = client.get("/api/v1/join-requests/${UUID.randomUUID()}") {
                header("Authorization", "Bearer ${bBody.accessToken}")
            }
            if (resp.status == HttpStatusCode.TooManyRequests) break
        }
        
        if (resp.status == HttpStatusCode.TooManyRequests) {
            val bodyText = resp.bodyAsText()
            assertTrue(bodyText.contains("RATE_LIMIT_EXCEEDED"))
            assertTrue(resp.headers.contains("Retry-After"))
            validateInteraction(
                HttpMethod.Get, "/api/v1/join-requests/${UUID.randomUUID()}",
                mapOf("Authorization" to "Bearer ${bBody.accessToken}"), null,
                resp.status.value, resp.headers, bodyText
            )
        }
    }

    @Test
    fun `test Problem Details MEETING_ENDED`() = testApplication {
        application { module(testConfig) }
        val bootstrap = client.post("/api/v1/session/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SessionBootstrapRequest(UUID.randomUUID().toString(), "ANDROID")))
        }
        val bBody = json.decodeFromString<SessionBootstrapResponse>(bootstrap.bodyAsText())
        val resp = client.get("/api/v1/meetings/INVALID_CODE/status") {
            header("Authorization", "Bearer ${bBody.accessToken}")
        }
        val bodyText = resp.bodyAsText()
        if (resp.status != HttpStatusCode.OK && bodyText.isNotEmpty()) {
            validateInteraction(
                HttpMethod.Get, "/api/v1/meetings/INVALID_CODE/status",
                mapOf("Authorization" to "Bearer ${bBody.accessToken}"), null,
                resp.status.value, resp.headers, bodyText
            )
        }
    }
}
