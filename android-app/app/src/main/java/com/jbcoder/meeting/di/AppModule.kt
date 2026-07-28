package com.jbcoder.meeting.di

import android.content.Context
import com.jbcoder.meeting.security.AndroidKeyStoreSessionStorage
import com.jbcoder.meeting.security.SecureSessionStorage
import com.jbcoder.meeting.storage.DataStoreInstallationIdProvider
import com.jbcoder.meeting.storage.InstallationIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindInstallationIdProvider(
        impl: DataStoreInstallationIdProvider
    ): InstallationIdProvider

    @Binds
    @Singleton
    abstract fun bindMeetingRepository(
        impl: com.jbcoder.meeting.data.meeting.MeetingRepositoryImpl
    ): com.jbcoder.meeting.data.meeting.MeetingRepository

    companion object {
        @Provides
        @Singleton
        fun provideSecureSessionStorage(
            @ApplicationContext context: Context
        ): SecureSessionStorage {
            return AndroidKeyStoreSessionStorage(context)
        }
    }
}
