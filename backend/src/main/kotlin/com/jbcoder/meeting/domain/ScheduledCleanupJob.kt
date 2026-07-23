package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.RedisService
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.MeetingsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.coroutines.coroutineContext

object ScheduledCleanupJob {
    private val logger = LoggerFactory.getLogger(ScheduledCleanupJob::class.java)
    private const val LOCK_KEY = "lock:job:cleanup"

    suspend fun startLoop() = withContext(Dispatchers.IO) {
        logger.info("Starting Scheduled Cleanup Job...")
        while (coroutineContext.isActive) {
            try {
                // Try to acquire distributed lock for 60 seconds (Run once a minute)
                if (RedisService.setIfAbsent(LOCK_KEY, "LOCKED", 60)) {
                    processExpiredMeetings()
                    processEmptyRooms()
                    pruneWebhooks()
                }
            } catch (e: Exception) {
                logger.error("Error in ScheduledCleanupJob", e)
            }
            delay(10000) // Poll every 10 seconds, though the lock naturally spaces it to 60s
        }
    }

    private fun processExpiredMeetings() {
        val now = Instant.now()
        val expiredIds = transaction {
            MeetingsTable.selectAll()
                .where { 
                    (MeetingsTable.expiresAt lessEq now) and
                    (MeetingsTable.status notInList listOf(MeetingStatus.EXPIRED.name, MeetingStatus.CANCELLED.name))
                }
                .map { Pair(it[MeetingsTable.id], it[MeetingsTable.optimisticLockVersion]) }
        }

        for ((id, version) in expiredIds) {
            try {
                val success = MeetingRepository.updateStatusWithOptimisticLock(id, version, MeetingStatus.EXPIRED)
                if (success) {
                    logger.info("Meeting $id automatically marked as EXPIRED")
                }
            } catch (e: Exception) {
                logger.error("Failed to expire meeting $id", e)
            }
        }
    }

    private suspend fun processEmptyRooms() {
        val config = com.jbcoder.meeting.configuration.AppConfig.load()
        val client = io.livekit.server.RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
        
        val liveMeetings = transaction {
            MeetingsTable.selectAll()
                .where { MeetingsTable.status eq MeetingStatus.LIVE.name }
                .map { 
                    Triple(
                        it[MeetingsTable.id],
                        it[MeetingsTable.livekitRoomName],
                        it[MeetingsTable.firstObservedEmptyAt]
                    )
                }
        }
        
        val now = Instant.now()
        val emptyTimeoutSeconds = 300L // 5 minutes

        for ((id, roomName, firstObserved) in liveMeetings) {
            try {
                val participantsResponse = client.listParticipants(roomName).execute()
                val isEmpty = if (participantsResponse.isSuccessful) {
                    val body = participantsResponse.body()
                    body.isNullOrEmpty()
                } else if (participantsResponse.code() == 404) {
                    true // Room doesn't exist on LK anymore
                } else {
                    false
                }

                transaction {
                    if (isEmpty) {
                        if (firstObserved == null) {
                            MeetingsTable.update({ MeetingsTable.id eq id }) {
                                it[firstObservedEmptyAt] = now
                            }
                        } else if (now.isAfter(firstObserved.plusSeconds(emptyTimeoutSeconds))) {
                            val meeting = MeetingRepository.findById(id)
                            if (meeting != null) {
                                MeetingRepository.updateStatusWithOptimisticLock(id, meeting.optimisticLockVersion, MeetingStatus.ENDED)
                                logger.info("Meeting $id ended due to being empty for $emptyTimeoutSeconds seconds")
                            }
                        }
                    } else {
                        // Reset if someone joined
                        if (firstObserved != null) {
                            MeetingsTable.update({ MeetingsTable.id eq id }) {
                                it[firstObservedEmptyAt] = null
                            }
                        }
                    }
                    Unit
                }
            } catch (e: Exception) {
                logger.error("Failed to check room $roomName for emptiness", e)
            }
        }
    }

    private fun pruneWebhooks() {
        try {
            transaction {
                val cutoff = Instant.now().minusSeconds(86400 * 7)
                com.jbcoder.meeting.persistence.WebhookEventsTable.deleteWhere { 
                    com.jbcoder.meeting.persistence.WebhookEventsTable.receivedAt less cutoff 
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to prune webhooks", e)
        }
    }
}
