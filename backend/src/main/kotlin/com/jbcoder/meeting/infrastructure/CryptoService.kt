package com.jbcoder.meeting.infrastructure

import de.mkammerer.argon2.Argon2Factory
import java.security.SecureRandom
import java.util.Base64

object CryptoService {
    // We use Argon2id variant as recommended by OWASP
    private val argon2 = Argon2Factory.create(
        Argon2Factory.Argon2Types.ARGON2id,
        32, // salt length
        64  // hash length
    )
    
    // Server-side configurable parameters
    // In production, these should ideally be loaded from environment variables
    private const val ITERATIONS = 3
    private const val MEMORY_KB = 65536 // 64 MB
    private const val PARALLELISM = 2

    // Secure random for tokens and secrets
    private val secureRandom = SecureRandom()

    /**
     * Hashes a plaintext password/secret using Argon2id.
     * Includes an optional pepper for additional server-side security.
     */
    fun hash(plaintext: String, pepper: String = ""): String {
        val pepperedText = plaintext + pepper
        // argon2-jvm handles salt generation and embedding internally
        return argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, pepperedText.toCharArray())
    }

    /**
     * Verifies a plaintext password against an Argon2id hash.
     * This operation is constant-time where supported by the underlying C implementation.
     */
    fun verify(plaintext: String, hash: String, pepper: String = ""): Boolean {
        val pepperedText = plaintext + pepper
        return argon2.verify(hash, pepperedText.toCharArray())
    }

    /**
     * Generates a secure random alphanumeric string of the specified length.
     * Useful for meeting codes and high-entropy host secrets.
     */
    fun generateSecureRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(chars[secureRandom.nextInt(chars.length)])
        }
        return sb.toString()
    }

    /**
     * Generates a URL-safe Base64 encoded token (e.g., for refresh tokens or session identifiers).
     */
    fun generateUrlSafeToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
