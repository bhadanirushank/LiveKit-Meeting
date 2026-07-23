package com.jbcoder.meeting.domain

import com.jbcoder.meeting.configuration.AppConfig
import io.livekit.server.RoomServiceClient
import org.slf4j.LoggerFactory

object LiveKitParticipantService {
    private val logger = LoggerFactory.getLogger(LiveKitParticipantService::class.java)

    /**
     * Removes a participant from the LiveKit room.
     */
    fun removeParticipant(roomName: String, identity: String): Result<Unit> {
        return try {
            val config = AppConfig.load()
            val client = RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            val response = client.removeParticipant(roomName, identity).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                logger.error("Failed to remove participant $identity from $roomName: $error")
                Result.failure(Exception("LiveKit API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            logger.error("Exception while removing participant $identity from $roomName", e)
            Result.failure(e)
        }
    }
}
