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
