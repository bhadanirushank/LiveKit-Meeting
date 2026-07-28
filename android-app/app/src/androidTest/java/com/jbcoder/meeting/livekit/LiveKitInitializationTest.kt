package com.jbcoder.meeting.livekit

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.livekit.android.LiveKit
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveKitInitializationTest {

    @Test
    fun testLiveKitSdkLoadsSuccessfully() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val coordinator = MeetingSessionCoordinator(context)
        
        // Ensure classes load without crashing
        assertNotNull(coordinator)
        
        // Initial state should be idle (we can check LiveKit context internally)
        val room = LiveKit.create(context)
        assertNotNull(room)
        
        room.release()
    }
}
