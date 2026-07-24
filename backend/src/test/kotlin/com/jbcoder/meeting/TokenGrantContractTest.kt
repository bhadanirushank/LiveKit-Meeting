package com.jbcoder.meeting

import com.auth0.jwt.JWT
import com.jbcoder.meeting.configuration.AppConfig
import com.jbcoder.meeting.domain.LiveKitTokenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Date

class TokenGrantContractTest {

    companion object {
        private lateinit var testConfig: AppConfig

        @JvmStatic
        @BeforeAll
        fun setup() {
            testConfig = run { TestSecrets.setupTestProperties(); AppConfig.load() }
        }
    }

    private fun verifyBaseTokenContract(
        tokenString: String,
        expectedIdentity: String,
        expectedRoom: String,
        expectedCanPublish: Boolean,
        expectedCanSubscribe: Boolean,
        expectedCanPublishData: Boolean,
        expectedSources: List<String>
    ) {
        val decoded = JWT.decode(tokenString)
        
        // Identity
        assertEquals(expectedIdentity, decoded.subject)
        
        val payloadStr = String(java.util.Base64.getUrlDecoder().decode(decoded.payload))
        
        // TTL (should be 300s = 5 mins)
        val expiresAt = decoded.expiresAt
        assertNotNull(expiresAt, "Token must have an expiration date")
        
        val now = Date().time
        val ttlSeconds = (expiresAt.time - now) / 1000
        assertTrue(ttlSeconds in 290..310, "Token TTL must be ~300 seconds, was $ttlSeconds")
        
        // Secrets absence
        assertFalse(payloadStr.contains("passcode"), "Token must not contain meeting passcodes")
        assertFalse(payloadStr.contains("hostSecret"), "Token must not contain host secrets")
        assertFalse(payloadStr.contains(testConfig.livekitSecret), "Token must not contain API secret")

        // Grants
        val videoClaim = decoded.getClaim("video").asMap()
        assertNotNull(videoClaim, "video claim must be present")
        
        assertEquals(expectedRoom, videoClaim["room"], "exact room name must be set")
        assertEquals(true, videoClaim["roomJoin"], "roomJoin must be true")
        assertNotEquals(true, videoClaim["roomAdmin"], "roomAdmin MUST NOT be granted")
        
        assertEquals(expectedCanPublish, videoClaim["canPublish"], "canPublish mismatch")
        assertEquals(expectedCanSubscribe, videoClaim["canSubscribe"], "canSubscribe mismatch")
        assertEquals(expectedCanPublishData, videoClaim["canPublishData"], "canPublishData mismatch")
        
        val actualSources = videoClaim["canPublishSources"] as? List<*> ?: emptyList<String>()
        assertEquals(expectedSources.size, actualSources.size, "Sources size mismatch")
        assertTrue(actualSources.containsAll(expectedSources), "Sources mismatch: expected $expectedSources, got $actualSources")
        
        // No unexpected admin grants
        assertNotEquals(true, videoClaim["roomCreate"])
        assertNotEquals(true, videoClaim["roomList"])
        assertNotEquals(true, videoClaim["roomRecord"])
    }

    @Test
    fun testHostTokenContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "hostRoom1",
            participantIdentity = "host_id",
            participantName = "Host Name",
            role = "HOST",
            publishRestricted = false,
            screenShareAllowed = false // Ignored for Host
        )
        verifyBaseTokenContract(
            token, "host_id", "hostRoom1", 
            expectedCanPublish = true, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = true,
            expectedSources = listOf("camera", "microphone", "screen_share", "screen_share_audio")
        )
    }

    @Test
    fun testCoHostTokenContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "coHostRoom",
            participantIdentity = "cohost_id",
            participantName = "CoHost",
            role = "CO_HOST",
            publishRestricted = true, // Should still be able to publish as co-host
            screenShareAllowed = false
        )
        verifyBaseTokenContract(
            token, "cohost_id", "coHostRoom", 
            expectedCanPublish = true, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = true,
            expectedSources = listOf("camera", "microphone", "screen_share", "screen_share_audio")
        )
    }

    @Test
    fun testNormalParticipantContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "pRoom",
            participantIdentity = "p_id",
            participantName = "Participant",
            role = "PARTICIPANT",
            publishRestricted = false,
            screenShareAllowed = false
        )
        verifyBaseTokenContract(
            token, "p_id", "pRoom", 
            expectedCanPublish = true, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = true,
            expectedSources = listOf("camera", "microphone")
        )
    }

    @Test
    fun testParticipantScreenShareAllowedContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "pRoom",
            participantIdentity = "p_id",
            participantName = "Participant",
            role = "PARTICIPANT",
            publishRestricted = false,
            screenShareAllowed = true
        )
        verifyBaseTokenContract(
            token, "p_id", "pRoom", 
            expectedCanPublish = true, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = true,
            expectedSources = listOf("camera", "microphone", "screen_share", "screen_share_audio")
        )
    }

    @Test
    fun testPublishRestrictedParticipantContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "pRoom",
            participantIdentity = "p_id_rest",
            participantName = "Participant",
            role = "PARTICIPANT",
            publishRestricted = true,
            screenShareAllowed = false
        )
        verifyBaseTokenContract(
            token, "p_id_rest", "pRoom", 
            expectedCanPublish = false, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = true,
            expectedSources = listOf("camera", "microphone")
        )
    }

    @Test
    fun testSubscribeOnlyParticipantContract() {
        val token = LiveKitTokenService.createToken(
            roomName = "sRoom",
            participantIdentity = "s_id",
            participantName = "Spectator",
            role = "SUBSCRIBE_ONLY",
            publishRestricted = false,
            screenShareAllowed = false
        )
        verifyBaseTokenContract(
            token, "s_id", "sRoom", 
            expectedCanPublish = false, 
            expectedCanSubscribe = true, 
            expectedCanPublishData = false,
            expectedSources = emptyList()
        )
    }
}
