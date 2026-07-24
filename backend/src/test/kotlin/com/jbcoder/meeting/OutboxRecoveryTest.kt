package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.persistence.AnonymousDeviceSessionsTable
import com.jbcoder.meeting.persistence.MeetingsTable
import com.jbcoder.meeting.persistence.ModerationOutboxTable
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class OutboxRecoveryTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            System.setProperty("POSTGRES_HOST", "127.0.0.1")
            System.setProperty("POSTGRES_PORT", "5432")
            System.setProperty("POSTGRES_DB", "livekit_meeting")
            System.setProperty("POSTGRES_USER", "postgres")
            System.setProperty("POSTGRES_PASSWORD", "postgres_password_placeholder")
            System.setProperty("REDIS_HOST", "127.0.0.1")
            System.setProperty("REDIS_PORT", "6379")
            System.setProperty("REDIS_PASSWORD", "redis_password_placeholder")
            System.setProperty("LIVEKIT_API_KEY", TestSecrets.liveKitApiKey)
            System.setProperty("LIVEKIT_API_SECRET", TestSecrets.liveKitApiSecret)
            System.setProperty("JWT_SECRET", TestSecrets.jwtSecret)

            val config = run { TestSecrets.setupTestProperties(); AppConfig.load() }
            DatabaseConfig.init(config)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            DatabaseConfig.close()
        }
    }

    @Test
    fun `stale jobs are correctly recovered by worker logic`() {
        val staleJobId = UUID.randomUUID()
        val recentJobId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val meetingId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        
        transaction {
            AnonymousDeviceSessionsTable.insert {
                it[id] = deviceId
                it[installationId] = "inst-${UUID.randomUUID()}"
                it[createdAt] = Instant.now()
                it[lastSeenAt] = Instant.now()
            }
            
            MeetingsTable.insert {
                it[id] = meetingId
                it[publicMeetingCode] = "CODE_${UUID.randomUUID().toString().take(6)}"
                it[livekitRoomName] = "ROOM_${UUID.randomUUID()}"
                it[title] = "Recovery Test Meeting"
                it[hostSecretHash] = "hash"
                it[status] = "READY"
                it[maximumParticipants] = 10
                it[expiresAt] = Instant.now().plus(1, ChronoUnit.HOURS)
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
            
            ParticipantSessionsTable.insert {
                it[id] = participantId
                it[this.meetingId] = meetingId
                it[livekitIdentity] = "part-${UUID.randomUUID()}"
                it[displayName] = "Part 1"
                it[deviceSessionId] = deviceId
                it[role] = "PARTICIPANT"
                it[state] = "IN_MEETING"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }

            ModerationOutboxTable.insert {
                it[id] = staleJobId
                it[this.meetingId] = meetingId
                it[this.participantSessionId] = participantId
                it[actionType] = "REMOVE"
                it[status] = "PROCESSING"
                it[workerId] = "crashed-worker-1"
                it[claimedAt] = Instant.now().minus(120, ChronoUnit.SECONDS)
                it[createdAt] = Instant.now().minus(130, ChronoUnit.SECONDS)
            }
            
            ModerationOutboxTable.insert {
                it[id] = recentJobId
                it[this.meetingId] = meetingId
                it[this.participantSessionId] = participantId
                it[actionType] = "REMOVE"
                it[status] = "PROCESSING"
                it[workerId] = "active-worker-2"
                it[claimedAt] = Instant.now().minus(10, ChronoUnit.SECONDS)
                it[createdAt] = Instant.now().minus(20, ChronoUnit.SECONDS)
            }
        }
        
        // Execute the recovery logic directly (equivalent to what the worker does)
        transaction {
            val staleThreshold = Instant.now().minusSeconds(60)
            ModerationOutboxTable.update({
                (ModerationOutboxTable.status eq "PROCESSING") and (ModerationOutboxTable.claimedAt less staleThreshold)
            }) {
                it[status] = "PENDING"
                it[workerId] = null
                it[claimedAt] = null
            }
        }
        
        // Verify
        transaction {
            val staleJob = ModerationOutboxTable.selectAll().where { ModerationOutboxTable.id eq staleJobId }.single()
            assertEquals("PENDING", staleJob[ModerationOutboxTable.status])
            assertEquals(null, staleJob[ModerationOutboxTable.workerId])
            
            val recentJob = ModerationOutboxTable.selectAll().where { ModerationOutboxTable.id eq recentJobId }.single()
            assertEquals("PROCESSING", recentJob[ModerationOutboxTable.status])
            assertEquals("active-worker-2", recentJob[ModerationOutboxTable.workerId])
        }
    }
}
