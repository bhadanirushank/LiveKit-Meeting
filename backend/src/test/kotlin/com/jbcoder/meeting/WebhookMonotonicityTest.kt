package com.jbcoder.meeting

import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.domain.ParticipantState
import com.jbcoder.meeting.domain.WebhookReconciliationJob
import com.jbcoder.meeting.persistence.AnonymousDeviceSessionsTable
import com.jbcoder.meeting.persistence.MeetingsTable
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import com.jbcoder.meeting.persistence.WebhookEventsTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class WebhookMonotonicityTest {

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

            val config = AppConfig.load()
            DatabaseConfig.init(config)
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            DatabaseConfig.close()
        }
    }

    @Test
    fun `late participant_joined event should not overwrite LEFT or REMOVED state`() {
        val meetingId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        val livekitIdentity = "part-${UUID.randomUUID()}"
        val roomName = "ROOM_${UUID.randomUUID()}"

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
                it[livekitRoomName] = roomName
                it[title] = "Monotonicity Test"
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
                it[this.livekitIdentity] = livekitIdentity
                it[displayName] = "Test User"
                it[deviceSessionId] = deviceId
                it[role] = "PARTICIPANT"
                it[state] = ParticipantState.LEFT.name // Already LEFT
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }

            // Insert late participant_joined event
            WebhookEventsTable.insert {
                it[eventId] = UUID.randomUUID().toString()
                it[eventType] = "participant_joined"
                it[rawPayload] = """{"event":"participant_joined","participant":{"identity":"$livekitIdentity"}}"""
                it[receivedAt] = Instant.now()
                it[processingStatus] = "PENDING"
            }
        }

        // We use reflection to call the private processPendingEvents method for testing
        val method = WebhookReconciliationJob::class.java.getDeclaredMethod("processPendingEvents")
        method.isAccessible = true
        method.invoke(WebhookReconciliationJob)

        transaction {
            val p = ParticipantSessionsTable.selectAll().where { ParticipantSessionsTable.livekitIdentity eq livekitIdentity }.single()
            // Should still be LEFT, not JOINED
            assertEquals(ParticipantState.LEFT.name, p[ParticipantSessionsTable.state], "Late joined event overwrote LEFT state!")
        }
    }
}
