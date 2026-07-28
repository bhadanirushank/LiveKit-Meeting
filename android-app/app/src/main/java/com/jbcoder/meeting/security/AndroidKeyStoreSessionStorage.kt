package com.jbcoder.meeting.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

@Singleton
class AndroidKeyStoreSessionStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureSessionStorage {

    private val KEY_ALIAS = "session_storage_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private val FILE_NAME = "secure_session_data"
    private val FORMAT_VERSION = 1
    
    private val mutex = Mutex()
    private val dataFile: File
        get() = File(context.noBackupFilesDir, FILE_NAME)
        
    private val json = Json { ignoreUnknownKeys = true }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    override suspend fun saveSession(data: MobileSessionData) = mutex.withLock {
        try {
            val serialized = json.encodeToString(data).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            
            // Associated data binding (AAD)
            val aad = "version=$FORMAT_VERSION".toByteArray(Charsets.UTF_8)
            cipher.updateAAD(aad)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(serialized)

            val buffer = ByteBuffer.allocate(4 + 4 + iv.size + 4 + cipherText.size)
            buffer.putInt(FORMAT_VERSION)
            buffer.putInt(iv.size)
            buffer.put(iv)
            buffer.putInt(cipherText.size)
            buffer.put(cipherText)

            val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
            tempFile.writeBytes(buffer.array())
            
            // Atomic replacement
            if (!tempFile.renameTo(dataFile)) {
                dataFile.delete()
                tempFile.renameTo(dataFile)
            }
        } catch (e: Exception) {
            // Fail closed, clear anything
            clearSessionInternal()
        }
    }

    override suspend fun loadSession(): MobileSessionData? = mutex.withLock {
        if (!dataFile.exists()) return null

        try {
            val bytes = dataFile.readBytes()
            val buffer = ByteBuffer.wrap(bytes)
            
            val version = buffer.getInt()
            if (version != FORMAT_VERSION) {
                clearSessionInternal()
                return null
            }

            val ivSize = buffer.getInt()
            if (ivSize != 12) { // GCM standard IV is 12 bytes
                clearSessionInternal()
                return null
            }
            
            val iv = ByteArray(ivSize)
            buffer.get(iv)

            val cipherTextSize = buffer.getInt()
            val cipherText = ByteArray(cipherTextSize)
            buffer.get(cipherText)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val aad = "version=$FORMAT_VERSION".toByteArray(Charsets.UTF_8)
            cipher.updateAAD(aad)

            val plainText = cipher.doFinal(cipherText)
            val jsonStr = String(plainText, Charsets.UTF_8)
            return json.decodeFromString<MobileSessionData>(jsonStr)
            
        } catch (e: GeneralSecurityException) {
            // Keystore invalidated or corruption
            clearSessionInternal()
            return null
        } catch (e: Exception) {
            // General corruption
            clearSessionInternal()
            return null
        }
    }

    override suspend fun clearSession() = mutex.withLock {
        clearSessionInternal()
    }

    private fun clearSessionInternal() {
        if (dataFile.exists()) {
            dataFile.delete()
        }
        val tempFile = File(context.noBackupFilesDir, "$FILE_NAME.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}
