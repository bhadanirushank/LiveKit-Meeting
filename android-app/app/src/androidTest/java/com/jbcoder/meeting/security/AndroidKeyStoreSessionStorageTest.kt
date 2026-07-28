package com.jbcoder.meeting.security

import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreSessionStorageTest {

    private lateinit var storage: AndroidKeyStoreSessionStorage
    private val keyAlias = "session_storage_key"

    @Before
    fun setup() {
        storage = AndroidKeyStoreSessionStorage(ApplicationProvider.getApplicationContext())
    }

    @After
    fun teardown() = runBlocking {
        storage.clearSession()
    }

    @Test
    fun testGenerateAES256Key() = runBlocking {
        // trigger key generation
        storage.saveSession(MobileSessionData("session1", "atk_test", "rtk_test", 0L, 0L))
        
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue(keyStore.containsAlias(keyAlias))
        
        val key = keyStore.getKey(keyAlias, null)
        assertEquals("AES", key.algorithm)
    }

    @Test
    fun testSaveAndLoadSessionRoundTrip() = runBlocking {
        val originalData = MobileSessionData("sess_123", "atk_123", "rtk_456", 1000L, 2000L)
        storage.saveSession(originalData)

        val loadedData = storage.loadSession()
        assertNotNull(loadedData)
        assertEquals("atk_123", loadedData?.accessToken)
        assertEquals("rtk_456", loadedData?.refreshToken)
    }

    @Test
    fun testFresh12ByteIVPerWriteAndDifferentCiphertext() = runBlocking {
        val data = MobileSessionData("sess", "atk_constant", "rtk_constant", 0L, 0L)
        storage.saveSession(data)
        
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "secure_session_data")
        val content1 = file.readBytes()
        
        // 12 byte IV + GCM tag (16 bytes) + ciphertext. Min length > 28
        assertTrue("Content must contain IV, tag, and ciphertext", content1.size > 28)

        // write again
        storage.saveSession(data)
        val content2 = file.readBytes()

        // Although plaintext is the same, ciphertext + IV should be totally different
        assertFalse("Ciphertext must be different for repeated writes of same plaintext", content1.contentEquals(content2))
    }

    @Test
    fun testClearOperation() = runBlocking {
        storage.saveSession(MobileSessionData("s", "atk_1", "rtk_2", 0L, 0L))
        assertNotNull(storage.loadSession())

        storage.clearSession()
        assertNull("Session should be null after clear", storage.loadSession())
        
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "secure_session_data")
        assertFalse("Stored file should be deleted", file.exists())
    }

    @Test
    fun testCorruptCiphertextRecovery() = runBlocking {
        storage.saveSession(MobileSessionData("s", "atk_1", "rtk_2", 0L, 0L))
        
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "secure_session_data")
        val content = file.readBytes()
        // Corrupt it by flipping a byte
        content[content.size - 1] = content[content.size - 1].inc()
        file.writeBytes(content)

        // Authentication failure fails closed, returns null instead of crashing
        assertNull(storage.loadSession())
    }

    @Test
    fun testMissingKeyRecovery() = runBlocking {
        storage.saveSession(MobileSessionData("s", "atk_1", "rtk_2", 0L, 0L))
        
        // Delete key manually
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(keyAlias)

        // Should recover gracefully and return null
        assertNull(storage.loadSession())
    }

    @Test
    fun testFileDoesNotContainPlaintextTokens() = runBlocking {
        val atk = "atk_super_secret"
        val rtk = "rtk_super_secret"
        storage.saveSession(MobileSessionData("s", atk, rtk, 0L, 0L))
        
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().noBackupFilesDir, "secure_session_data")
        val text = file.readText(Charsets.UTF_8)
        
        assertFalse(text.contains(atk))
        assertFalse(text.contains(rtk))
    }
}
