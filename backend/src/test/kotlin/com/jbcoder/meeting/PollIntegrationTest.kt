package com.jbcoder.meeting

import com.jbcoder.meeting.api.*
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.configuration.DatabaseConfig
import com.jbcoder.meeting.configuration.RedisConfig
import com.jbcoder.meeting.domain.PollService
import com.jbcoder.meeting.persistence.MeetingsTable
import com.jbcoder.meeting.persistence.ParticipantSessionsTable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class PollIntegrationTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            testConfig = AppConfig.load()
            DatabaseConfig.init(testConfig)
            RedisConfig.init(testConfig)
            if (!DatabaseConfig.isHealthy()) fail<Unit>("PostgreSQL unreachable")
            if (!RedisConfig.isHealthy()) fail<Unit>("Redis unreachable")
        }

        /** Call PollService suspend function from a background thread to avoid blocking testApplication dispatcher */
        fun <T> runOnThread(block: suspend () -> T): T {
            val result = AtomicReference<Result<T>>()
            val thread = Thread {
                result.set(kotlinx.coroutines.runBlocking { Result.runCatching { block() } })
            }
            thread.start()
            thread.join(10_000)
            return result.get()!!.getOrThrow()
        }
    }

    @Test
    fun testPollLifecycle() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Poll Test Meeting","passcode":"abc123","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.Created, createRes.status, "Meeting: ${createRes.bodyAsText()}")
        val meetingJson = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject
        val meetingCode = meetingJson["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = meetingJson["hostSecret"]!!.jsonPrimitive.content

        val hostToken = Json.parseToJsonElement(
            client.post("/api/v1/host-sessions/exchange") {
                contentType(ContentType.Application.Json)
                setBody("""{"publicMeetingCode":"$meetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
            }.bodyAsText()
        ).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Create poll (starts DRAFT)
        val createPollRes = client.post("/api/v1/meetings/$meetingCode/polls") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            setBody("""{"question":"Favorite color?","allowMultipleAnswers":false,"options":["Red","Blue","Green"]}""")
        }
        assertEquals(HttpStatusCode.Created, createPollRes.status, "Poll creation: ${createPollRes.bodyAsText()}")
        val pollId = UUID.fromString(Json.parseToJsonElement(createPollRes.bodyAsText()).jsonObject["pollId"]!!.jsonPrimitive.content)

        // Open poll - must run on separate thread to avoid dispatcher deadlock inside testApplication
        val openResult = runOnThread { PollService.openPoll(pollId) }
        assertTrue(openResult.isSuccess, "Poll open: ${openResult.exceptionOrNull()?.message}")

        // Get options
        val pollData = runOnThread { PollService.getPollResults(pollId) }.getOrThrow()
        assertEquals("OPEN", pollData.status)
        assertEquals(3, pollData.options.size)

        val optionId = pollData.options.first().optionId
        val participantId = UUID.randomUUID()

        // Insert real participant session so poll vote foreign key check passes
        runOnThread {
            transaction {
                val meetingId = MeetingsTable.selectAll().where { MeetingsTable.publicMeetingCode eq meetingCode }.single()[MeetingsTable.id]
                ParticipantSessionsTable.insert {
                    it[id] = participantId
                    it[this.meetingId] = meetingId
                    it[livekitIdentity] = "voter-$participantId"
                    it[displayName] = "Test Voter"
                    it[role] = "PARTICIPANT"
                    it[state] = "ADMITTED"
                    it[requestedAt] = Instant.now()
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }
        }

        // Vote
        val voteRes = client.post("/api/v1/meetings/$meetingCode/polls/$pollId/vote") {
            contentType(ContentType.Application.Json)
            setBody("""{"optionId":"$optionId","participantId":"$participantId"}""")
        }
        assertEquals(HttpStatusCode.OK, voteRes.status, "Vote: ${voteRes.bodyAsText()}")

        // Vote count increases
        val results = runOnThread { PollService.getPollResults(pollId) }.getOrThrow()
        val votedOption = results.options.first { it.optionId == optionId }
        assertTrue(votedOption.voteCount >= 1, "Vote count should be at least 1, was: ${votedOption.voteCount}")

        // Close poll
        val closeResult = runOnThread { PollService.closePoll(pollId) }
        assertTrue(closeResult.isSuccess, "Poll close: ${closeResult.exceptionOrNull()?.message}")

        // Vote after closed must fail
        val lateVoteRes = client.post("/api/v1/meetings/$meetingCode/polls/$pollId/vote") {
            contentType(ContentType.Application.Json)
            setBody("""{"optionId":"$optionId","participantId":"${UUID.randomUUID()}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, lateVoteRes.status, "Vote after close must fail")
        assertTrue(lateVoteRes.bodyAsText().contains("not open"), "Error must say poll not open")

        // Cannot re-open a closed poll
        val reopenResult = runOnThread { PollService.openPoll(pollId) }
        assertFalse(reopenResult.isSuccess, "Cannot re-open a closed poll")

        // Verify final closed state
        val finalResults = runOnThread { PollService.getPollResults(pollId) }.getOrThrow()
        assertEquals("CLOSED", finalResults.status)
    }

    @Test
    fun testPollRequiresMinimumTwoOptions() = testApplication {
        application { module(testConfig) }

        val createRes = client.post("/api/v1/meetings") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Poll Options Test","passcode":"abc123","waitingRoomEnabled":true,"joinBeforeHostEnabled":false,"maximumParticipants":10,"idempotencyKey":"${UUID.randomUUID()}"}""")
        }
        val meetingCode = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["publicMeetingCode"]!!.jsonPrimitive.content
        val hostSecret = Json.parseToJsonElement(createRes.bodyAsText()).jsonObject["hostSecret"]!!.jsonPrimitive.content
        val hostToken = Json.parseToJsonElement(
            client.post("/api/v1/host-sessions/exchange") {
                contentType(ContentType.Application.Json)
                setBody("""{"publicMeetingCode":"$meetingCode","hostSecret":"$hostSecret","deviceSessionId":"${UUID.randomUUID()}"}""")
            }.bodyAsText()
        ).jsonObject["accessToken"]!!.jsonPrimitive.content

        // Single option - should fail
        val badPollRes = client.post("/api/v1/meetings/$meetingCode/polls") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $hostToken")
            setBody("""{"question":"Only one?","allowMultipleAnswers":false,"options":["Only"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badPollRes.status, "Poll with 1 option must be rejected")
    }
}
