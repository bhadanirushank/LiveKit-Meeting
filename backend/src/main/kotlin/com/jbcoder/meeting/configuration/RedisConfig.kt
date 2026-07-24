package com.jbcoder.meeting.configuration

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import org.slf4j.LoggerFactory

object RedisConfig {
    private val logger = LoggerFactory.getLogger(RedisConfig::class.java)
    private lateinit var redisClient: RedisClient
    lateinit var connection: StatefulRedisConnection<String, String>
        private set

    fun init(config: AppConfig) {
        logger.info("Initializing Redis connection...")
        
        val redisUri = RedisURI.builder()
            .withHost(config.redisHost)
            .withPort(config.redisPort)
            .withPassword(config.redisPass.toCharArray())
            .withTimeout(java.time.Duration.ofSeconds(5))
            .build()
            
        redisClient = RedisClient.create(redisUri)
        
        // Use fail-fast behavior: REJECT_COMMANDS and enforce timeouts on queued/active commands
        val clientOptions = io.lettuce.core.ClientOptions.builder()
            .disconnectedBehavior(io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .timeoutOptions(io.lettuce.core.TimeoutOptions.builder().fixedTimeout(java.time.Duration.ofSeconds(5)).build())
            .build()
        redisClient.setOptions(clientOptions)
        
        connection = redisClient.connect()
        
        logger.info("Redis connection initialized.")
    }

    fun close() {
        if (::connection.isInitialized) {
            logger.info("Closing Redis connection...")
            connection.close()
        }
        if (::redisClient.isInitialized) {
            redisClient.shutdown()
        }
    }
    
    fun isHealthy(): Boolean {
        return try {
            val pong = connection.sync().ping()
            pong.equals("PONG", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
