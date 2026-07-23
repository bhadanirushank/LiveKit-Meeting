package com.jbcoder.meeting.configuration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)
    private lateinit var dataSource: HikariDataSource

    fun init(config: AppConfig) {
        logger.info("Initializing database connection pool...")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPass
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            connectionTimeout = 5000 // 5 seconds
            idleTimeout = 600000 // 10 minutes
            maxLifetime = 1800000 // 30 minutes
            validationTimeout = 2000 // 2 seconds
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        
        if (config.flywayMigrateOnStart) {
            logger.info("Running Flyway migrations...")
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(true) // No destructive clean operations
                .validateOnMigrate(true)
                .load()
            
            try {
                flyway.migrate()
                logger.info("Flyway migrations completed successfully.")
            } catch (e: Exception) {
                logger.error("Flyway migration failed: ${e.message}", e)
                throw e
            }
        }

        Database.connect(dataSource)
        logger.info("Database connection initialized.")
    }

    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            logger.info("Closing database connection pool...")
            dataSource.close()
        }
    }
    
    // Check if the database is reachable (lightweight query)
    fun isHealthy(): Boolean {
        return try {
            transaction {
                val conn = connection.connection as java.sql.Connection
                conn.isValid(2) // 2 seconds timeout
            }
        } catch (e: Exception) {
            false
        }
    }
}

// Utility to run queries on IO dispatcher
suspend fun <T> query(block: () -> T): T =
    withContext(Dispatchers.IO) {
        transaction { block() }
    }
