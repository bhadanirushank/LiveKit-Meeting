package com.jbcoder.meeting.configuration

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.Base64
import java.security.SecureRandom

class AppConfigTest {

    private val originalProperties = mutableMapOf<String, String?>()
    private val requiredKeys = listOf(
        "POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "REDIS_PASSWORD",
        "LIVEKIT_API_KEY", "LIVEKIT_API_SECRET", "JWT_SECRET", "TOKEN_DELIVERY_ENCRYPTION_KEY_B64"
    )

    @BeforeEach
    fun setup() {
        requiredKeys.forEach { key ->
            originalProperties[key] = System.getProperty(key)
        }
        System.setProperty("POSTGRES_DB", "test")
        System.setProperty("POSTGRES_USER", "test")
        System.setProperty("POSTGRES_PASSWORD", "test")
        System.setProperty("REDIS_PASSWORD", "test")
        System.setProperty("LIVEKIT_API_KEY", "test")
        System.setProperty("LIVEKIT_API_SECRET", "test")
        System.setProperty("JWT_SECRET", "test")
    }

    @AfterEach
    fun teardown() {
        requiredKeys.forEach { key ->
            val orig = originalProperties[key]
            if (orig == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, orig)
            }
        }
    }

    @Test
    fun `exactly 32 bytes succeeds`() {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", Base64.getEncoder().encodeToString(bytes))
        val config = AppConfig.load()
        assertEquals(Base64.getEncoder().encodeToString(bytes), config.tokenDeliveryEncryptionKeyB64)
    }

    @Test
    fun `16 byte value fails startup`() {
        val bytes = ByteArray(16)
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", Base64.getEncoder().encodeToString(bytes))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            AppConfig.load()
        }
        assertEquals("TOKEN_DELIVERY_ENCRYPTION_KEY_B64 must decode to exactly 32 bytes, found 16 bytes.", exception.message)
    }

    @Test
    fun `24 byte value fails startup`() {
        val bytes = ByteArray(24)
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", Base64.getEncoder().encodeToString(bytes))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            AppConfig.load()
        }
        assertEquals("TOKEN_DELIVERY_ENCRYPTION_KEY_B64 must decode to exactly 32 bytes, found 24 bytes.", exception.message)
    }

    @Test
    fun `31 byte value fails startup`() {
        val bytes = ByteArray(31)
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", Base64.getEncoder().encodeToString(bytes))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            AppConfig.load()
        }
        assertEquals("TOKEN_DELIVERY_ENCRYPTION_KEY_B64 must decode to exactly 32 bytes, found 31 bytes.", exception.message)
    }

    @Test
    fun `33 byte value fails startup`() {
        val bytes = ByteArray(33)
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", Base64.getEncoder().encodeToString(bytes))
        val exception = assertThrows(IllegalArgumentException::class.java) {
            AppConfig.load()
        }
        assertEquals("TOKEN_DELIVERY_ENCRYPTION_KEY_B64 must decode to exactly 32 bytes, found 33 bytes.", exception.message)
    }

    @Test
    fun `malformed Base64 fails startup`() {
        System.setProperty("TOKEN_DELIVERY_ENCRYPTION_KEY_B64", "not-base-64-@#!")
        val exception = assertThrows(IllegalArgumentException::class.java) {
            AppConfig.load()
        }
        assertEquals("TOKEN_DELIVERY_ENCRYPTION_KEY_B64 must be valid Base64.", exception.message)
    }
}
