package com.jbcoder.meeting.security

object TestOutputRedactor {
    // Dynamic encryption key configuration value. We don't have the exact value dynamically, 
    // but tests can pass it if they know it, or we redact Base64-like strings of length 43 (32 bytes).
    // Actually, it's safer to redact exactly when it matches the environment variable.
    private var encryptionKey: String? = null
    
    fun setEncryptionKey(key: String) {
        encryptionKey = key
    }

    private val jsonAccessTokenPattern = Regex("(\"accessToken\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonRefreshTokenPattern = Regex("(\"refreshToken\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonLivekitTokenPattern = Regex("(\"livekitToken\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonTokenPattern = Regex("(\"token\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonEncryptedTokenPattern = Regex("(\"encryptedToken\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonHostSecretPattern = Regex("(\"hostSecret\"\\s*:\\s*\")[^\"]+(\")")
    private val jsonHostSecretHashPattern = Regex("(\"hostSecretHash\"\\s*:\\s*\")[^\"]+(\")")
    
    private val authHeaderPattern = Regex("(?i)(Authorization\\s*:\\s*)[^\\r\\n]+")
    private val idempotencyHeaderPattern = Regex("(?i)(Idempotency-Key\\s*:\\s*)[^\\r\\n]+")
    
    private val bearerPattern = Regex("Bearer\\s+[\\w\\-._~+/]+={0,2}")
    private val atkPattern = Regex("atk_[\\w\\-._~+/]+")
    private val rtkPattern = Regex("rtk_[\\w\\-._~+/]+")

    fun redact(input: String): String {
        var result = input
        
        // Structured JSON
        result = jsonAccessTokenPattern.replace(result, "$1[REDACTED]$2")
        result = jsonRefreshTokenPattern.replace(result, "$1[REDACTED]$2")
        result = jsonLivekitTokenPattern.replace(result, "$1[REDACTED]$2")
        result = jsonTokenPattern.replace(result, "$1[REDACTED]$2")
        result = jsonEncryptedTokenPattern.replace(result, "$1[REDACTED]$2")
        result = jsonHostSecretPattern.replace(result, "$1[REDACTED]$2")
        result = jsonHostSecretHashPattern.replace(result, "$1[REDACTED]$2")
        
        // Headers
        result = authHeaderPattern.replace(result, "$1[REDACTED]")
        result = idempotencyHeaderPattern.replace(result, "$1[REDACTED]")
        
        // Fallbacks
        result = bearerPattern.replace(result, "Bearer [REDACTED]")
        result = atkPattern.replace(result, "atk_[REDACTED]")
        result = rtkPattern.replace(result, "rtk_[REDACTED]")
        
        // Dynamic Encryption Key
        encryptionKey?.let {
            if (it.isNotBlank()) {
                result = result.replace(it, "[REDACTED]")
            }
        }
        
        return result
    }
}
