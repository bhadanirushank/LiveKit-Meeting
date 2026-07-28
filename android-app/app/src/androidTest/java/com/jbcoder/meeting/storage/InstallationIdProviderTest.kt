package com.jbcoder.meeting.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InstallationIdProviderTest {

    private lateinit var provider: DataStoreInstallationIdProvider

    @Before
    fun setup() {
        provider = DataStoreInstallationIdProvider(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testFirstRequestCreatesValidUUID() = runBlocking {
        val id = provider.getInstallationId()
        assertNotNull(id)
        // verify it's a valid UUID
        val uuid = UUID.fromString(id)
        assertNotNull(uuid)
    }

    @Test
    fun testRepeatedRequestReturnsSameUUID() = runBlocking {
        val id1 = provider.getInstallationId()
        val id2 = provider.getInstallationId()
        assertEquals(id1, id2)
    }

    @Test
    fun testConcurrentRequestsReturnOneIdenticalUUID() = runBlocking {
        // Run concurrent fetches
        val ids = (1..50).map {
            async { provider.getInstallationId() }
        }.awaitAll()
        
        val firstId = ids.first()
        assertTrue(ids.all { it == firstId })
    }
}
