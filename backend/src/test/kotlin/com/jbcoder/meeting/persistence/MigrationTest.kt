package com.jbcoder.meeting.persistence

import com.jbcoder.meeting.configuration.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class MigrationTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var config: AppConfig
    private lateinit var testDbName: String

    @BeforeEach
    fun setup() {
        com.jbcoder.meeting.TestSecrets.setupTestProperties()
        config = AppConfig.load()
        
        // Generate a unique database name for this test execution
        testDbName = "migration_test_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        
        // Create the unique database by connecting to the default db
        val defaultHikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl // Assuming this points to the default test db
            username = config.dbUser
            password = config.dbPass
            driverClassName = "org.postgresql.Driver"
        }
        HikariDataSource(defaultHikari).use { ds ->
            ds.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("CREATE DATABASE $testDbName")
                }
            }
        }

        // Now connect to the new isolated database
        val newDbUrl = config.dbUrl.replaceAfterLast("/", testDbName)
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = newDbUrl
            username = config.dbUser
            password = config.dbPass
            driverClassName = "org.postgresql.Driver"
        }
        dataSource = HikariDataSource(hikariConfig)
    }

    @AfterEach
    fun teardown() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
        }
        
        // Drop the isolated database to prevent leaks
        val defaultHikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPass
            driverClassName = "org.postgresql.Driver"
        }
        try {
            HikariDataSource(defaultHikari).use { ds ->
                ds.connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        // Terminate other connections if any
                        stmt.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$testDbName'")
                        stmt.execute("DROP DATABASE IF EXISTS $testDbName")
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to drop test database: ${e.message}")
        }
    }

    @Test
    fun `test Flyway empty to latest migration`() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false) // Allow clean for tests
            .load()

        // Ensure database is clean
        flyway.clean()

        // Migrate empty -> latest
        val result = flyway.migrate()
        assertTrue(result.migrationsExecuted > 0, "Migrations should have been executed")
        assertEquals(10, result.migrationsExecuted, "Expected 10 migrations to run")
    }

    @Test
    fun `test safe rerun on already migrated database`() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()

        flyway.clean()
        flyway.migrate()

        // Second run should result in 0 migrations
        val rerunResult = flyway.migrate()
        assertEquals(0, rerunResult.migrationsExecuted, "Rerun should execute 0 migrations")
        assertTrue(rerunResult.success)
    }

    @Test
    fun `test preservation of records across migrations`() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()

        flyway.clean()
        // Migrate to V2
        val flywayV2 = Flyway.configure().dataSource(dataSource).cleanDisabled(false).target(org.flywaydb.core.api.MigrationVersion.fromVersion("2")).load()
        flywayV2.clean()
        flywayV2.migrate()

        // Insert a record (raw SQL using JDBC)
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    INSERT INTO meetings (id, public_meeting_code, livekit_room_name, title, host_secret_hash, status, waiting_room_enabled, join_before_host_enabled, is_locked, maximum_participants, expires_at, created_at, updated_at, optimistic_lock_version)
                    VALUES ('d1774e50-bd12-4217-9140-5b8719bcfe01', 'CODE123', 'ROOM123', 'Test Meeting', 'secret', 'SCHEDULED', true, false, false, 10, '2030-01-01 00:00:00', '2023-01-01 00:00:00', '2023-01-01 00:00:00', 1)
                """)
            }
        }

        // Migrate to latest
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).target(org.flywaydb.core.api.MigrationVersion.LATEST).load().migrate()

        // Verify record is still there
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM meetings WHERE public_meeting_code = 'CODE123'")
                rs.next()
                assertEquals(1, rs.getInt(1), "Meeting record should be preserved")
            }
        }
    }

    @Test
    fun `test failed state rejection`() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()

        flyway.clean()
        
        // Simulate a failed migration state
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE flyway_schema_history (
                        installed_rank INT NOT NULL,
                        version VARCHAR(50),
                        description VARCHAR(200) NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        script VARCHAR(1000) NOT NULL,
                        checksum INT,
                        installed_by VARCHAR(100) NOT NULL,
                        installed_on TIMESTAMP NOT NULL DEFAULT NOW(),
                        execution_time INT NOT NULL,
                        success BOOLEAN NOT NULL
                    );
                    INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                    VALUES (1, '1', 'Initial Schema', 'SQL', 'V1__Initial_Schema.sql', 12345, 'test', 10, false);
                """)
            }
        }

        val exception = assertThrows(org.flywaydb.core.api.FlywayException::class.java) {
            flyway.migrate()
        }
        assertTrue(exception.message!!.contains("failed", ignoreCase = true) || exception.message!!.contains("Validate failed", ignoreCase = true), "Should reject failed migration state")
    }
}

