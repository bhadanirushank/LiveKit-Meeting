package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.ModerationOutboxTable
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

object ModerationOutboxWorker {
    private val logger = LoggerFactory.getLogger(ModerationOutboxWorker::class.java)
    private var workerScope: CoroutineScope? = null
    private var job: Job? = null
    private val workerId = UUID.randomUUID().toString()
    
    // Add DB backoff tracking
    private var dbErrorCount = 0
    private val maxDbErrorCount = 10

    fun start() {
        if (job?.isActive == true) return
        logger.info("Starting ModerationOutboxWorker (workerId=$workerId)...")
        
        // Use an explicit application-owned scope with a named dispatcher
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("ModerationOutboxWorker"))
        workerScope = scope
        
        job = scope.launch {
            while (isActive) {
                try {
                    processJobs()
                    dbErrorCount = 0 // Reset on success
                    delay(3000)
                } catch (e: Exception) {
                    dbErrorCount++
                    val backoffSeconds = ((1 shl minOf(dbErrorCount, 6)) * 2L) + Random.nextLong(0, 3)
                    logger.error("Error in ModerationOutboxWorker loop (Attempt $dbErrorCount). Backing off for ${backoffSeconds}s", e)
                    
                    if (dbErrorCount >= maxDbErrorCount) {
                        logger.error("Maximum DB error count reached. ModerationOutboxWorker will suspend longer.")
                        delay(60000) // Suspend for a long time before trying again
                    } else {
                        delay(backoffSeconds * 1000)
                    }
                }
            }
        }
    }

    suspend fun stop() {
        logger.info("Stopping ModerationOutboxWorker...")
        job?.cancelAndJoin()
        workerScope?.cancel()
        job = null
        workerScope = null
        logger.info("ModerationOutboxWorker stopped.")
    }

    private suspend fun processJobs() {
        // Recover abandoned jobs
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

        // Claim and process jobs
        while (true) {
            val claimedJobIds = transaction {
                val now = Instant.now()
                // Step 1: SELECT FOR UPDATE SKIP LOCKED to get candidate IDs
                val selectSql = """
                    SELECT id FROM moderation_outbox 
                    WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= ?) 
                    LIMIT 5
                    FOR UPDATE SKIP LOCKED
                """.trimIndent()

                val ids = mutableListOf<UUID>()
                exec(selectSql, listOf(org.jetbrains.exposed.sql.javatime.JavaInstantColumnType() to now)) { rs ->
                    while (rs.next()) {
                        ids.add(UUID.fromString(rs.getString("id")))
                    }
                }

                // Step 2: UPDATE the claimed rows in the same transaction
                if (ids.isNotEmpty()) {
                    val placeholders = ids.joinToString(",") { "'${it}'" }
                    val updateSql = """
                        UPDATE moderation_outbox
                        SET worker_id = '$workerId', claimed_at = '$now', status = 'PROCESSING'
                        WHERE id IN ($placeholders)
                    """.trimIndent()
                    exec(updateSql)
                }
                ids
            }

            if (claimedJobIds.isEmpty()) {
                break
            }

            for (jobId in claimedJobIds) {
                processJob(jobId)
            }
        }
    }

    private suspend fun processJob(jobId: UUID) {
        // Fetch job details
        val jobInfo = transaction {
            val jobRow = ModerationOutboxTable.selectAll().where { ModerationOutboxTable.id eq jobId }.single()
            val participantId = jobRow[ModerationOutboxTable.participantSessionId]
            
            val pRow = ParticipantSessionsTable.innerJoin(com.jbcoder.meeting.persistence.MeetingsTable)
                .selectAll()
                .where { ParticipantSessionsTable.id eq participantId }
                .single()
            
            JobInfo(
                actionType = jobRow[ModerationOutboxTable.actionType],
                roomName = pRow[com.jbcoder.meeting.persistence.MeetingsTable.livekitRoomName],
                identity = pRow[ParticipantSessionsTable.livekitIdentity],
                attempts = jobRow[ModerationOutboxTable.retryCount]
            )
        }
        val actionType = jobInfo.actionType
        val livekitRoomName = jobInfo.roomName
        val livekitIdentity = jobInfo.identity
        val attempts = jobInfo.attempts

        try {
            // Perform LiveKit network call outside transaction
            if (actionType == "REMOVE_PARTICIPANT") {
                LiveKitRoomService.removeParticipant(livekitRoomName, livekitIdentity)
            }

            // Mark successful
            transaction {
                ModerationOutboxTable.update({ ModerationOutboxTable.id eq jobId }) {
                    it[status] = "COMPLETED"
                    it[completedAt] = Instant.now()
                }
            }
            logger.info("Moderation job ${"$jobId"} completed successfully.")
        } catch (e: Exception) {
            val newAttempts = attempts + 1
            if (newAttempts >= 5) {
                // Permanent failure
                transaction {
                    ModerationOutboxTable.update({ ModerationOutboxTable.id eq jobId }) {
                        it[status] = "FAILED_PERMANENTLY"
                        it[failureReason] = e.message
                        it[retryCount] = newAttempts
                    }
                }
                logger.error("Moderation job ${"$jobId"} failed permanently.", e)
            } else {
                // Exponential backoff: 2s, 4s, 8s, 16s + jitter
                val backoffSeconds = (1 shl newAttempts) * 2L
                val jitter = Random.nextLong(0, 3)
                val nextRetry = Instant.now().plusSeconds(backoffSeconds + jitter)
                
                transaction {
                    ModerationOutboxTable.update({ ModerationOutboxTable.id eq jobId }) {
                        it[status] = "PENDING"
                        it[failureReason] = e.message
                        it[retryCount] = newAttempts
                        it[nextRetryAt] = nextRetry
                        it[workerId] = null
                        it[claimedAt] = null
                    }
                }
                logger.warn("Moderation job ${"$jobId"} failed. Retrying at ${"$nextRetry"}.", e)
            }
        }
    }
    
    private data class JobInfo(
        val actionType: String,
        val roomName: String,
        val identity: String,
        val attempts: Int
    )
}

