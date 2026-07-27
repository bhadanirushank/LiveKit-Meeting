package com.jbcoder.meeting.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TestOutputRedactorTest {

    @Test
    fun `redacts access and refresh credentials`() {
        val input = "Here is atk_1234abcd and rtk_XYZ987-foo"
        val expected = "Here is atk_[REDACTED] and rtk_[REDACTED]"
        assertEquals(expected, TestOutputRedactor.redact(input))
    }

    @Test
    fun `redacts authorization header case-insensitively`() {
        val input1 = "Authorization: Bearer mySecretToken\nAccept: */*"
        val input2 = "AUTHORIZATION: Bearer anotherSecret\r\nConnection: keep-alive"
        
        val expected1 = "Authorization: [REDACTED]\nAccept: */*"
        val expected2 = "AUTHORIZATION: [REDACTED]\r\nConnection: keep-alive"
        
        assertEquals(expected1, TestOutputRedactor.redact(input1))
        assertEquals(expected2, TestOutputRedactor.redact(input2))
    }

    @Test
    fun `redacts bearer token independently`() {
        val input = "Some text Bearer 1234567890abcdef text"
        val expected = "Some text Bearer [REDACTED] text"
        assertEquals(expected, TestOutputRedactor.redact(input))
    }

    @Test
    fun `redacts idempotency key header case-insensitively`() {
        val input = "idempotency-key: 123e4567-e89b-12d3-a456-426614174000\nHost: localhost"
        val expected = "idempotency-key: [REDACTED]\nHost: localhost"
        assertEquals(expected, TestOutputRedactor.redact(input))
    }

    @Test
    fun `redacts nested JSON sensitive fields`() {
        val json = """
            {
                "sessionId": "1234",
                "accessToken": "atk_secret_value",
                "refreshToken": "rtk_secret_value",
                "token": "livekit_secret",
                "encryptedToken": "base64_secret",
                "hostSecret": "host_super_secret"
            }
        """.trimIndent()
        
        val redacted = TestOutputRedactor.redact(json)
        
        assertTrue(redacted.contains("\"sessionId\": \"1234\""))
        assertTrue(redacted.contains("\"accessToken\": \"[REDACTED]\""))
        assertTrue(redacted.contains("\"refreshToken\": \"[REDACTED]\""))
        assertTrue(redacted.contains("\"token\": \"[REDACTED]\""))
        assertTrue(redacted.contains("\"encryptedToken\": \"[REDACTED]\""))
        assertTrue(redacted.contains("\"hostSecret\": \"[REDACTED]\""))
    }

    @Test
    fun `redacts exception messages containing sensitive values`() {
        val exceptionMessage = "Failed to validate token atk_badtoken for user"
        val expected = "Failed to validate token atk_[REDACTED] for user"
        assertEquals(expected, TestOutputRedactor.redact(exceptionMessage))
    }
    
    @Test
    fun `redacts encryption key value`() {
        val key = "mySuperSecretEncryptionKey32B="
        TestOutputRedactor.setEncryptionKey(key)
        val input = "Key is mySuperSecretEncryptionKey32B= in logs"
        val expected = "Key is [REDACTED] in logs"
        assertEquals(expected, TestOutputRedactor.redact(input))
    }

    @Test
    fun `leaves non-sensitive fields intact`() {
        val uuid = UUID.randomUUID().toString()
        val input = "Meeting code TEST-1234, ID $uuid, Status 200, State ADMITTED, TS 2026-07-27"
        assertEquals(input, TestOutputRedactor.redact(input))
    }
    
    @Test
    fun `redacts multiple credentials in a single multiline string`() {
        val input = """
            POST /api/v1/session/refresh HTTP/1.1
            Authorization: Bearer rtk_oldToken
            Idempotency-Key: some-uuid
            
            Response: 200 OK
            {
                "accessToken": "atk_newToken",
                "refreshToken": "rtk_newRefreshToken"
            }
        """.trimIndent()
        
        val expected = """
            POST /api/v1/session/refresh HTTP/1.1
            Authorization: [REDACTED]
            Idempotency-Key: [REDACTED]
            
            Response: 200 OK
            {
                "accessToken": "[REDACTED]",
                "refreshToken": "[REDACTED]"
            }
        """.trimIndent()
        
        assertEquals(expected, TestOutputRedactor.redact(input))
    }
}
