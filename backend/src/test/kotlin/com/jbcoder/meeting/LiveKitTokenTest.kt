package com.jbcoder.meeting

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.domain.LiveKitTokenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LiveKitTokenTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Need to set env variables so AppConfig.load() doesn't fail
            System.setProperty("POSTGRES_HOST", "localhost")
            System.setProperty("POSTGRES_PORT", "5432")
            System.setProperty("POSTGRES_DB", "livekit_meeting")
            System.setProperty("POSTGRES_USER", "test")
            System.setProperty("POSTGRES_PASSWORD", "test")
            System.setProperty("REDIS_HOST", "localhost")
            System.setProperty("REDIS_PORT", "6379")
            System.setProperty("REDIS_PASSWORD", "test")
            System.setProperty("LIVEKIT_API_KEY", TestSecrets.liveKitApiKey)
            System.setProperty("LIVEKIT_API_SECRET", TestSecrets.liveKitApiSecret)
            System.setProperty("JWT_SECRET", TestSecrets.jwtSecret)
        }
    }

    private fun decodeVideoGrant(token: String): Map<String, Any> {
        val decoded: DecodedJWT = JWT.decode(token)
        val videoClaim = decoded.getClaim("video")
        return videoClaim.asMap()
    }

    @Test
    fun `host token has correct grants`() {
        val token = LiveKitTokenService.createToken("room1", "host-1", "Host", role = "HOST")
        val videoGrant = decodeVideoGrant(token)
        
        assertEquals("room1", videoGrant["room"])
        assertEquals(true, videoGrant["roomJoin"])
        assertEquals(true, videoGrant["canPublish"])
        assertEquals(true, videoGrant["canSubscribe"])
        assertEquals(true, videoGrant["canPublishData"])
        assertEquals(false, videoGrant["roomAdmin"])
        
        val sources = videoGrant["canPublishSources"] as List<*>
        assertTrue(sources.containsAll(listOf("camera", "microphone", "screen_share", "screen_share_audio")))
    }

    @Test
    fun `participant token has correct grants`() {
        val token = LiveKitTokenService.createToken("room1", "part-1", "Participant", role = "PARTICIPANT")
        val videoGrant = decodeVideoGrant(token)
        
        assertEquals(true, videoGrant["canPublish"])
        assertEquals(true, videoGrant["canSubscribe"])
        assertEquals(true, videoGrant["canPublishData"])
        assertEquals(false, videoGrant["roomAdmin"])
        
        val sources = videoGrant["canPublishSources"] as List<*>
        assertTrue(sources.contains("camera"))
        assertTrue(sources.contains("microphone"))
        assertFalse(sources.contains("screen_share"))
    }

    @Test
    fun `publish restricted participant cannot publish video`() {
        val token = LiveKitTokenService.createToken("room1", "part-1", "Participant", role = "PARTICIPANT", publishRestricted = true)
        val videoGrant = decodeVideoGrant(token)
        
        // When publish is restricted, canPublish shouldn't be explicitly true
        val canPublish = videoGrant["canPublish"] as? Boolean ?: false
        assertFalse(canPublish, "publishRestricted should remove canPublish")
        
        // But they can still publish data
        assertEquals(true, videoGrant["canPublishData"])
    }

    @Test
    fun `subscribe only token cannot publish anything`() {
        val token = LiveKitTokenService.createToken("room1", "sub-1", "Viewer", role = "SUBSCRIBE_ONLY")
        val videoGrant = decodeVideoGrant(token)
        
        assertEquals(false, videoGrant["canPublish"])
        assertEquals(false, videoGrant["canPublishData"])
        assertEquals(true, videoGrant["canSubscribe"])
    }
}
