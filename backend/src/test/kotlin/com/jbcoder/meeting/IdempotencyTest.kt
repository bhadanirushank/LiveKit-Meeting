package com.jbcoder.meeting

import com.jbcoder.meeting.api.CreateMeetingRequest
import com.jbcoder.meeting.api.CreateMeetingResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

import com.jbcoder.meeting.module
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import io.ktor.client.plugins.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.AfterAll

class IdempotencyTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            val config = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            DatabaseConfig.init(config)
            RedisConfig.init(config)
        }
        
        @JvmStatic
        @AfterAll
        fun teardown() {
            RedisConfig.close()
            DatabaseConfig.close()
        }
    }

    @Test
    fun `test idempotency prevents duplicate meeting creation and handles concurrent requests`() = runBlocking {
        testApplication {
            application {
                module(run { TestSecrets.setupTestProperties(); AppConfig.load() })
            }
            val client = createClient { }
            
            val idempotencyKey = UUID.randomUUID().toString()
            val requestBody = """{"title":"Idempotent Test","passcode":"1234","waitingRoomEnabled":false,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"$idempotencyKey"}"""
            
            // Send 3 concurrent requests with the same idempotency key
            val responses = (1..3).map {
                async {
                    client.post("/api/v1/meetings") {
                        contentType(ContentType.Application.Json)
                        header("Idempotency-Key", idempotencyKey)
                        header("X-Installation-Id", "test-actor")
                        setBody(requestBody)
                    }
                }
            }.awaitAll()
            
            // Exactly one should be 201 Created
            val successResponses = responses.filter { it.status == HttpStatusCode.Created }
            assertEquals(1, successResponses.size, "Exactly one request should succeed")
            
            // The others should be 409 Conflict (CONCURRENT_REQUEST_PROCESSING)
            val conflictResponses = responses.filter { it.status == HttpStatusCode.Conflict }
            assertEquals(2, conflictResponses.size, "The concurrent requests should fail with 409")
            
            // Now, send a follow up request with the exact same key. It should return the cached 201 response.
            val followupResponse = client.post("/api/v1/meetings") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", idempotencyKey)
                header("X-Installation-Id", "test-actor")
                setBody(requestBody)
            }
            
            assertEquals(HttpStatusCode.Created, followupResponse.status)
            
            val initialBody = successResponses.first().bodyAsText()
            val followupBody = followupResponse.bodyAsText()
            assertEquals(initialBody, followupBody, "Follow up response body must exactly match cached response")
            
            // Now send with the same key but different body -> IDEMPOTENCY_KEY_REUSED
            val diffBodyResponse = client.post("/api/v1/meetings") {
                contentType(ContentType.Application.Json)
                header("Idempotency-Key", idempotencyKey)
                header("X-Installation-Id", "test-actor")
                setBody(requestBody.replace("Idempotent Test", "Changed Title"))
            }
            
            assertEquals(HttpStatusCode.Conflict, diffBodyResponse.status)
            org.junit.jupiter.api.Assertions.assertTrue(diffBodyResponse.bodyAsText().contains("IDEMPOTENCY_KEY_REUSED"))
        }
    }
}
