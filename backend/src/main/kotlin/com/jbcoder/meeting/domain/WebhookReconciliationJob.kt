package com.jbcoder.meeting.domain

import com.jbcoder.meeting.infrastructure.RedisService
import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.MeetingsTable
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import com.jbcoder.meeting.persistence.WebhookEventsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.coroutines.coroutineContext

object WebhookReconciliationJob {
    private val logger = LoggerFactory.getLogger(WebhookReconciliationJob::class.java)
    private const val LOCK_KEY = "lock:job:webhook-reconciliation"

    suspend fun startLoop() = withContext(Dispatchers.IO) {
        logger.info("Starting Webhook Reconciliation Job...")
        while (coroutineContext.isActive) {
            try {
                // Try to acquire distributed lock for 30 seconds
                if (RedisService.setIfAbsent(LOCK_KEY, "LOCKED", 30)) {
                    try {
                        processPendingEvents()
                    } finally {
                        RedisService.delete(LOCK_KEY)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error in WebhookReconciliationJob", e)
            }
            delay(5000) // Poll every 5 seconds
        }
    }

    private fun processPendingEvents() {
        val pendingEvents = transaction {
            WebhookEventsTable.selectAll()
                .where { WebhookEventsTable.processingStatus eq "PENDING" }
                .orderBy(WebhookEventsTable.receivedAt)
                .limit(50)
                .map {
                    Triple(
                        it[WebhookEventsTable.eventId],
                        it[WebhookEventsTable.eventType],
                        it[WebhookEventsTable.rawPayload]
                    )
                }
        }

        for ((eventId, eventType, payload) in pendingEvents) {
            try {
                handleEvent(eventType, payload)
                transaction {
                    WebhookEventsTable.update({ WebhookEventsTable.eventId eq eventId }) {
                        it[processingStatus] = "PROCESSED"
                        it[processedAt] = Instant.now()
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to process event $eventId", e)
                transaction {
                    WebhookEventsTable.update({ WebhookEventsTable.eventId eq eventId }) {
                        with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                            it[retryCount] = WebhookEventsTable.retryCount + 1
                        }
                        it[failureReason] = e.message
                        // We could mark it FAILED after max retries, keeping it simple for now.
                    }
                }
            }
        }
    }

    private fun handleEvent(eventType: String, rawPayload: String) {
        val json = Json { ignoreUnknownKeys = true }.parseToJsonElement(rawPayload).jsonObject
        
        when (eventType) {
            "participant_joined" -> {
                val participantObj = json["participant"]?.jsonObject ?: return
                val identity = participantObj["identity"]?.jsonPrimitive?.content ?: return
                
                transaction {
                    val p = ParticipantSessionsTable.selectAll().where { ParticipantSessionsTable.livekitIdentity eq identity }.singleOrNull()
                    if (p != null) {
                        val currentState = ParticipantState.valueOf(p[ParticipantSessionsTable.state])
                        if (currentState != ParticipantState.LEFT && currentState != ParticipantState.REMOVED) {
                            ParticipantSessionsTable.update({ ParticipantSessionsTable.livekitIdentity eq identity }) {
                                it[state] = ParticipantState.JOINED.name
                                it[joinedAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }
                        }
                    }
                }
            }
            "participant_left", "participant_connection_aborted" -> {
                val participantObj = json["participant"]?.jsonObject ?: return
                val identity = participantObj["identity"]?.jsonPrimitive?.content ?: return
                
                transaction {
                    // It is possible the backend already marked them REMOVED. Don't override REMOVED.
                    val p = ParticipantSessionsTable.selectAll().where { ParticipantSessionsTable.livekitIdentity eq identity }.singleOrNull()
                    if (p != null) {
                        val currentState = ParticipantState.valueOf(p[ParticipantSessionsTable.state])
                        if (currentState != ParticipantState.REMOVED) {
                            ParticipantSessionsTable.update({ ParticipantSessionsTable.livekitIdentity eq identity }) {
                                it[state] = ParticipantState.LEFT.name
                                it[leftAt] = Instant.now()
                                it[updatedAt] = Instant.now()
                            }
                        }
                    }
                }
            }
            "room_finished" -> {
                val roomObj = json["room"]?.jsonObject ?: return
                val roomName = roomObj["name"]?.jsonPrimitive?.content ?: return
                
                transaction {
                    val meeting = MeetingsTable.selectAll().where { MeetingsTable.livekitRoomName eq roomName }.singleOrNull()
                    if (meeting != null) {
                        val id = meeting[MeetingsTable.id]
                        val version = meeting[MeetingsTable.optimisticLockVersion]
                        val status = MeetingStatus.valueOf(meeting[MeetingsTable.status])
                        if (status != MeetingStatus.ENDED && status != MeetingStatus.CANCELLED) {
                            MeetingRepository.updateStatusWithOptimisticLock(id, version, MeetingStatus.ENDED)
                        }
                    }
                }
            }
            else -> {
                // Ignore other events
            }
        }
    }
}
