package com.jbcoder.meeting.domain

import io.livekit.server.AccessToken

object LiveKitPermissionMapper {

    /**
     * Mobile clients (both hosts and ordinary participants) receive narrowly scoped grants.
     * Crucially, roomAdmin is NEVER granted to mobile tokens.
     * Moderation actions must be routed through the Ktor backend.
     */
    fun applyGrants(
        token: AccessToken,
        roomName: String,
        role: String,
        publishRestricted: Boolean,
        screenShareAllowed: Boolean
    ) {
        val canSubscribe = true
        var canPublish = false
        var canPublishData = false
        val sources = mutableListOf<String>()

        when (role.uppercase()) {
            "HOST", "CO_HOST" -> {
                canPublish = true
                canPublishData = true
                sources.addAll(listOf("camera", "microphone", "screen_share", "screen_share_audio"))
            }
            "PARTICIPANT" -> {
                if (!publishRestricted) {
                    canPublish = true
                }
                canPublishData = true // Data messaging allowed for chat
                sources.addAll(listOf("camera", "microphone"))
                // Let any publishing participant share their screen
                if (!publishRestricted) {
                    sources.addAll(listOf("screen_share", "screen_share_audio"))
                }
            }
            "SUBSCRIBE_ONLY" -> {
                canPublish = false
                canPublishData = false
                // sources remains empty
            }
        }

        token.addGrants(
            io.livekit.server.RoomJoin(true),
            io.livekit.server.RoomName(roomName),
            io.livekit.server.CanPublish(canPublish),
            io.livekit.server.CanSubscribe(canSubscribe),
            io.livekit.server.CanPublishData(canPublishData),
            io.livekit.server.CanPublishSources(sources),
            io.livekit.server.RoomAdmin(false)
        )
    }
}
