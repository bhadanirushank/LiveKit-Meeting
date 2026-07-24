package com.jbcoder.meeting

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jbcoder.meeting.domain.LiveKitPermissionMapper
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date
import com.jbcoder.meeting.module
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

class JwtSecurityTest {

    companion object {
        private lateinit var config: AppConfig
        
        @JvmStatic
        @BeforeAll
        fun setup() {
            System.setProperty("POSTGRES_HOST", "127.0.0.1")
            System.setProperty("POSTGRES_PORT", "5432")
            System.setProperty("POSTGRES_DB", "livekit_meeting")
            System.setProperty("POSTGRES_USER", "postgres")
            System.setProperty("POSTGRES_PASSWORD", "postgres_password_placeholder")
            System.setProperty("REDIS_HOST", "127.0.0.1")
            System.setProperty("REDIS_PORT", "6379")
            System.setProperty("REDIS_PASSWORD", "redis_password_placeholder")
            System.setProperty("LIVEKIT_API_KEY", TestSecrets.liveKitApiKey)
            System.setProperty("LIVEKIT_API_SECRET", TestSecrets.liveKitApiSecret)
            System.setProperty("JWT_SECRET", TestSecrets.jwtSecret)
            System.setProperty("JWT_ISSUER", "livekit-meeting-app")
            System.setProperty("JWT_AUDIENCE", "livekit-meeting-app")

            config = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            DatabaseConfig.init(config)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            DatabaseConfig.close()
        }
    }

    @Test
    fun `API rejects expired JWTs`() = testApplication {
        application {
            module(config)
        }
        
        val expiredToken = JWT.create()
            .withIssuer("livekit-meeting-app")
            .withAudience("livekit-meeting-app")
            .withClaim("role", "HOST")
            .withExpiresAt(Date(System.currentTimeMillis() - 10000))
            .sign(Algorithm.HMAC256(TestSecrets.jwtSecret))

        val response = client.post("/api/v1/meetings/somecode/start") {
            bearerAuth(expiredToken)
        }
        
        // It should reject with 401 Unauthorized
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `API rejects JWTs with incorrect issuer`() = testApplication {
        application {
            module(config)
        }
        
        val invalidIssuerToken = JWT.create()
            .withIssuer("evil-issuer")
            .withAudience("livekit-meeting-app")
            .withClaim("role", "HOST")
            .withExpiresAt(Date(System.currentTimeMillis() + 10000))
            .sign(Algorithm.HMAC256(TestSecrets.jwtSecret))

        val response = client.post("/api/v1/meetings/somecode/start") {
            bearerAuth(invalidIssuerToken)
        }
        
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `API rejects None algorithm`() = testApplication {
        application {
            module(config)
        }
        
        // Simulating a token generated with Algorithm.none() by manually creating the base64 parts
        // Header: {"alg":"none","typ":"JWT"} -> eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0
        // Payload: {"iss":"livekit-meeting-app","aud":"livekit-meeting-app","role":"HOST"} -> eyJpc3MiOiJsaXZla2l0LW1lZXRpbmctYXBwIiwiYXVkIjoibGl2ZWtpdC1tZWV0aW5nLWFwcCIsInJvbGUiOiJIT1NUIn0
        val header = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
        val payload = "eyJpc3MiOiJsaXZla2l0LW1lZXRpbmctYXBwIiwiYXVkIjoibGl2ZWtpdC1tZWV0aW5nLWFwcCIsInJvbGUiOiJIT1NUIn0"
        val token = "$header.$payload." // Empty signature

        val response = client.post("/api/v1/meetings/somecode/start") {
            bearerAuth(token)
        }
        
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
