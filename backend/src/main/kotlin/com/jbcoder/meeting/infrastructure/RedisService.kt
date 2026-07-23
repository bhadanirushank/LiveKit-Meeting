package com.jbcoder.meeting.infrastructure

import com.jbcoder.meeting.configuration.RedisConfig
import io.lettuce.core.SetArgs
import kotlinx.coroutines.future.await

object RedisService {
    
    suspend fun setIfAbsent(key: String, value: String, expirySeconds: Long): Boolean {
        val result = RedisConfig.connection.async()
            .set(key, value, SetArgs.Builder.nx().ex(expirySeconds))
            .await()
        return result == "OK"
    }

    suspend fun get(key: String): String? {
        return RedisConfig.connection.async().get(key).await()
    }

    suspend fun set(key: String, value: String, expirySeconds: Long) {
        RedisConfig.connection.async().setex(key, expirySeconds, value).await()
    }

    suspend fun delete(key: String) {
        RedisConfig.connection.async().del(key).await()
    }
    
    data class RateLimitResult(val count: Long, val ttl: Long)

    /**
     * Increments a counter and sets an expiry if it's newly created, atomically via Lua script.
     * Returns a pair of (current count, ttl).
     */
    suspend fun incrementAndGetAtomic(key: String, windowSeconds: Long): RateLimitResult {
        val script = """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
                redis.call('expire', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('ttl', KEYS[1])
            return {current, ttl}
        """.trimIndent()
        
        val result = RedisConfig.connection.async()
            .eval<List<Long>>(script, io.lettuce.core.ScriptOutputType.MULTI, arrayOf(key), windowSeconds.toString())
            .await()
            
        return RateLimitResult(result[0], result[1])
    }
    
    /**
     * Gets the current TTL for a key. Returns -2 if key does not exist, -1 if no expiry.
     */
    suspend fun getTtl(key: String): Long {
        return RedisConfig.connection.async().ttl(key).await()
    }
}
