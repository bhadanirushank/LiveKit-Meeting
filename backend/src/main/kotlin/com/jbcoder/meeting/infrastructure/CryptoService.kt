package com.jbcoder.meeting.infrastructure

import de.mkammerer.argon2.Argon2Factory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.security.SecureRandom
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
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

    // Bounded dedicated crypto dispatcher to prevent CPU exhaustion on concurrent requests
    // Argon2 is blocking and CPU intensive. We limit concurrency to exactly the number of available processors.
    private val cryptoDispatcher = Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

    /**
     * Hashes a plaintext password/secret using Argon2id.
     * Includes an optional pepper for additional server-side security.
     */
    suspend fun hash(plaintext: String, pepper: String = ""): String = withContext(cryptoDispatcher) {
        val pepperedText = plaintext + pepper
        // argon2-jvm handles salt generation and embedding internally
        argon2.hash(ITERATIONS, MEMORY_KB, PARALLELISM, pepperedText.toCharArray())
    }

    /**
     * Verifies a plaintext password against an Argon2id hash.
     * This operation is constant-time where supported by the underlying C implementation.
     */
    suspend fun verify(plaintext: String, hash: String, pepper: String = ""): Boolean = withContext(cryptoDispatcher) {
        val pepperedText = plaintext + pepper
        argon2.verify(hash, pepperedText.toCharArray())
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
     * Generates a secure random numeric string of the specified length.
     * Useful for numeric meeting codes.
     */
    fun generateSecureNumericString(length: Int): String {
        val chars = "0123456789"
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

    /**
     * Hashes a 256-bit cryptographically secure token using SHA-256 for fast indexed database lookups.
     * Do not use for user passwords, only for machine-generated high-entropy tokens.
     */
    fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        return java.security.MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts a string using AES-GCM.
     * @param plaintext The text to encrypt
     * @param key A 256-bit (32 bytes) key derived from the bearer token or system key
     * @return A Pair containing (Base64 Encoded Ciphertext, Base64 Encoded IV)
     */
    fun encryptAesGcm(plaintext: String, keyBytes: ByteArray): Pair<String, String> {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val gcmParameterSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        return Pair(
            Base64.getEncoder().encodeToString(cipherText),
            Base64.getEncoder().encodeToString(iv)
        )
    }

    /**
     * Decrypts an AES-GCM encrypted string.
     */
    fun decryptAesGcm(base64Ciphertext: String, base64Iv: String, keyBytes: ByteArray): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.getDecoder().decode(base64Iv)
        val gcmParameterSpec = javax.crypto.spec.GCMParameterSpec(128, iv)
        val secretKeySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
        val plainTextBytes = cipher.doFinal(Base64.getDecoder().decode(base64Ciphertext))
        
        return String(plainTextBytes, Charsets.UTF_8)
    }
}
