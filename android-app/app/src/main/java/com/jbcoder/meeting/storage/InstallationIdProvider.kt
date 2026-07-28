package com.jbcoder.meeting.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val Context.dataStore by preferencesDataStore(name = "installation_prefs")

interface InstallationIdProvider {
    suspend fun getInstallationId(): String
}

@Singleton
class DataStoreInstallationIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : InstallationIdProvider {
    private val INSTALLATION_ID_KEY = stringPreferencesKey("installation_id")
    private val mutex = Mutex()

    override suspend fun getInstallationId(): String = mutex.withLock {
        val currentId = context.dataStore.data.map { prefs ->
            prefs[INSTALLATION_ID_KEY]
        }.first()

        if (currentId != null) {
            return currentId
        }

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            prefs[INSTALLATION_ID_KEY] = newId
        }
        return newId
    }
}
