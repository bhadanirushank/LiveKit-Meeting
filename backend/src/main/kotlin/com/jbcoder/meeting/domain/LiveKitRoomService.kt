package com.jbcoder.meeting.domain

import com.jbcoder.meeting.configuration.AppConfig
import io.livekit.server.RoomServiceClient
import livekit.LivekitModels.Room
import org.slf4j.LoggerFactory
import retrofit2.Response

object LiveKitRoomService {
    private val logger = LoggerFactory.getLogger(LiveKitRoomService::class.java)

    /**
     * Creates or updates a room explicitly using the LiveKit RoomServiceClient.
     * This is useful when we need to enforce specific parameters like max participants.
     */
    fun createRoom(
        roomName: String,
        maxParticipants: Int,
        emptyTimeout: Int = 300
    ): Result<Room> {
        return try {
            val config = AppConfig.load()
            val client = RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            // In livekit-server SDK 0.8.2, createRoom takes roomName, emptyTimeout, maxParticipants etc.
            val call = client.createRoom(
                roomName,
                emptyTimeout,
                maxParticipants,
                "", // nodeId
                ""  // metadata
            )
            
            val response = call.execute()
            if (response.isSuccessful) {
                val room = response.body()
                if (room != null) {
                    Result.success(room)
                } else {
                    Result.failure(Exception("LiveKit returned successful response but empty room body"))
                }
            } else {
                val error = response.errorBody()?.string() ?: "Unknown LiveKit error"
                logger.error("Failed to create room $roomName in LiveKit: $error")
                Result.failure(Exception("LiveKit Room Creation Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            logger.error("Exception during LiveKit room creation for $roomName", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a room explicitly using the LiveKit RoomServiceClient.
     */
    fun deleteRoom(roomName: String): Result<Unit> {
        return try {
            val config = AppConfig.load()
            val client = RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            val call = client.deleteRoom(roomName)
            val response = call.execute()
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown LiveKit error"
                logger.error("Failed to delete room $roomName in LiveKit: $error")
                Result.failure(Exception("LiveKit Room Deletion Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            logger.error("Exception during LiveKit room deletion for $roomName", e)
            Result.failure(e)
        }
    }

    /**
     * Removes a participant from a room explicitly using the LiveKit RoomServiceClient.
     */
    fun removeParticipant(roomName: String, identity: String): Result<Unit> {
        return try {
            val config = AppConfig.load()
            val client = RoomServiceClient.createClient(config.livekitApiUrl, config.livekitKey, config.livekitSecret)
            
            val call = client.removeParticipant(roomName, identity)
            val response = call.execute()
            
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown LiveKit error"
                logger.error("Failed to remove participant $identity from room $roomName: $error")
                Result.failure(Exception("LiveKit Participant Removal Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            logger.error("Exception removing participant $identity from room $roomName", e)
            Result.failure(e)
        }
    }
}
