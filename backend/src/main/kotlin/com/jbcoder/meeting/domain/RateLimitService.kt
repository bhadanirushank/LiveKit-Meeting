package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.RedisService

object RateLimitService {

    /**
     * Checks rate limits for join requests. 
     * Returns true if request is allowed, false if rate limited.
     */
    suspend fun isJoinRequestAllowed(
        ipAddress: String, 
        deviceSessionId: String, 
        publicMeetingCode: String
    ): Boolean {
        // Simple rate limiting logic using sliding window approximations or fixed window counters in Redis
        val ipKey = "ratelimit:join:ip:$ipAddress"
        val deviceKey = "ratelimit:join:device:$deviceSessionId"
        val meetingKey = "ratelimit:join:meeting:$publicMeetingCode"
        
        // Limits:
        // IP: 20 per minute
        // Device: 10 per minute
        // Meeting: 100 per minute
        
        val limit = System.getProperty("RATE_LIMIT")?.toIntOrNull() ?: 20
        val ipCount = RedisService.incrementAndGetAtomic(ipKey, 60).count
        if (ipCount > limit) return false
        
        val deviceCount = RedisService.incrementAndGetAtomic(deviceKey, 60).count
        if (deviceCount > 10) return false
        
        val meetingCount = RedisService.incrementAndGetAtomic(meetingKey, 60).count
        if (meetingCount > 100) return false
        
        return true
    }

    /**
     * Records a failed passcode attempt and checks if temporarily locked.
     * Returns true if locked, false if allowed to retry.
     */
    suspend fun recordFailedAttempt(deviceSessionId: String): Boolean {
        val failKey = "ratelimit:fail:device:$deviceSessionId"
        // 5 failures per 5 minutes = lock
        val failCount = RedisService.incrementAndGetAtomic(failKey, 300).count
        if (failCount > 5) {
            // Apply lock
            val lockKey = "ratelimit:lock:device:$deviceSessionId"
            RedisService.set(lockKey, "LOCKED", 900) // 15 minute lock
            return true
        }
        return false
    }
    
    suspend fun isLocked(deviceSessionId: String): Boolean {
        val lockKey = "ratelimit:lock:device:$deviceSessionId"
        return RedisService.get(lockKey) != null
    }
}
