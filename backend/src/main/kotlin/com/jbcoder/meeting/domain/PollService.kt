package com.jbcoder.meeting.domain

import com.jbcoder.meeting.persistence.MeetingRepository
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import com.jbcoder.meeting.persistence.PollOptionsTable
import com.jbcoder.meeting.persistence.PollsTable
import com.jbcoder.meeting.persistence.PollVotesTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object PollService {
    
    data class CreatePollCommand(
        val meetingId: UUID,
        val question: String,
        val allowMultipleAnswers: Boolean,
        val options: List<String>,
        val createdBy: UUID?  // null when created by the host session (no participant row)
    )
    
    suspend fun createPoll(command: CreatePollCommand): Result<UUID> = withContext(Dispatchers.IO) {
        if (command.options.size < 2) return@withContext Result.failure(Exception("At least 2 options are required"))
        if (command.question.isBlank()) return@withContext Result.failure(Exception("Question cannot be blank"))

        val meeting = MeetingRepository.findById(command.meetingId)
            ?: return@withContext Result.failure(Exception("Meeting not found"))

        transaction {
            val pollId = UUID.randomUUID()
            PollsTable.insert {
                it[id] = pollId
                it[meetingId] = meeting.id
                it[question] = command.question
                it[allowMultipleAnswers] = command.allowMultipleAnswers
                it[status] = "DRAFT"
                it[createdBy] = command.createdBy
                it[createdAt] = Instant.now()
            }
            
            command.options.forEachIndexed { index, optionText ->
                PollOptionsTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.pollId] = pollId
                    it[this.optionText] = optionText
                    it[sortOrder] = index
                }
            }
            Result.success(pollId)
        }
    }
    
    suspend fun vote(pollId: UUID, optionId: UUID, participantId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        transaction {
            val poll = PollsTable.selectAll().where { PollsTable.id eq pollId }.singleOrNull()
                ?: return@transaction Result.failure(Exception("Poll not found"))
                
            if (poll[PollsTable.status] != "OPEN") {
                return@transaction Result.failure(Exception("Poll is not open for voting"))
            }
            
            // Check if option exists
            val optionExists = PollOptionsTable.selectAll().where { (PollOptionsTable.id eq optionId) and (PollOptionsTable.pollId eq pollId) }.count() > 0
            if (!optionExists) {
                return@transaction Result.failure(Exception("Option not found in this poll"))
            }

            // Check if participant session exists
            val participantExists = ParticipantSessionsTable.selectAll().where { ParticipantSessionsTable.id eq participantId }.count() > 0
            if (!participantExists) {
                return@transaction Result.failure(Exception("Participant session not found"))
            }

            // Enforce allowMultipleAnswers constraint
            val allowMultiple = poll[PollsTable.allowMultipleAnswers]
            if (!allowMultiple) {
                val existingVoteCount = PollVotesTable.selectAll().where { 
                    (PollVotesTable.pollId eq pollId) and (PollVotesTable.participantSessionId eq participantId)
                }.count()
                if (existingVoteCount > 0) {
                    return@transaction Result.failure(Exception("You have already voted in this poll"))
                }
            }

            // Insert vote, ignore if duplicate
            try {
                PollVotesTable.insert {
                    it[id] = UUID.randomUUID()
                    it[this.pollId] = pollId
                    it[this.pollOptionId] = optionId
                    it[this.participantSessionId] = participantId
                    it[createdAt] = Instant.now()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                if (e.message?.contains("unique constraint", ignoreCase = true) == true ||
                    e.message?.contains("duplicate", ignoreCase = true) == true ||
                    e.message?.contains("poll_votes_poll_id_participant_session_id_poll_option_id_key", ignoreCase = true) == true) {
                    Result.success(Unit) // Idempotent success
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun openPoll(pollId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        transaction {
            val updated = PollsTable.update({ (PollsTable.id eq pollId) and (PollsTable.status eq "DRAFT") }) {
                it[status] = "OPEN"
                it[openedAt] = Instant.now()
            }
            if (updated > 0) Result.success(Unit) else Result.failure(Exception("Poll not found or not in DRAFT state"))
        }
    }

    suspend fun closePoll(pollId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        transaction {
            val updated = PollsTable.update({ (PollsTable.id eq pollId) and (PollsTable.status eq "OPEN") }) {
                it[status] = "CLOSED"
                it[closedAt] = Instant.now()
            }
            if (updated > 0) Result.success(Unit) else Result.failure(Exception("Poll not found or not in OPEN state"))
        }
    }

    data class PollOptionResult(val optionId: UUID, val text: String, val voteCount: Long)
    data class PollResults(val pollId: UUID, val question: String, val status: String, val options: List<PollOptionResult>)

    suspend fun getPollResults(pollId: UUID): Result<PollResults> = withContext(Dispatchers.IO) {
        transaction {
            val poll = PollsTable.selectAll().where { PollsTable.id eq pollId }.singleOrNull()
                ?: return@transaction Result.failure(Exception("Poll not found"))
                
            val options = PollOptionsTable.selectAll().where { PollOptionsTable.pollId eq pollId }
                .orderBy(PollOptionsTable.sortOrder)
                .map { Pair(it[PollOptionsTable.id], it[PollOptionsTable.optionText]) }
                
            val voteCounts = PollVotesTable.selectAll().where { PollVotesTable.pollId eq pollId }
                .groupBy { it[PollVotesTable.pollOptionId] }
                .mapValues { it.value.size.toLong() }
                
            val resultsList = options.map { (optId, text) ->
                PollOptionResult(optId, text, voteCounts[optId] ?: 0L)
            }
            
            Result.success(PollResults(pollId, poll[PollsTable.question], poll[PollsTable.status], resultsList))
        }
    }
}
